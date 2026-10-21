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

echo "▸ 1/4 编译并打 jar（目标平台 $PLATFORM）"
TARGET_PLATFORM="$PLATFORM" "$ROOT/tools/build.sh" jar

echo "▸ 2/4 准备运行时依赖"
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
  --add-modules java.base,java.desktop,java.logging,java.xml,java.naming,java.prefs,java.management,java.net.http,jdk.unsupported,jdk.crypto.ec
)

export TMPDIR="$TMP"
export JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=1 -Xss512k -XX:+UseSerialGC -Xmx512m -Djava.io.tmpdir=$(wp "$TMP")"
JP="$JAVA_HOME/bin/jpackage"
[ -x "$JP" ] || JP="$JAVA_HOME/bin/jpackage.exe"

case "$PLATFORM" in
  windows-x64)
    echo "▸ 3/4 jpackage → Windows 免安装目录"
    if [ -d "$ROOT/dist/dsh-desktop" ]; then
      mv "$ROOT/dist/dsh-desktop" "$ROOT/dist/.app-old-$(date +%s)"
    fi
    "$JP" --type app-image "${JP_COMMON[@]}" >/dev/null

    echo "▸ 4/4 打包 zip"
    "$PY" - "$(wp "$ROOT")" "$VERSION" <<'PY'
import os, sys, zipfile
root, version = sys.argv[1], sys.argv[2]
src = os.path.join(root, "dist", "dsh-desktop")
out = os.path.join(root, "dist", f"dsh-desktop-{version}-windows-x64.zip")
if os.path.exists(out):
    os.remove(out)
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for base, _dirs, files in os.walk(src):
        for f in files:
            full = os.path.join(base, f)
            z.write(full, os.path.relpath(full, os.path.dirname(src)))
print(f"   {out} · {os.path.getsize(out) // 1048576} MB")
PY

    # msi 需要 WiX Toolset 3.x；没装就跳过，不让它把整条流水线判死
    echo "▸ 附：尝试打 msi（需要 WiX 3.x）"
    if "$JP" --type msi --win-menu --win-shortcut --win-dir-chooser \
         "${JP_COMMON[@]}" >/dev/null 2>&1; then
      echo "   ✓ msi 已生成"
    else
      echo "   ⚠ 跳过 msi（缺 WiX 或 jpackage 报错），zip 已可用" >&2
    fi
    ;;

  *)
    # linux-arm64 → arm64，其余 → amd64（deb 的架构名）
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
