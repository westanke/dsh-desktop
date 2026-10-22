@echo off
rem dsh-desktop 启动器（Windows）。与 tools/launcher/dsh-desktop.sh 同构：
rem 轻量包不含 JRE，第一次启动下载一份到 %LOCALAPPDATA%\dsh-desktop\ 下。
rem
rem 下载源国内优先（Gitee 在前、GitHub 在后），DSH_RUNTIME_MIRROR=github 可对调；
rem 离线兜底：DSH_RUNTIME_HOME 指向已有运行时，DSH_RUNTIME_TARBALL 指向本地压缩包。
rem 诊断：日志在 %LOCALAPPDATA%\dsh-desktop\launcher.log（双击启动时控制台会一闪而过，
rem 失败原因只能从日志看，所以每条失败都要落日志 + 双击场景下 pause）。
setlocal enabledelayedexpansion
set APPHOME=%~dp0..
if "%APPHOME:~-1%"=="\" set APPHOME=%APPHOME:~0,-1%

set LOGDIR=%LOCALAPPDATA%\dsh-desktop
if not exist "%LOGDIR%" mkdir "%LOGDIR%" >nul 2>&1
set LOG=%LOGDIR%\launcher.log

set /p VERSION=<"%APPHOME%\lib\app\VERSION"
if not defined VERSION set VERSION=unknown
set RTNAME=dsh-desktop-runtime-%VERSION%-windows-x64
set RTBASE=dsh-desktop-runtime-%VERSION%-windows-x64
set RTHOME=%DSH_RUNTIME_HOME%
if not defined RTHOME set RTHOME=%LOCALAPPDATA%\dsh-desktop\%RTBASE%

echo [%DATE% %TIME%] 启动 v%VERSION%（windows-x64）>>"%LOG%"
echo [%DATE% %TIME%]   APPHOME=%APPHOME%>>"%LOG%"

set JAVA=%RTHOME%\bin\java.exe
if exist "%APPHOME%\lib\runtime\bin\java.exe" (
  rem 完整包：用自带的
  set JAVA=%APPHOME%\lib\runtime\bin\java.exe
)

if not exist "%JAVA%" (
  if defined DSH_RUNTIME_TARBALL (
    if exist "!DSH_RUNTIME_TARBALL!" (
      echo 解压本地运行时…
      echo [%DATE% %TIME%]   本地压缩包 !DSH_RUNTIME_TARBALL!>>"%LOG%"
      if not exist "%RTHOME%" mkdir "%RTHOME%"
      tar -xJf "!DSH_RUNTIME_TARBALL!" -C "%RTHOME%"
      set JAVA=%RTHOME%\bin\java.exe
    )
  )
)

if not exist "%JAVA%" (
  set REPO=westanke/dsh-desktop
  if /i "%DSH_RUNTIME_MIRROR%"=="github" (
    set URL1=https://github.com/%REPO%/releases/download/v%VERSION%/%RTNAME%.tar.xz
    set URL2=https://gitee.com/%REPO%/releases/download/v%VERSION%/%RTNAME%.tar.xz
  ) else (
    set URL1=https://gitee.com/%REPO%/releases/download/v%VERSION%/%RTNAME%.tar.xz
    set URL2=https://github.com/%REPO%/releases/download/v%VERSION%/%RTNAME%.tar.xz
  )
  echo 首次启动：下载运行环境 %RTNAME%.tar.xz（约 25 MB，只此一次）
  echo [%DATE% %TIME%]   下载 %RTNAME%.tar.xz>>"%LOG%"
  set TARTMP=%TEMP%\%RTNAME%.tar.xz
  set GOT=0
  for %%U in ("!URL1!" "!URL2!") do (
    if "!GOT!"=="0" (
      echo 尝试 %%~U
      echo [%DATE% %TIME%]   尝试 %%~U>>"%LOG%"
      curl -fL --connect-timeout 10 --retry 2 -C - -o "%TARTMP%" "%%~U"
      if not errorlevel 1 set GOT=1
    )
  )
  rem 这里不做 sha256 校验：curl 的 -f 与 --retry 已经挡掉大部分坏文件，
  rem 剩下半截的也会在 tar 解压时暴露（xz 流有完整性校验）。
  rem Linux 侧引导器做校验，因为 sha256sum 在 Linux 上随手就有、Windows 上没有等价物。
  if "!GOT!"=="1" (
    if not exist "%RTHOME%" mkdir "%RTHOME%"
    echo 解压到 %RTHOME%
    echo [%DATE% %TIME%]   解压到 %RTHOME%>>"%LOG%"
    tar -xJf "%TARTMP%" -C "%RTHOME%"
    del "%TARTMP%"
    set JAVA=%RTHOME%\bin\java.exe
  )
)

if not exist "%JAVA%" goto :nodownload

"%JAVA%" -Xmx768m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8 ^
  -Dskiko.renderApi=SOFTWARE -cp "%APPHOME%\lib\app\*" ^
  io.github.westanke.dshdesktop.MainKt %*
set CODE=%ERRORLEVEL%
if not "%CODE%"=="0" (
  echo [%DATE% %TIME%] 进程退出码 %CODE%>>"%LOG%"
  echo.
  echo 客户端异常退出（退出码 %CODE%），详情见日志：
  echo   %LOG%
  call :pause_if_clicked
)
exit /b %CODE%

:nodownload
echo.
echo 运行环境下载失败。可手动下载后任选一种方式启动：
echo   set DSH_RUNTIME_HOME=解压后的目录 然后重新运行本脚本
echo   set DSH_RUNTIME_TARBALL=%RTNAME%.tar.xz 然后重新运行本脚本
echo 下载地址：
echo   https://gitee.com/westanke/dsh-desktop/releases/download/v%VERSION%/%RTNAME%.tar.xz
echo   https://github.com/westanke/dsh-desktop/releases/download/v%VERSION%/%RTNAME%.tar.xz
echo   https://gitee.com/westanke/dsh-desktop/releases
echo.
echo [%DATE% %TIME%] 运行环境下载失败（或该版本发行版里没有运行时资产）>>"%LOG%"
call :pause_if_clicked
exit /b 1

:pause_if_clicked
rem 双击启动（cmd /c）时控制台会立刻关闭，必须停一下让人看清；
rem 从已有终端里调用就不停，免得卡住批处理。
echo %cmdcmdline% | find /i "/c" >nul && pause
goto :eof
