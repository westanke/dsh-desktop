#!/bin/bash
# 原生构建脚本（本机 UOS、CI 的 Linux/Windows 共用这一份）。
#
# 为什么不用 Gradle：开发机单进程虚拟地址空间只有 4GB（RLIMIT_AS 硬限制），
# Gradle 在**配置阶段**就会被地址空间耗尽打死（Map failed / Native memory allocation failed）。
# 这里直接调 Kotlin 编译器，只加载编译器本体 + 运行时依赖，地址占用约为 Gradle 的一半。
#
# 用法：
#   tools/build.sh            编译到 build/classes
#   tools/build.sh jar        再打 build/dsh-desktop.jar
#   tools/build.sh run        编译后直接启动窗口（仅图形环境）
#
# 环境变量（都有默认值，CI 上覆盖即可）：
#   JAVA_HOME         JDK 17+；不设则依次探测本机缓存、/usr/lib/jvm、PATH 上的 java
#   BUILD_TMP         临时目录；默认 build/tmp（跟着仓库走，不写死绝对路径）
#   KOTLINC_HOME      Kotlin 编译器 jar 所在的本地缓存；缺了就自动从 Maven 下载
#   TARGET_PLATFORM   linux-x64 | linux-arm64 | windows-x64；默认按 uname 推断
#   PYTHON            Python 解释器名；默认依次探测 python3 / python / py
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# ── 目标平台 ────────────────────────────────────────────────────────────
# skiko 的原生库分平台，classpath 里只能留目标平台那一份：同时塞进 x64 和 arm64 的
# 原生库，打出来的包会带着跑不起来的 .so。
detect_platform() {
  case "$(uname -s)" in
    Linux)
      case "$(uname -m)" in
        aarch64|arm64) echo linux-arm64 ;;
        *) echo linux-x64 ;;
      esac
      ;;
    Darwin)
      case "$(uname -m)" in
        arm64) echo macos-arm64 ;;
        *) echo macos-x64 ;;
      esac
      ;;
    MINGW*|MSYS*|CYGWIN*) echo windows-x64 ;;
    *) echo linux-x64 ;;
  esac
}
PLATFORM="${TARGET_PLATFORM:-$(detect_platform)}"

# ── 路径与类路径方言 ────────────────────────────────────────────────────
# Git Bash 下的路径是 /d/a/b 形式，而 java.exe / jpackage.exe 只认 D:\a\b；
# 类路径分隔符在 Windows 上是分号。两件事必须一起处理，否则 Windows 构建必挂。
IS_WIN=0
case "$PLATFORM" in windows-*) IS_WIN=1 ;; esac
if [ "$IS_WIN" = 1 ]; then CPSEP=';'; else CPSEP=':'; fi

wp() { # 把 POSIX 路径转成当前平台 Java 能懂的写法
  if [ "$IS_WIN" = 1 ] && command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$1"
  else
    printf '%s' "$1"
  fi
}

# ── JDK ─────────────────────────────────────────────────────────────────
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
  if [ -n "$JAVABIN" ]; then
    JAVA_HOME="$(cd "$(dirname "$JAVABIN")/.." && pwd)"
  fi
fi
JAVA="${JAVA_HOME:-}/bin/java"
if [ -z "${JAVA_HOME:-}" ] || { [ ! -x "$JAVA" ] && [ ! -x "$JAVA.exe" ]; }; then
  echo "✗ 找不到 JDK 17+。请设置 JAVA_HOME（当前：${JAVA_HOME:-<空>}）" >&2
  exit 1
fi

OUT="$ROOT/build/classes"
TMP="${BUILD_TMP:-$ROOT/build/tmp}"
# 版本号唯一来源是仓库根的 version.txt（安装包名与文档也读它）
VERSION="$(tr -d '[:space:]' < "$ROOT/version.txt" 2>/dev/null || echo 0.0.0)"
mkdir -p "$OUT" "$TMP"

# ── Python ──────────────────────────────────────────────────────────────
# 打 jar 用 Python 的 zipfile（见下方说明），Windows runner 上没有 python3 这个名字。
PY="${PYTHON:-}"
if [ -z "$PY" ]; then
  for c in python3 python py; do
    if command -v "$c" >/dev/null 2>&1; then PY="$c"; break; fi
  done
fi
if [ -z "$PY" ]; then
  echo "✗ 找不到 Python（打 jar 需要它）" >&2
  exit 1
fi

