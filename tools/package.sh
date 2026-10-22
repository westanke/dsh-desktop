#!/bin/bash
# 打安装包。Linux → deb（amd64 / arm64），Windows → 免安装 zip（附赠 msi，需 WiX）。
#
# 为什么手写这套流程：
#   1) 开发机 Gradle 在配置阶段就会耗尽 4GB 地址空间，所以编译走 tools/build.sh，
#      打包走 jpackage；
#   2) jpackage 是 JVM 工具，必须靠 JAVA_TOOL_OPTIONS 压住线程数才起得来；
#   3) deb 里 jpackage 只把 .desktop 丢在 /opt 下、没装进 /usr/share/applications，
#      真机因此既没菜单项也没桌面图标 —— 必须解开重打（仅 Linux）。
#
# 用法：
#   tools/package.sh                                按当前平台产出 dist/ 下的安装包
#   TARGET_PLATFORM=linux-arm64 tools/package.sh    在 arm64 机器上产出 arm64 deb
#   TARGET_PLATFORM=windows-x64 tools/package.sh    在 Windows（Git Bash）上产出 zip
#
# 环境变量：JAVA_HOME / BUILD_TMP / TARGET_PLATFORM / PYTHON，同 tools/build.sh。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# 版本号唯一来源是仓库根的 version.txt：脚本、jar 清单、安装包名、文档全都读它。
VERSION="$(tr -d '[:space:]' < "$ROOT/version.txt" 2>/dev/null || true)"
[ -n "$VERSION" ] || { echo "✗ 读不到 version.txt" >&2; exit 1; }
TMP="${BUILD_TMP:-$ROOT/build/tmp}"
mkdir -p "$TMP" "$ROOT/dist"

detect_platform() {
  case "$(uname -s)" in
    Linux)
      case "$(uname -m)" in
        aarch64|arm64) echo linux-arm64 ;;
        *) echo linux-x64 ;;
      esac
      ;;
    MINGW*|MSYS*|CYGWIN*) echo windows-x64 ;;
    *) echo linux-x64 ;;
  esac
}
PLATFORM="${TARGET_PLATFORM:-$(detect_platform)}"

# 路径与类路径方言（同 tools/build.sh）：Windows 上要给 jpackage.exe 传 D:\ 形式
IS_WIN=0
case "$PLATFORM" in windows-*) IS_WIN=1 ;; esac
wp() {
  if [ "$IS_WIN" = 1 ] && command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$1"
  else
    printf '%s' "$1"
  fi
}

