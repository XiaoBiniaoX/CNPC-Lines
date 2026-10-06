@echo off
setlocal EnableExtensions

title CNPC-Lines GitHub Import

REM ============================================================
REM CNPC-Lines 一体化导入脚本
REM
REM 本地项目：
REM C:\Users\ASUS\Desktop\Mod开发\CNPC lines\1.20.1Forge
REM
REM GitHub：
REM https://github.com/XiaoBiniaoX/CNPC-Lines
REM
REM 注意：
REM - 不覆盖远程仓库
REM - 不使用 force push
REM - 会产生正常 Git commit
REM - 不修改原始开发目录
REM ============================================================

set "SOURCE=C:\Users\ASUS\Desktop\Mod开发\CNPC lines\1.20.1Forge"
set "REPO=https://github.com/XiaoBiniaoX/CNPC-Lines.git"
set "WORK=%TEMP%\CNPC-Lines-GitImport"

echo.
echo ==========================================
echo        CNPC-Lines GitHub Import
echo ==========================================
echo.
echo Source:
echo %SOURCE%
echo.
echo Repository:
echo %REPO%
echo.

REM ------------------------------------------------------------
REM 检查 Git
REM ------------------------------------------------------------

where git >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Git 未找到。
    echo 请先安装 Git，并确保 git.exe 在 PATH 中。
    pause
    exit /b 1
)

REM ------------------------------------------------------------
REM 检查源目录
REM ------------------------------------------------------------

if not exist "%SOURCE%\" (
    echo [ERROR] 找不到源目录：
    echo %SOURCE%
    pause
    exit /b 1
)

REM ------------------------------------------------------------
REM 清理临时目录
REM ------------------------------------------------------------

if exist "%WORK%\" (
    echo [INFO] 清理旧的临时目录...
    rmdir /s /q "%WORK%"
)

mkdir "%WORK%"

REM ------------------------------------------------------------
REM Clone GitHub 仓库
REM ------------------------------------------------------------

echo.
echo [1/6] Clone GitHub repository...
echo.

git clone "%REPO%" "%WORK%"
if errorlevel 1 (
    echo.
    echo [ERROR] Git clone 失败。
    echo 请检查网络、GitHub 登录状态或仓库权限。
    pause
    exit /b 1
)

REM ------------------------------------------------------------
REM 检查当前分支
REM ------------------------------------------------------------

cd /d "%WORK%"

echo.
echo [2/6] 检查远程仓库...
echo.

git status
if errorlevel 1 (
    echo [ERROR] Git repository 状态异常。
    pause
    exit /b 1
)

REM ------------------------------------------------------------
REM 从本地项目复制文件
REM
REM /E       包含子目录
REM /COPY:DAT 保留数据、属性、时间
REM /R:2     失败重试 2 次
REM /W:1     等待 1 秒
REM /XJ      排除 junction
REM
REM 明确排除：
REM task_plan.md
REM readme.md
REM progress.md
REM findings.md
REM build-safe.bat
REM .idea
REM libs
REM
REM 另外排除 .git，避免把本地 Git 仓库套进来。
REM ------------------------------------------------------------

echo.
echo [3/6] Import local project...
echo.

robocopy "%SOURCE%" "%WORK%" /E /COPY:DAT /R:2 /W:1 /XJ ^
    /XD "%SOURCE%\.idea" ^
        "%SOURCE%\libs" ^
        "%SOURCE%\.git" ^
    /XF "%SOURCE%\task_plan.md" ^
        "%SOURCE%\readme.md" ^
        "%SOURCE%\progress.md" ^
        "%SOURCE%\findings.md" ^
        "%SOURCE%\build-safe.bat"

REM ------------------------------------------------------------
REM Robocopy 返回值 0~7 都不算真正失败
REM ------------------------------------------------------------

if errorlevel 8 (
    echo.
    echo [ERROR] Robocopy 导入失败。
    pause
    exit /b 1
)

REM ------------------------------------------------------------
REM 再保险：
REM 删除目标仓库中不允许上传的内容
REM
REM 这样即使远程仓库未来已经存在这些文件，
REM 本次导入也不会把它们带入提交。
REM
REM 注意：这里不会删除远程仓库文件，只是不把这些文件
REM 放进本次 commit。
REM ------------------------------------------------------------

echo.
echo [4/6] 检查排除文件...
echo.

del /f /q "%WORK%\task_plan.md" 2>nul
del /f /q "%WORK%\readme.md" 2>nul
del /f /q "%WORK%\progress.md" 2>nul
del /f /q "%WORK%\findings.md" 2>nul
del /f /q "%WORK%\build-safe.bat" 2>nul

if exist "%WORK%\.idea\" (
    rmdir /s /q "%WORK%\.idea"
)

if exist "%WORK%\libs\" (
    rmdir /s /q "%WORK%\libs"
)

REM ------------------------------------------------------------
REM 查看即将提交的内容
REM ------------------------------------------------------------

echo.
echo ==========================================
echo Files to be committed:
echo ==========================================
echo.

git status --short

echo.
echo ==========================================
echo 排除：
echo   task_plan.md
echo   readme.md
echo   progress.md
echo   findings.md
echo   build-safe.bat
echo   .idea\
echo   libs\
echo ==========================================
echo.

REM ------------------------------------------------------------
REM Git add
REM ------------------------------------------------------------

echo [5/6] Creating commit...

git add -A
if errorlevel 1 (
    echo.
    echo [ERROR] git add 失败。
    pause
    exit /b 1
)

REM ------------------------------------------------------------
REM 检查是否真的有变化
REM ------------------------------------------------------------

git diff --cached --quiet
if not errorlevel 1 (
    echo.
    echo [INFO] 没有新的文件变化。
    echo 不需要创建 commit。
    echo.
    echo 当前仓库状态：
    git status
    pause
    exit /b 0
)

REM ------------------------------------------------------------
REM Commit
REM ------------------------------------------------------------

git commit -m "Import CNPC-Lines 1.20.1Forge"
if errorlevel 1 (
    echo.
    echo [ERROR] git commit 失败。
    echo 如果 Git 提示没有配置 user.name / user.email，
    echo 请先配置 Git 身份。
    pause
    exit /b 1
)

REM ------------------------------------------------------------
REM Push
REM ------------------------------------------------------------

echo.
echo [6/6] Push commit to GitHub...
echo.

git push origin HEAD
if errorlevel 1 (
    echo.
    echo [ERROR] Push 失败。
    echo.
    echo 本地 commit 已经创建，没有丢失。
    echo 可以进入以下目录手动执行：
    echo %WORK%
    echo.
    echo 然后执行：
    echo git push origin HEAD
    echo.
    pause
    exit /b 1
)

echo.
echo ==========================================
echo          IMPORT SUCCESS
echo ==========================================
echo.
echo GitHub:
echo https://github.com/XiaoBiniaoX/CNPC-Lines
echo.
echo Commit:
git log -1 --oneline
echo.
echo 原始开发目录没有被修改。
echo.
echo 临时工作目录：
echo %WORK%
echo.

pause
exit /b 0