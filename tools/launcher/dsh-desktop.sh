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
#
# **失败不能静默**：`.desktop` 里是 `Terminal=false`，从图标启动时 stdout/stderr
# 全部丢失。早先版本只往 stderr 打字，用户看到的只是"点了没反应"。现在所有失败
# 都走 fail()：写日志 + 弹窗（zenity/kdialog/xmessage/notify-send 有哪个用哪个）。
#
# 手动兜底（内网 / 完全离线）：
#   DSH_RUNTIME_HOME=/path/to/runtime ./dsh-desktop     指向一份已有的运行时
#   DSH_RUNTIME_TARBALL=/path/to/runtime.tar.xz ./dsh-desktop   直接吃本地压缩包
# 诊断：
#   DSH_LAUNCHER_DRY_RUN=1 ./dsh-desktop    只走"找运行时/下载/解压"，
#   把最终要执行的 java 命令打印出来但不启动 —— 排查"到底用了哪个运行时"用。
#   日志：~/.cache/dsh-desktop/launcher.log
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

CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/dsh-desktop"
LOG="$CACHE_DIR/launcher.log"

JAVA_BIN="$BUNDLED/bin/java"
[ -x "$JAVA_BIN" ] || JAVA_BIN="$RT_HOME/bin/java"

log() {
  mkdir -p "$CACHE_DIR" 2>/dev/null || return 0
  printf '%s %s\n' "$(date '+%F %T')" "$*" >>"$LOG" 2>/dev/null || return 0
}

note() { command -v notify-send >/dev/null 2>&1 && notify-send "DSH Desktop" "$1" 2>/dev/null; return 0; }

# 从图标启动时没有终端：必须弹窗，否则用户只会看到"点了没反应"。
# 有终端（`-t 2`）就不弹，免得终端里跑脚本还蹦一个窗口。
# 弹窗一律带 60 秒超时：脚本/CI 里万一没人点，不能把进程吊死在那儿。
# 需要完全静默（自动化测试）：DSH_LAUNCHER_NO_DIALOG=1。
dialog() {
  [ "${DSH_LAUNCHER_NO_DIALOG:-0}" = "1" ] && return 0
  [ -t 2 ] && return 0
  if command -v zenity >/dev/null 2>&1; then
    zenity --error --no-wrap --timeout=60 --title "DSH Desktop" --text "$1" 2>/dev/null && return 0
  fi
  if command -v kdialog >/dev/null 2>&1; then
    kdialog --title "DSH Desktop" --timeout 60 --error "$1" 2>/dev/null && return 0
  fi
  if command -v xmessage >/dev/null 2>&1; then
    xmessage -center -timeout 60 -title "DSH Desktop" "$1" 2>/dev/null && return 0
  fi
  notify-send "DSH Desktop 启动失败" "$1" 2>/dev/null
  return 0
}

# 统一的失败出口：stderr（终端里看得见）+ 日志 + 弹窗
fail() {
  printf '✗ %s\n' "$1" >&2
  log "✗ $1"
  dialog "DSH Desktop 启动失败

$1

日志：$LOG"
  exit 1
}

download() { # $1=url $2=目标
  if command -v curl >/dev/null 2>&1; then
    curl -fL --connect-timeout 10 --retry 2 -C - -o "$2" "$1"
  elif command -v wget >/dev/null 2>&1; then
    wget -c -T 10 -t 2 -O "$2" "$1"
  else
    echo "系统里既没有 curl 也没有 wget，无法下载运行时。" >&2
    return 1
  fi
}

log "── 启动 v$VERSION（$PLATFORM）"
log "   APP_HOME=$APP_HOME"
log "   java=$JAVA_BIN"

