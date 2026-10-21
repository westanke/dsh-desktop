#!/bin/sh
# DSH Desktop 免安装启动脚本（Linux / UOS）
#
# 依赖本机已有的 JDK 17+。找不到 JAVA_HOME 时退回 PATH 里的 java。
# 目标机器没有 JDK 时请用 deb 包（自带精简运行时）。
set -e
DIR=$(cd "$(dirname "$0")" && pwd)

JAVA=""
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA=java
else
  echo "找不到 Java 17+。请安装 JDK 后重试，或改用 deb 安装包（自带运行时）。" >&2
  exit 1
fi

exec "$JAVA" \
  -Xmx768m -XX:MaxMetaspaceSize=384m \
  -Dfile.encoding=UTF-8 \
  -cp "$DIR/dsh-desktop.jar:$DIR/lib/*" \
  io.github.westanke.dshdesktop.MainKt "$@"
