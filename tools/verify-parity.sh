#!/bin/bash
# 第二批对标（P2-1 / P2-2 / P3）的纯函数回归。
#
# 为什么单开一个脚本：这批新增的纯函数分散在三处（schema 摊平、版本比较、
# SHA256SUMS 解析、档位判定），塞进既有脚本会让各自的"在测什么"变糊。
# 用法：tools/verify-parity.sh（无参数，全部内置用例）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME=${JAVA_HOME:-/home/wangke/.workbuddy/binaries/java/jdk-17.0.20.1+1}
JAVA="$JAVA_HOME/bin/java"
CACHE=${GRADLE_CACHE:-/home/wangke/.workbuddy/binaries/gradle/home/caches/modules-2/files-2.1}
TMP="$ROOT/build/tmp"
OUT="$ROOT/build/verify"
mkdir -p "$TMP" "$OUT"

# 先确保主工程编译产物是最新的（纯函数从 build/classes 取）
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
KC="$KC:$(cpjar org.jetbrains.annotations/13.0 annotations-13.0.jar)"
KC="$KC:$(cpjar org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.9.0 kotlinx-coroutines-core-jvm-1.9.0.jar)"

CP="$ROOT/build/classes:$(ls "$ROOT"/lib/*.jar | tr '\n' ':')"

echo "▸ 编译验证程序"
"$JAVA" -Xmx900m -Xss512k -XX:+UseSerialGC -XX:ActiveProcessorCount=1 \
  -Djava.io.tmpdir="$TMP" -cp "$KC" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -classpath "$CP" -jvm-target 17 \
  -d "$OUT" "$ROOT/tools/VerifyParity.kt" >/dev/null

echo "▸ 跑内置用例"
exec "$JAVA" -Xmx512m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 \
  -Djava.io.tmpdir="$TMP" -cp "$OUT:$CP" VerifyParityKt
