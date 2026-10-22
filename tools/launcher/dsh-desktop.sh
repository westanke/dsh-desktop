#!/bin/sh
# dsh-desktop 启动器（Linux）。
#
# 存在的原因：轻量安装包里**不含 JRE**（那 50 多 MB 是安装包的大头）。
# 第一次启动时按下面顺序拿运行时：
#   1. 安装目录里自带的（完整包就这样，直接用，不联网）；
#   2. 用户数据目录里已下载过的（第二次起就复用）；
#   3. 都没有 → 下载一份解压到用户数据目录，然后启动。
#
# 下载源按"国内优先"排：Gitee 发行版在国内有 CDN，GitHub 在国外。
# 两个都试，哪个先通用哪个；也可以 `DSH_RUNTIME_MIRROR=github` 强制换序。
# 全程不弹窗、不卡死：失败就把原因和手动下载地址说清楚，退出码非 0。
#
# 手动兜底（内网 / 完全离线）：
#   DSH_RUNTIME_HOME=/path/to/runtime ./dsh-desktop     指向一份已有的运行时
#   DSH_RUNTIME_TARBALL=/path/to/runtime.tar.xz ./dsh-desktop   直接吃本地压缩包
# 诊断：
#   DSH_LAUNCHER_DRY_RUN=1 ./dsh-desktop    只走"找运行时/下载/解压"，
#   把最终要执行的 java 命令打印出来但不启动 —— 排查"到底用了哪个运行时"用。
set -u

APP_HOME=$(cd "$(dirname "$0")/.." && pwd)
VERSION=$(cat "$APP_HOME/lib/app/VERSION" 2>/dev/null || echo "unknown")
PLATFORM=$(uname -s | tr 'A-Z' 'a-z')   # linux / darwin
case "$(uname -m)" in
  x86_64|amd64) PLATFORM="$PLATFORM-x64" ;;
  aarch64|arm64) PLATFORM="$PLATFORM-arm64" ;;
  *) PLATFORM="$PLATFORM-$(uname -m)" ;;
esac
RT_NAME="dsh-desktop-runtime-$VERSION-$PLATFORM.tar.xz"
RT_BASE="dsh-desktop-runtime-$VERSION-$PLATFORM"

# 运行时落地位置：优先安装目录里自带的，其次用户数据目录
BUNDLED="$APP_HOME/lib/runtime"
RT_HOME="${DSH_RUNTIME_HOME:-${XDG_DATA_HOME:-$HOME/.local/share}/dsh-desktop/$RT_BASE}"

JAVA_BIN="$BUNDLED/bin/java"
[ -x "$JAVA_BIN" ] || JAVA_BIN="$RT_HOME/bin/java"

note() { command -v notify-send >/dev/null 2>&1 && notify-send "DSH Desktop" "$1" 2>/dev/null; return 0; }

download() { # $1=url $2=目标
  if command -v curl >/dev/null 2>&1; then
    curl -fL --connect-timeout 10 --retry 2 -C - -o "$2" "$1"
  elif command -v wget >/dev/null 2>&1; then
    wget -c -T 10 -t 2 -O "$2" "$1"
  else
    echo "✗ 系统里既没有 curl 也没有 wget，无法下载运行时。" >&2
    return 1
  fi
}

if [ ! -x "$JAVA_BIN" ]; then
  # ── 本地压缩包兜底（内网分发常用：把 tar.xz 放到安装目录）──────────────
  if [ -n "${DSH_RUNTIME_TARBALL:-}" ] && [ -f "$DSH_RUNTIME_TARBALL" ]; then
    note "正在解压本地运行时…"
    mkdir -p "$RT_HOME" && tar -xJf "$DSH_RUNTIME_TARBALL" -C "$RT_HOME"
    JAVA_BIN="$RT_HOME/bin/java"
  fi
fi