if [ ! -x "$JAVA_BIN" ]; then
  # ── 本地压缩包兜底（内网分发常用：把 tar.xz 放到安装目录）──────────────
  if [ -n "${DSH_RUNTIME_TARBALL:-}" ] && [ -f "$DSH_RUNTIME_TARBALL" ]; then
    note "正在解压本地运行时…"
    log "   用本地压缩包 $DSH_RUNTIME_TARBALL"
    mkdir -p "$RT_HOME" || fail "无法创建 $RT_HOME（权限？）"
    tar -xJf "$DSH_RUNTIME_TARBALL" -C "$RT_HOME" || fail "解压本地运行时失败：$DSH_RUNTIME_TARBALL"
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
  log "   下载 $RT_NAME"
  # 临时文件放**用户缓存目录**而不是 /tmp：/tmp 在某些机器上是 noexec、只读，
  # 或者空间很小的 tmpfs，curl 写到一半报 (23) Failed writing body（真机踩过）。
  DL_DIR="$CACHE_DIR/downloads"
  mkdir -p "$DL_DIR" 2>/dev/null || DL_DIR="${TMPDIR:-/tmp}"
  TMP_TAR="$DL_DIR/$RT_NAME.$$"
  ok=""
  for url in $URLS; do
    echo "  尝试 $url"
    log "   尝试 $url"
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
          log "  ✗ sha256 不匹配（want=$want got=$got）"
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
    # 这条最常撞：发行版里**没挂**该版本的运行时资产（404）。把地址和手动办法
    # 一并给出来，用户不用去翻日志猜。
    log "   ✗ 所有源都失败：$URLS"
    fail "运行环境下载失败（网络不通，或该版本的发行版里没有运行时资产）。

可以手动兜底，任选一种：
1. 打开 https://gitee.com/$REPO/releases 下载 $RT_NAME，
   解压后这样启动：DSH_RUNTIME_HOME=<解压目录> $0
2. 或把压缩包留下：DSH_RUNTIME_TARBALL=<路径>/$RT_NAME $0

下载地址：
$(echo "$URLS" | sed 's/^ *//')"
  fi

  mkdir -p "$RT_HOME" || fail "无法创建 $RT_HOME（权限？）"
  echo "▸ 解压到 $RT_HOME"
  log "   解压到 $RT_HOME"
  tar -xJf "$TMP_TAR" -C "$RT_HOME" || fail "解压失败（系统缺 xz 工具？）：$TMP_TAR"
  rm -f "$TMP_TAR"
  JAVA_BIN="$RT_HOME/bin/java"
  [ -x "$JAVA_BIN" ] || fail "解压出来的运行时里没有 bin/java：$RT_HOME"
  note "运行环境就绪"
fi

if [ "${DSH_LAUNCHER_DRY_RUN:-0}" = "1" ]; then
  echo "DRY-RUN java = $JAVA_BIN"
  echo "DRY-RUN classpath = $APP_HOME/lib/app/*"
  echo "DRY-RUN main = io.github.westanke.dshdesktop.MainKt $*"
  exit 0
fi

# 不用 exec：要在 java 起不来（缺库、线程数限制、JVM 参数写错）时还能弹窗。
# 从图标启动没有终端，java 的 stderr 也一并写进日志，否则崩溃现场彻底消失。
if [ -t 2 ]; then
  "$JAVA_BIN" \
    -Xmx768m -XX:MaxMetaspaceSize=384m \
    -Dfile.encoding=UTF-8 \
    -Dskiko.renderApi=SOFTWARE \
    -cp "$APP_HOME/lib/app/*" \
    io.github.westanke.dshdesktop.MainKt "$@"
  code=$?
else
  "$JAVA_BIN" \
    -Xmx768m -XX:MaxMetaspaceSize=384m \
    -Dfile.encoding=UTF-8 \
    -Dskiko.renderApi=SOFTWARE \
    -cp "$APP_HOME/lib/app/*" \
    io.github.westanke.dshdesktop.MainKt "$@" 2>>"$LOG"
  code=$?
fi

# 1..128 才算"启动失败"；128+ 是信号（注销、被杀），不该弹窗吓人
if [ "$code" -ge 1 ] && [ "$code" -le 128 ]; then
  log "✗ 进程退出码 $code"
  fail "客户端异常退出（退出码 $code），详情见日志：$LOG"
fi
log "── 正常退出（$code）"
exit "$code"
