#!/bin/bash
# 下载目标平台的 skiko 原生运行时到 tools/platform-libs/<platform>/。
#
# 为什么需要它：skiko 把"跨平台 API"和"每平台的原生库"拆成两个 artifact。
# lib/ 里入库的是开发机用的 linux-x64 运行时；打 Windows 包或 ARM64 包时，
# 必须换成对应平台那一份，否则包里带的是跑不起来的 .so。
#
# 用法：
#   tools/fetch-platform.sh linux-arm64
#   tools/fetch-platform.sh windows-x64
#   tools/fetch-platform.sh            # 按当前机器推断
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="0.8.18"

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
PLATFORM="${1:-$(detect_platform)}"

case "$PLATFORM" in
  linux-x64|linux-arm64|windows-x64) ;;
  *)
    echo "✗ 不支持的平台：$PLATFORM（可选 linux-x64 / linux-arm64 / windows-x64）" >&2
    exit 2
    ;;
esac

ARTIFACT="skiko-awt-runtime-$PLATFORM"
NAME="$ARTIFACT-$VERSION.jar"
DEST="$ROOT/tools/platform-libs/$PLATFORM"
mkdir -p "$DEST"

if [ -s "$DEST/$NAME" ]; then
  echo "▸ $PLATFORM 运行时已在本地：$DEST/$NAME"
  exit 0
fi

# 本地种子优先（离线也能用）：lib/ 里有当前平台那份，tools/windows-libs/ 是历史留的 Windows 那份
for seed in "$ROOT/lib/$NAME" "$ROOT/tools/windows-libs/$NAME"; do
  if [ -s "$seed" ]; then
    cp "$seed" "$DEST/$NAME"
    echo "▸ 从本地种子复制：$(basename "$seed") → $DEST/$NAME"
    exit 0
  fi
done

GPATH="org/jetbrains/skiko/$ARTIFACT/$VERSION"
for base in "https://repo1.maven.org/maven2" "https://maven.aliyun.com/repository/public"; do
  echo "▸ 下载 $PLATFORM 运行时（$base）…"
  if curl -fsSL --retry 2 --max-time 300 "$base/$GPATH/$NAME" -o "$DEST/$NAME.part" 2>/dev/null; then
    mv "$DEST/$NAME.part" "$DEST/$NAME"
    break
  fi
done

if [ ! -s "$DEST/$NAME" ]; then
  echo "✗ 下载失败：$ARTIFACT:$VERSION" >&2
  exit 1
fi

# 简单校验：必须是 zip（jar）——Maven 返回错误页时也会是 200 + HTML，别把坏文件当依赖
PY="${PYTHON:-}"
if [ -z "$PY" ]; then
  for c in python3 python py; do
    command -v "$c" >/dev/null 2>&1 && { PY="$c"; break; }
  done
fi
if ! "$PY" -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1])" "$DEST/$NAME" 2>/dev/null; then
  echo "✗ 下到的不是有效 jar：$DEST/$NAME" >&2
  rm -f "$DEST/$NAME"
  exit 1
fi

echo "▸ 就绪：$DEST/$NAME（$(du -h "$DEST/$NAME" | cut -f1)）"