if [ ! -x "$JAVA_BIN" ]; then
  REPO="${DSH_RUNTIME_REPO:-westanke/dsh-desktop}"
  # 国内优先：Gitee 在前。GitHub 在后。可用 DSH_RUNTIME_MIRROR=github 对调。
  if [ "${DSH_RUNTIME_MIRROR:-gitee}" = "github" ]; then
    URLS="https://github.com/$REPO/releases/download/v$VERSION/$RT_NAME
          https://gitee.com/$REPO/releases/download/v$VERSION/$RT_NAME"
  else
    URLS="https://gitee.com/$REPO/releases/download/v$VERSION/$RT_NAME
          https://github.com/$REPO/releases/download/v$VERSION/$RT_NAME"
  fi

  note "正在下载运行环境（约 25 MB，只此一次）…"
  echo "▸ 首次启动：下载运行环境 $RT_NAME（约 25 MB，只此一次）"
  # 临时文件放**用户缓存目录**而不是 /tmp：/tmp 在某些机器上是 noexec、只读，
  # 或者空间很小的 tmpfs，curl 写到一半报 (23) Failed writing body（真机踩过）。
  DL_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/dsh-desktop/downloads"
  mkdir -p "$DL_DIR" 2>/dev/null || DL_DIR="${TMPDIR:-/tmp}"
  TMP_TAR="$DL_DIR/$RT_NAME.$$"
  ok=""
  for url in $URLS; do
    echo "  尝试 $url"
    # 注意：**不能**写成 `download … | sed` —— 管道的退出码是 sed 的，
    # curl 404 也会被当成成功，接着拿空文件去校验，报的错完全对不上真因（踩过）。
    if download "$url" "$TMP_TAR"; then
      # 校验：随包的 sha256（打包时生成）；没有文件就跳过，不因此卡死用户
      SUM_FILE="$APP_HOME/lib/app/$RT_BASE.sha256"
      if [ -f "$SUM_FILE" ]; then
        want=$(awk '{print $1}' "$SUM_FILE")
        got=$(sha256sum "$TMP_TAR" | awk '{print $1}')
        if [ "$want" != "$got" ]; then
          echo "  ✗ 校验不过（期望 $want，实得 $got），换源重试" >&2
          rm -f "$TMP_TAR"; continue
        fi
        echo "  ✓ sha256 校验通过"
      fi
      ok=1; break
    fi
    rm -f "$TMP_TAR"
  done

  if [ -z "$ok" ]; then
    note "运行环境下载失败"
    echo "✗ 运行环境下载失败。可手动下载后放到任意目录，再用它启动：" >&2
    echo "    DSH_RUNTIME_HOME=<解压后的目录> $0" >&2
    echo "  或把压缩包放到安装目录旁：DSH_RUNTIME_TARBALL=$RT_NAME $0" >&2
    echo "  下载地址（任选其一）：" >&2
    for url in $URLS; do echo "    $url" >&2; done
    exit 1
  fi

  mkdir -p "$RT_HOME"
  echo "▸ 解压到 $RT_HOME"
  tar -xJf "$TMP_TAR" -C "$RT_HOME" || { echo "✗ 解压失败（xz 工具缺失？）" >&2; exit 1; }
  rm -f "$TMP_TAR"
  JAVA_BIN="$RT_HOME/bin/java"
  [ -x "$JAVA_BIN" ] || { echo "✗ 解压出来的运行时里没有 bin/java" >&2; exit 1; }
  note "运行环境就绪"
fi

if [ "${DSH_LAUNCHER_DRY_RUN:-0}" = "1" ]; then
  echo "DRY-RUN java = $JAVA_BIN"
  echo "DRY-RUN classpath = $APP_HOME/lib/app/*"
  echo "DRY-RUN main = io.github.westanke.dshdesktop.MainKt $*"
  exit 0
fi

exec "$JAVA_BIN" \
  -Xmx768m -XX:MaxMetaspaceSize=384m \
  -Dfile.encoding=UTF-8 \
  -Dskiko.renderApi=SOFTWARE \
  -cp "$APP_HOME/lib/app/*" \
  io.github.westanke.dshdesktop.MainKt "$@"
