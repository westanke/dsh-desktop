@echo off
rem ============================================================
rem  DSH Desktop — Windows 构建与打包
rem
rem  这里只是 tools\build.sh 的薄封装：编译与打包逻辑只有一份 bash 脚本，
rem  Windows 与 UOS、CI 三处共用，避免两套流程漂移。
rem
rem  要求：
rem    · JDK 17+（JAVA_HOME 已设置，或 java 在 PATH 里）
rem    · Git for Windows（提供 bash、cygpath、curl）
rem    · Python 3（打 jar 用，命令行名 python / py 均可）
rem    · WiX Toolset 3.x（可选，只有想打 msi 才需要）
rem
rem  用法：
rem    build-windows.bat            编译 + 打免安装 zip
rem    build-windows.bat run        编译后直接启动窗口
rem
rem  产物：dist\dsh-desktop-1.0.0-windows-x64.zip（有 WiX 时另出 msi）
rem ============================================================
setlocal

where bash >nul 2>nul
if errorlevel 1 (
  echo [错误] 找不到 bash。请安装 Git for Windows，并确保 bash.exe 在 PATH 里。
  exit /b 1
)

if "%JAVA_HOME%"=="" (
  where java >nul 2>nul
  if errorlevel 1 (
    echo [错误] 找不到 java，请先安装 JDK 17 并设置 JAVA_HOME。
    exit /b 1
  )
)

if /i "%~1"=="run" (
  echo [1/1] 编译并启动 ...
  bash "%~dp0tools\build.sh" run
  exit /b %errorlevel%
)

echo [1/2] 拉取 Windows 平台运行时 ...
bash "%~dp0tools\fetch-platform.sh" windows-x64
if errorlevel 1 (
  echo [失败] 运行时下载未完成。
  exit /b 1
)

echo [2/2] 编译并打包 ...
bash "%~dp0tools\package.sh"
if errorlevel 1 (
  echo [失败] 打包未完成，请查看上面的报错。
  exit /b 1
)

echo.
echo 完成，产物在 dist\ 下：
dir /b dist
endlocal
