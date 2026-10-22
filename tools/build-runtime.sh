#!/bin/bash
# 把运行时镜像打成**可下载的压缩包**（轻量安装包第一次启动时拉它）。
#
# 产物（dist/ 下）：
#   dsh-desktop-runtime-<版本>-<平台>-<架构>.tar.xz   运行时本体（约 25 MB）
#   dsh-desktop-runtime-<版本>-<平台>-<架构>.sha256    引导器下载后当场校验用
#
# 为什么要单独一个脚本：完整包与轻包共用同一个 jlink 镜像（package.sh 产出
# build/runtime-image），这里只负责"打包 + 算校验和"，不重新 jlink ——
# 否则轻包用户下载到的运行时和完整包里的不是一份字节，行为可能对不上。
#
# 用法：
#   tools/build-runtime.sh                    按当前平台
#   TARGET_PLATFORM=linux-arm64 tools/build-runtime.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="$(tr -d '[:space:]' < "$ROOT/version.txt" 2>/dev/null || true)"
[ -n "$VERSION" ] || { echo "✗ 读不到 version.txt" >&2; exit 1; }

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
case "$PLATFORM" in
  linux-arm64) TAG="arm64" ;;
  windows-x64) TAG="x64" ;;
  *) TAG="x64" ;;
esac

RT_IMAGE="${RUNTIME_IMAGE:-$ROOT/build/runtime-image}"
[ -x "$RT_IMAGE/bin/java" ] || [ -x "$RT_IMAGE/bin/java.exe" ] || {
  echo "✗ 没有运行时镜像（$RT_IMAGE）。先跑 tools/package.sh 生成它" >&2; exit 1; }

# 顶层目录名必须和引导器拼的 RT_BASE 一致（引导器解压后期望 <RT_BASE>/bin/java）
# $PLATFORM 已含架构：linux-x64 / linux-arm64 / windows-x64
RT_BASE="dsh-desktop-runtime-$VERSION-$PLATFORM"
STAGE="$ROOT/dist/$RT_BASE"
OUT="$ROOT/dist/$RT_BASE.tar.xz"
if [ -d "$STAGE" ]; then mv "$STAGE" "$ROOT/dist/.rt-stage-old-$(date +%s)"; fi
cp -r "$RT_IMAGE" "$STAGE"

# **不打顶层目录**（`-C "$STAGE" .`）：引导器把内容直接解压到
# ~/.local/share/dsh-desktop/<RT_BASE>/ 下，多套一层目录就会变成
# <RT_BASE>/<RT_BASE>/bin/java，引导器按 bin/java 找不到，然后白下一遍。
# xz -9e：压缩率比默认高一截；解压只多不到一秒
tar -cJf "$OUT" -C "$STAGE" .
(cd "$ROOT/dist" && sha256sum "$RT_BASE.tar.xz" > "$RT_BASE.sha256")
mv "$STAGE" "$ROOT/build/.rt-stage-done-$(date +%s)" 2>/dev/null || true

echo "▸ 产物："
ls -lh "$OUT" "$ROOT/dist/$RT_BASE.sha256" | sed 's/^/   /'
echo "   sha256: $(awk '{print $1}' "$ROOT/dist/$RT_BASE.sha256")"
