#!/bin/bash
# 用真实的历史响应验证投影，防「会话整页空白」回归。
#
# 用法：
#   tools/verify-history.sh <history.json>
#
# 怎么拿到 history.json（令牌先解出来，别提交这个文件）：
#   curl -s -H "Authorization: Bearer $TOKEN" \
#     http://127.0.0.1:3090/api/v1/chat/sessions/<id>/messages > h.json
#
# 判据：事件数 > 0 而可渲染条目数 = 0 → 退出码 2（回归）。
# 它不联网、不改状态，只跑 parseHistory。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME=${JAVA_HOME:-/home/wangke/.workbuddy/binaries/java/jdk-17.0.20.1+1}
JAVA="$JAVA_HOME/bin/java"
CACHE=${GRADLE_CACHE:-/home/wangke/.workbuddy/binaries/gradle/home/caches/modules-2/files-2.1}
TMP="$ROOT/build/tmp"
OUT="$ROOT/build/verify"
mkdir -p "$TMP" "$OUT"

if [ $# -lt 1 ]; then
  sed -n '3,12p' "$0" >&2
  exit 3
fi

# 先确保主工程编译产物是最新的（parseHistory 从 build/classes 取）
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
  -d "$OUT" "$ROOT/tools/VerifyHistory.kt" >/dev/null

echo "▸ 验证 $(basename "$1")"
exec "$JAVA" -Xmx512m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 \
  -Djava.io.tmpdir="$TMP" -cp "$OUT:$CP" VerifyHistoryKt "$1"