# JDK 探测（同 tools/build.sh）
if [ -z "${JAVA_HOME:-}" ]; then
  for cand in \
    "$HOME/.workbuddy/binaries/java/jdk-17.0.20.1+1" \
    /usr/lib/jvm/*17* /usr/lib/jvm/*21*; do
    [ -x "$cand/bin/java" ] || [ -x "$cand/bin/java.exe" ] || continue
    JAVA_HOME="$cand"; break
  done
fi
if [ -z "${JAVA_HOME:-}" ]; then
  JAVABIN="$(command -v java || true)"
  [ -n "$JAVABIN" ] && JAVA_HOME="$(cd "$(dirname "$JAVABIN")/.." && pwd)"
fi
if [ -z "${JAVA_HOME:-}" ] || { [ ! -x "$JAVA_HOME/bin/java" ] && [ ! -x "$JAVA_HOME/bin/java.exe" ]; }; then
  echo "✗ 找不到 JDK 17+。请设置 JAVA_HOME（当前：${JAVA_HOME:-<空>}）" >&2
  exit 1
fi
export JAVA_HOME

PY="${PYTHON:-}"
if [ -z "$PY" ]; then
  for c in python3 python py; do
    if command -v "$c" >/dev/null 2>&1; then PY="$c"; break; fi
  done
fi
[ -n "$PY" ] || { echo "✗ 找不到 Python" >&2; exit 1; }
# 同 tools/build.sh：Windows 控制台默认不是 UTF-8，Python 打印中文会崩
export PYTHONUTF8=1
export PYTHONIOENCODING=utf-8

echo "▸ 1/4 编译并打 jar（目标平台 $PLATFORM）"
TARGET_PLATFORM="$PLATFORM" "$ROOT/tools/build.sh" jar

# 内存/线程上限必须**早于**本脚本里任何 JVM 工具：jlink、IconGen 的 java、
# jpackage 全是 JVM 进程，开发机地址空间就 4GB，不压线程数直接 OOM 起不来。
# （踩过：jlink 放在这段之前跑，报 unable to create native thread。）
export TMPDIR="$TMP"
export JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=1 -Xss512k -XX:+UseSerialGC -Xmx512m -Djava.io.tmpdir=$(wp "$TMP")"

echo "▸ 2/4 准备运行时镜像（jlink 精简版）"
# runtime image 单独 jlink 出来，而不是让 jpackage 自己拼：
#   1) 轻包（LITE=1）与完整包**共用同一个 image**，"下载下来的运行时"和
#      "包里的运行时"字节一致，不会出现两套行为；
#   2) tools/build-runtime.sh 也拿它打 tar.xz 当下载资产。
# 模块集为什么这么小：HTTP 走 OkHttp（不碰 java.net.http），序列化走
# kotlinx.serialization（不碰 java.xml），日志走 okhttp 自带的 JUL。
# **jdk.crypto.ec 不能删** —— TLS 的 ECDHE 靠 SunEC，少了它连 https 会握手失败
# （jdeps 看不出来，它不分析运行时算法协商；真机探针验过 gitee/baidu 都通）。
RT_IMAGE="${RUNTIME_IMAGE:-$ROOT/build/runtime-image}"
if [ ! -d "$RT_IMAGE" ] || [ -z "$(ls -A "$RT_IMAGE" 2>/dev/null)" ]; then
  if [ -d "$RT_IMAGE" ]; then mv "$RT_IMAGE" "$ROOT/build/.rt-image-old-$(date +%s)"; fi
  "$JAVA_HOME/bin/jlink" \
    --add-modules java.base,java.desktop,java.logging,java.instrument,jdk.unsupported,jdk.crypto.ec \
    --strip-debug --compress 2 --no-header-files --no-man-pages \
    --output "$(wp "$RT_IMAGE")" 2>&1 | grep -viE 'picked up|^warning' || true
fi
[ -x "$RT_IMAGE/bin/java" ] || [ -x "$RT_IMAGE/bin/java.exe" ] || {
  echo "✗ jlink 运行时镜像没生成（$RT_IMAGE）" >&2; exit 1; }
du -sh "$RT_IMAGE" | sed 's/^/   运行时镜像 /'
# 本机 rm 走 safe-delete 包装，批量删除会因阈值中断脚本；挪走旧目录即可
if [ -d "$ROOT/build/app-input" ]; then
  mv "$ROOT/build/app-input" "$ROOT/build/.app-input-old-$(date +%s)"
fi
mkdir -p "$ROOT/build/app-input"
cp "$ROOT/build/dsh-desktop.jar" "$ROOT/build/app-input/"

for f in "$ROOT"/lib/*.jar; do
  base="$(basename "$f")"
  case "$base" in
    # 编译器插件不随包发布
    kotlin-compose-compiler-plugin*|kotlin-serialization-compiler-plugin*) continue ;;
    # material-icons-extended 36 MB，本工程只 import 23 个图标 → 先瘦身再带走。
    # 白名单从源码 import 现抓，漏抓就是编译期 unresolved reference，不会静默少图标。
    material-icons-extended*)
      "$PY" "$ROOT/tools/slim-icons.py" "$f" "$ROOT/build/app-input/$base"
      continue
      ;;
    # 只带目标平台的 skiko 原生库
    skiko-awt-runtime-*)
      case "$base" in
        *"$PLATFORM"*) cp "$f" "$ROOT/build/app-input/" ;;
        *) : ;;
      esac
      continue
      ;;
  esac
  cp "$f" "$ROOT/build/app-input/"
done
if [ -d "$ROOT/tools/platform-libs/$PLATFORM" ]; then
  for f in "$ROOT/tools/platform-libs/$PLATFORM"/*.jar; do
    [ -e "$f" ] && cp "$f" "$ROOT/build/app-input/"
  done
fi

# 公共 jpackage 参数
JP_COMMON=(
  --name dsh-desktop
  --app-version "$VERSION"
  --input "$(wp "$ROOT/build/app-input")"
  --main-jar dsh-desktop.jar
  --main-class io.github.westanke.dshdesktop.MainKt
  --dest "$(wp "$ROOT/dist")"
  --vendor "westanke"
  --description "DSH Desktop - 远程连接 DeepSeek Harness 的原生桌面客户端"
  --java-options "-Xmx768m"
  --java-options "-XX:MaxMetaspaceSize=384m"
  --java-options "-Dfile.encoding=UTF-8"
  --java-options "-Dskiko.renderApi=SOFTWARE"
  --runtime-image "$(wp "$RT_IMAGE")"
)

# 内存/线程上限见第 87 行：本脚本所有 JVM 工具共用同一份 JAVA_TOOL_OPTIONS
JP="$JAVA_HOME/bin/jpackage"
[ -x "$JP" ] || JP="$JAVA_HOME/bin/jpackage.exe"

case "$PLATFORM" in
  windows-x64)
    echo "▸ 3/4 jpackage → Windows 免安装目录"
    if [ -d "$ROOT/dist/dsh-desktop" ]; then
      mv "$ROOT/dist/dsh-desktop" "$ROOT/dist/.app-old-$(date +%s)"
    fi
    "$JP" --type app-image "${JP_COMMON[@]}" >/dev/null

    # 轻量包（LITE=1）：拿掉 50 MB 运行时，jpackage 的 exe 是硬编码找 runtime 的，
    # 一并换成 bat 引导器（下载逻辑与 Linux 侧同构：国内源优先、离线可手动指定）。
    # zip 没有"快捷方式"的概念，所以用户点开文件夹第一眼看到的就是 dsh-desktop.bat。
    if [ "${LITE:-0}" = "1" ]; then
      mv "$ROOT/dist/dsh-desktop/runtime" "$ROOT/dist/.lite-runtime-old-$(date +%s)"
      mkdir -p "$ROOT/dist/dsh-desktop/lib/app"
      printf '%s\n' "$VERSION" > "$ROOT/dist/dsh-desktop/lib/app/VERSION"
      RT_BASE="dsh-desktop-runtime-$VERSION-windows-x64"
      if [ -f "$ROOT/dist/$RT_BASE.sha256" ]; then
        cp "$ROOT/dist/$RT_BASE.sha256" "$ROOT/dist/dsh-desktop/lib/app/"
      else
        echo "⚠ 没找到 dist/$RT_BASE.sha256（Windows 侧引导器本就不校验，仅作记录）" >&2
      fi
      rm -f "$ROOT/dist/dsh-desktop/dsh-desktop.exe"
      cp "$ROOT/tools/launcher/dsh-desktop.bat" "$ROOT/dist/dsh-desktop/dsh-desktop.bat"
    fi

    echo "▸ 4/4 打包 zip"
    "$PY" - "$(wp "$ROOT")" "$VERSION" <<'PY'
import os, sys, zipfile
root, version = sys.argv[1], sys.argv[2]
lite = os.environ.get("LITE") == "1"
src = os.path.join(root, "dist", "dsh-desktop")
suffix = "-windows-x64-lite" if lite else "-windows-x64"
out = os.path.join(root, "dist", f"dsh-desktop-{version}{suffix}.zip")
if os.path.exists(out):
    os.remove(out)
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for base, _dirs, files in os.walk(src):
        for f in files:
            full = os.path.join(base, f)
            z.write(full, os.path.relpath(full, os.path.dirname(src)))
print(f"   {out} · {os.path.getsize(out) // 1048576} MB")
PY

    # msi 需要 WiX Toolset 3.x；没装就跳过，不让它把整条流水线判死。
    # 轻量包不打 msi：WiX 的快捷方式指向 exe，而轻包换成了 bat，改不动。
    if [ "${LITE:-0}" != "1" ]; then
      echo "▸ 附：尝试打 msi（需要 WiX 3.x）"
      if "$JP" --type msi --win-menu --win-shortcut --win-dir-chooser \
           "${JP_COMMON[@]}" >/dev/null 2>&1; then
        echo "   ✓ msi 已生成"
      else
        echo "   ⚠ 跳过 msi（缺 WiX 或 jpackage 报错），zip 已可用" >&2
      fi
    fi
    ;;

  *)
    # linux-arm64 → arm64，其余 → amd64（deb 的架构名）。
    # 运行时资产名另用 $PLATFORM（linux-x64 / linux-arm64，架构含在里面）。
    case "$PLATFORM" in
      linux-arm64) DEB_ARCH="arm64" ;;
      *) DEB_ARCH="amd64" ;;
    esac

    echo "▸ 3/4 jpackage → deb（$DEB_ARCH）"
    "$JP" --type deb \
      --linux-deb-maintainer "westanke" \
      --linux-shortcut \
      --linux-menu-group "Development" \
      "${JP_COMMON[@]}" >/dev/null

    echo "▸ 4/4 补桌面入口并重打 deb"
    DEB="$ROOT/dist/dsh-desktop_${VERSION}-1_${DEB_ARCH}.deb"
    ICONS="$ROOT/build/icons"

    # 多尺寸图标：32/48/64/128/256。jpackage 自带的只有 32×32，桌面上会糊成一片。
    "$JAVA_HOME/bin/java" -XX:ActiveProcessorCount=1 -Xss512k -XX:+UseSerialGC -Xmx256m \
      "-Djava.io.tmpdir=$(wp "$TMP")" "$ROOT/tools/IconGen.java" "$ICONS" >/dev/null

    WORK="$ROOT/build/deb-work"
    if [ -d "$WORK" ]; then mv "$WORK" "$ROOT/build/.deb-work-old-$(date +%s)"; fi
    dpkg-deb -R "$DEB" "$WORK"

    # ① 菜单项：jpackage 只把 .desktop 丢在 /opt/dsh-desktop/lib 下，**没装进 /usr/share/applications**
    #    真机因此既没有菜单项也没有桌面图标。
    mkdir -p "$WORK/usr/share/applications" "$WORK/usr/share/icons"
    cp "$ROOT/tools/linux/dsh-desktop.desktop" "$WORK/usr/share/applications/dsh-desktop.desktop"
    chmod 644 "$WORK/usr/share/applications/dsh-desktop.desktop"

    # ② 图标主题
    cp -r "$ICONS/hicolor/." "$WORK/usr/share/icons/hicolor/"

    # ③ 顺手把 /opt 里那张 32×32 换成 256×256（任务栏、窗口图标都读它）
    cp "$ICONS/hicolor/256x256/apps/dsh-desktop.png" "$WORK/opt/dsh-desktop/lib/dsh-desktop.png"

    # ⑤ 轻量包（LITE=1）：把 50 MB 的运行时从包里拿掉，换成"首次启动下载"。
    #    桌面入口 Exec 本来指向 bin/dsh-desktop，所以换成引导器脚本后
    #    菜单项 / 桌面图标点下去走的还是同一条路，对用户完全无感。
    if [ "${LITE:-0}" = "1" ]; then
      RT_BASE="dsh-desktop-runtime-$VERSION-$PLATFORM"
      # 本机 rm 走 safe-delete 包装，超 50 个文件就要人工确认，脚本会被打断；
      # 挪走即可（build/ 里的旧目录下次打包自己会再被挪）
      mv "$WORK/opt/dsh-desktop/lib/runtime" \
         "$ROOT/build/.lite-runtime-old-$(date +%s)"
      # 版本号：引导器靠它拼运行时包名与用户目录名，必须和 release 上的资产对得上
      mkdir -p "$WORK/opt/dsh-desktop/lib/app"
      printf '%s\n' "$VERSION" > "$WORK/opt/dsh-desktop/lib/app/VERSION"
      # 随包带运行时压缩包的 sha256：下载完立刻验，防 CDN 劫持/半截文件
      if [ -f "$ROOT/dist/$RT_BASE.sha256" ]; then
        cp "$ROOT/dist/$RT_BASE.sha256" "$WORK/opt/dsh-desktop/lib/app/"
      else
        echo "⚠ 没找到 dist/$RT_BASE.sha256，引导器将跳过校验" >&2
        echo "   先跑 tools/build-runtime.sh 生成它" >&2
      fi
      # jpackage 的 launcher 是硬编码找 lib/runtime 的二进制，换成 sh 引导器
      cp "$ROOT/tools/launcher/dsh-desktop.sh" "$WORK/opt/dsh-desktop/bin/dsh-desktop"
      chmod 755 "$WORK/opt/dsh-desktop/bin/dsh-desktop"
      DEB="$ROOT/dist/dsh-desktop-lite_${VERSION}-1_${DEB_ARCH}.deb"
    fi

    # ④ postinst / postrm 追加桌面图标逻辑。
    #    追加前必须摘掉末尾孤立的 `exit 0`，否则追加的代码永远执行不到。
    "$PY" - "$WORK" "$ROOT/tools/linux/postinst-shortcut.sh" "$ROOT/tools/linux/postrm-shortcut.sh" <<'PY'
import sys, os, re
work, postinst, postrm = sys.argv[1:4]
for name, extra in (("postinst", postinst), ("postrm", postrm)):
    path = os.path.join(work, "DEBIAN", name)
    base = open(path, encoding="utf-8").read() if os.path.exists(path) else "#!/bin/sh\n"
    add = open(extra, encoding="utf-8").read()
    base = re.sub(r"\n\s*exit\s+0\s*;?\s*$", "\n", base.rstrip()) + "\n"
    open(path, "w", encoding="utf-8").write(base + "\n" + add)
    os.chmod(path, 0o755)
print("   postinst / postrm 已合并")
PY

    dpkg-deb --build "$WORK" "$DEB" >/dev/null

    echo "▸ 校验包内桌面入口"
    dpkg-deb -c "$DEB" | grep -E 'usr/share/applications|usr/share/icons/.*/dsh-desktop' >/dev/null || {
      echo "   ✗ 桌面入口缺失" >&2; exit 1
    }
    ;;
esac

echo "▸ 产物："
ls -lh "$ROOT/dist" | grep -vE '^total|^总用量' || true
