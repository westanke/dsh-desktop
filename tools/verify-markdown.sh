#!/bin/bash
# 用真实的历史响应验证 **Markdown 解析**（重点盯表格）。
#
# 用法：
#   tools/verify-markdown.sh <history.json>
#   tools/verify-markdown.sh              # 只跑内置用例
#
# 怎么拿到 history.json（令牌先解出来，别提交这个文件）：
#   curl -s -H "Authorization: Bearer $TOKEN" \
#     http://127.0.0.1:3090/api/v1/chat/sessions/<id>/messages > h.json
#
# 判据：内置用例全过 + 真实正文里至少解析出 1 张表格、且每张表的行都能补齐到列数。
# 表格退化（整张表摊成几十行竖线文本）既不报错也不影响编译，只能靠这个脚本抓。
# 它不联网、不改状态，只跑 parseMarkdown / parseInline。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME=${JAVA_HOME:-/home/wangke/.workbuddy/binaries/java/jdk-17.0.20.1+1}
JAVA="$JAVA_HOME/bin/java"
CACHE=${GRADLE_CACHE:-/home/wangke/.workbuddy/binaries/gradle/home/caches/modules-2/files-2.1}
TMP="$ROOT/build/tmp"
OUT="$ROOT/build/verify-md"
mkdir -p "$TMP" "$OUT"

# 先确保主工程编译产物是最新的（parseMarkdown 从 build/classes 取）
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
  -d "$OUT" "$ROOT/tools/VerifyMarkdown.kt" >/dev/null

echo "▸ 验证 ${1:-内置用例}"
exec "$JAVA" -Xmx512m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 \
  -Djava.io.tmpdir="$TMP" -cp "$OUT:$CP" VerifyMarkdownKt "$@"