RUNTIME_CP=""
for f in "$ROOT"/lib/*.jar; do
  base="$(basename "$f")"
  case "$base" in
    skiko-awt-runtime-*)
      case "$base" in
        *"$PLATFORM"*) RUNTIME_CP="$RUNTIME_CP$(wp "$f")$CPSEP" ;;
        *) : ;;                       # 别的平台的运行时，剔除
      esac
      ;;
    *) RUNTIME_CP="$RUNTIME_CP$(wp "$f")$CPSEP" ;;
  esac
done
if [ -d "$ROOT/tools/platform-libs/$PLATFORM" ]; then
  for f in "$ROOT/tools/platform-libs/$PLATFORM"/*.jar; do
    [ -e "$f" ] && RUNTIME_CP="$RUNTIME_CP$(wp "$f")$CPSEP"
  done
fi
if ! printf '%s' "$RUNTIME_CP" | grep -q "skiko-awt-runtime-$PLATFORM"; then
  echo "✗ classpath 里没有 $PLATFORM 的 skiko 运行时。" >&2
  echo "  先跑：tools/fetch-platform.sh $PLATFORM" >&2
  exit 1
fi

# ── Kotlin 编译器 ───────────────────────────────────────────────────────
# 开发机上它躺在 Gradle 缓存里（内容哈希子目录，路径不固定，只能 find）；
# CI 上没有这个缓存，就按坐标从 Maven 下到 build/.kotlinc。
KOTLINC_HOME="${KOTLINC_HOME:-$HOME/.workbuddy/binaries/gradle/home/caches/modules-2/files-2.1}"
KOTLINC_DL="$ROOT/build/.kotlinc"
MAVEN_BASES="https://repo1.maven.org/maven2 https://maven.aliyun.com/repository/public"

resolve_jar() { # $1=groupId路径 $2=artifactId $3=version
  local gpath="$1" artifact="$2" version="$3"
  local name="${artifact}-${version}.jar"
  if [ -d "$KOTLINC_HOME/$gpath/$artifact/$version" ]; then
    local hit
    hit="$(find "$KOTLINC_HOME/$gpath/$artifact/$version" -name "$name" -print -quit 2>/dev/null || true)"
    if [ -n "$hit" ]; then printf '%s\n' "$hit"; return; fi
  fi
  mkdir -p "$KOTLINC_DL"
  if [ ! -s "$KOTLINC_DL/$name" ]; then
    local base
    for base in $MAVEN_BASES; do
      if curl -fsSL --retry 2 --max-time 180 "$base/$gpath/$artifact/$version/$name" \
           -o "$KOTLINC_DL/$name.part" 2>/dev/null; then
        mv "$KOTLINC_DL/$name.part" "$KOTLINC_DL/$name"
        break
      fi
    done
  fi
  if [ ! -s "$KOTLINC_DL/$name" ]; then
    echo "✗ 下载不到编译器依赖：$artifact:$version" >&2
    exit 1
  fi
  printf '%s\n' "$KOTLINC_DL/$name"
}

KOTLINC_CP=""
# kotlin-compiler-embeddable 把 coroutines 当外部依赖，必须显式补上
for dep in \
  "org/jetbrains/kotlin:kotlin-compiler-embeddable:2.1.0" \
  "org/jetbrains/kotlin:kotlin-stdlib:2.1.0" \
  "org/jetbrains/intellij/deps:trove4j:1.0.20200330" \
  "org/jetbrains/kotlin:kotlin-script-runtime:2.1.0" \
  "org/jetbrains/kotlin:kotlin-reflect:1.6.10" \
  "org/jetbrains:annotations:13.0" \
  "org/jetbrains/kotlinx:kotlinx-coroutines-core-jvm:1.9.0"; do
  IFS=':' read -r gpath artifact version <<< "$dep"
  KOTLINC_CP="$KOTLINC_CP$(wp "$(resolve_jar "$gpath" "$artifact" "$version")")$CPSEP"
done

COMPOSE_PLUGIN="$ROOT/lib/kotlin-compose-compiler-plugin-embeddable-2.1.0.jar"
SERIAL_PLUGIN="$ROOT/lib/kotlin-serialization-compiler-plugin-embeddable-2.1.0.jar"

echo "▸ 编译 Kotlin 源码（Compose 编译器插件，目标平台 $PLATFORM）…"
"$JAVA" -Xms128m -Xmx1200m -Xss512k -XX:MaxMetaspaceSize=448m \
        -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC -XX:-UsePerfData \
        -XX:ActiveProcessorCount=1 "-Djava.io.tmpdir=$(wp "$TMP")" -Dfile.encoding=UTF-8 \
        -cp "$KOTLINC_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
        -no-stdlib -no-reflect \
        -classpath "$RUNTIME_CP" \
        -Xplugin="$(wp "$COMPOSE_PLUGIN")" \
        -Xplugin="$(wp "$SERIAL_PLUGIN")" \
        -jvm-target 17 \
        -d "$(wp "$OUT")" \
        "$(wp "$ROOT/src/main/kotlin")"

echo "▸ 编译完成：$OUT"

case "${1:-}" in
  jar)
    # 不用 JDK 的 jar 工具：它要建线程，本机地址空间紧张时会失败。
    # jar 就是 zip + META-INF/MANIFEST.MF，用 Python 打，全平台一致。
    "$PY" - "$(wp "$ROOT")" "$VERSION" <<'PY'
import os, sys, zipfile
root, version = sys.argv[1], sys.argv[2]
classes = os.path.join(root, "build", "classes")
out = os.path.join(root, "build", "dsh-desktop.jar")
if os.path.exists(out):
    os.remove(out)
manifest = (
    "Manifest-Version: 1.0\r\n"
    "Main-Class: io.github.westanke.dshdesktop.MainKt\r\n"
    "Implementation-Title: DSH Desktop\r\n"
    f"Implementation-Version: {version}\r\n"
    "Created-By: tools/build.sh\r\n\r\n"
)
count = 0
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("META-INF/MANIFEST.MF", manifest)
    for base, _dirs, files in os.walk(classes):
        for f in files:
            full = os.path.join(base, f)
            z.write(full, os.path.relpath(full, classes))
            count += 1
print(f"   {count} 个类文件 · {os.path.getsize(out)} 字节")
PY
    echo "▸ 已生成 $ROOT/build/dsh-desktop.jar"
    ;;
  run)
    exec "$JAVA" -Xmx768m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC \
      -Dfile.encoding=UTF-8 "-Djava.io.tmpdir=$(wp "$TMP")" \
      -cp "$(wp "$OUT")$CPSEP$RUNTIME_CP" io.github.westanke.dshdesktop.MainKt
    ;;
esac
