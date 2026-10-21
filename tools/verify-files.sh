#!/bin/bash
# 验证**文件类型判定与文本解码**（`data/FileKinds.kt`）。
#
# 用法：tools/verify-files.sh
#
# 为什么单独一个脚本：这两个函数决定"文件页到底怎么显示"，错了不会报错、
# 只会显示得不对——GBK 文件变乱码、冒充 md 的二进制被当文本渲染、PDF 走了内嵌预览
# 然后一片空白。这类问题编译期一律看不出来，只能靠用例钉住。
#
# 不联网、不改状态，只跑 classifyFile / decodeText / refineByContent / imageFormatOf。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME=${JAVA_HOME:-/home/wangke/.workbuddy/binaries/java/jdk-17.0.20.1+1}
JAVA="$JAVA_HOME/bin/java"
CACHE=${GRADLE_CACHE:-/home/wangke/.workbuddy/binaries/gradle/home/caches/modules-2/files-2.1}
TMP="$ROOT/build/tmp"
OUT="$ROOT/build/verify-files"
mkdir -p "$TMP" "$OUT"

if [ ! -d "$ROOT/build/classes" ]; then
  echo "▸ build/classes 不存在，先编译"
  "$ROOT/tools/build.sh"
fi

KC_DL="$ROOT/build/.kotlinc"
cpjar() { # $1=group/artifact/version  $2=jar 名；CI 上没有 Gradle 缓存，回退 build/.kotlinc
  local hit
  hit="$(find "$CACHE/$1" -name "$2" -print -quit 2>/dev/null || true)"
  [ -n "$hit" ] && { printf '%s' "$hit"; return; }
  find "$KC_DL" -name "$2" -print -quit 2>/dev/null || true
}
KC="$(cpjar org.jetbrains.kotlin/kotlin-compiler-embeddable/2.1.0 kotlin-compiler-embeddable-2.1.0.jar)"
KC="$KC:$(cpjar org.jetbrains.kotlin/kotlin-stdlib/2.1.0 kotlin-stdlib-2.1.0.jar)"
KC="$KC:$(cpjar org.jetbrains.intellij.deps/trove4j/1.0.20200330 trove4j-1.0.20200330.jar)"
KC="$KC:$(cpjar org.jetbrains.kotlin/kotlin-script-runtime/2.1.0 kotlin-script-runtime-2.1.0.jar)"
KC="$KC:$(cpjar org.jetbrains.kotlin/kotlin-reflect/1.6.10 kotlin-reflect-1.6.10.jar)"
KC="$KC:$(cpjar org.jetbrains/annotations/13.0 annotations-13.0.jar)"
KC="$KC:$(cpjar org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.9.0 kotlinx-coroutines-core-jvm-1.9.0.jar)"

CP="$ROOT/build/classes:$(ls "$ROOT"/lib/*.jar | tr '\n' ':')"

echo "▸ 编译验证程序"
"$JAVA" -Xmx900m -Xss512k -XX:+UseSerialGC -XX:ActiveProcessorCount=1 \
  -Djava.io.tmpdir="$TMP" -cp "$KC" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -classpath "$CP" -jvm-target 17 \
  -d "$OUT" "$ROOT/tools/VerifyFileKinds.kt" >/dev/null

echo "▸ 跑内置用例"
exec "$JAVA" -Xmx512m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 \
  -Djava.io.tmpdir="$TMP" -cp "$OUT:$CP" VerifyFileKindsKt
