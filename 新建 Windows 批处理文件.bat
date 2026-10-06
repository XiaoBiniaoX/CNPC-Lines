@echo off
setlocal EnableExtensions

title CNPC-Lines GitHub Auto Upload

REM ============================================================
REM CNPC-Lines 1.20.1Forge GitHub 自动导入 / 更新
REM ============================================================

set "SOURCE=C:\Users\ASUS\Desktop\Mod开发\CNPC lines\1.20.1Forge"
set "REPO=https://github.com/XiaoBiniaoX/CNPC-Lines.git"
set "WORK=%TEMP%\CNPC-Lines-GitImport"

echo.
echo ============================================================
echo             CNPC-Lines GitHub Auto Upload
echo ============================================================
echo.
echo 本地项目：
echo %SOURCE%
echo.
echo GitHub：
echo %REPO%
echo.
echo ============================================================
echo.

REM ============================================================
REM 1. 检查 Git
REM ============================================================

where git >nul 2>nul

if errorlevel 1 (
    echo [ERROR] 找不到 Git。
    echo 请确认 Git 已安装并加入 PATH。
    echo.
    pause
    exit /b 1
)

REM ============================================================
REM 2. 检查本地项目
REM ============================================================

if not exist "%SOURCE%\" (
    echo [ERROR] 找不到本地项目目录：
    echo %SOURCE%
    echo.
    pause
    exit /b 1
)

REM ============================================================
REM 3. 清理临时工作目录
REM ============================================================

if exist "%WORK%\" (
    echo [INFO] 清理旧的临时工作目录...
    rmdir /s /q "%WORK%"
)

mkdir "%WORK%"

if errorlevel 1 (
    echo [ERROR] 无法创建临时工作目录：
    echo %WORK%
    pause
    exit /b 1
)

REM ============================================================
REM 4. 获取 GitHub 当前最新仓库
REM ============================================================

echo.
echo [1/6] Clone GitHub repository...
echo.

git clone "%REPO%" "%WORK%"

if errorlevel 1 (
    echo.
    echo [ERROR] Git clone 失败。
    echo 请检查：
    echo   - 网络连接
    echo   - GitHub 登录/认证
    echo   - 仓库权限
    echo.
    pause
    exit /b 1
)

cd /d "%WORK%"

REM ============================================================
REM 5. 导入本地项目
REM
REM 排除：
REM   task_plan.md
REM   readme.md
REM   progress.md
REM   findings.md
REM   build-safe.bat
REM
REM   .idea
REM   libs
REM   build
REM   .gradle
REM   .git
REM ============================================================

echo.
echo [2/6] Import local project...
echo.

robocopy "%SOURCE%" "%WORK%" /E /COPY:DAT /R:2 /W:1 /XJ ^
    /XD "%SOURCE%\.idea" ^
        "%SOURCE%\libs" ^
        "%SOURCE%\build" ^
        "%SOURCE%\.gradle" ^
        "%SOURCE%\.git" ^
    /XF "%SOURCE%\task_plan.md" ^
        "%SOURCE%\readme.md" ^
        "%SOURCE%\progress.md" ^
        "%SOURCE%\findings.md" ^
        "%SOURCE%\build-safe.bat"

REM Robocopy：
REM 0~7 = 正常
REM 8+  = 失败

if errorlevel 8 (
    echo.
    echo [ERROR] 文件导入失败。
    pause
    exit /b 1
)

REM ============================================================
REM 6. 再次清理排除项
REM
REM 防止这些文件原本就存在于 GitHub 仓库中，
REM 被误加入本次提交。
REM ============================================================

echo.
echo [3/6] Cleaning excluded files...
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

if exist "%WORK%\build\" (
    rmdir /s /q "%WORK%\build"
)

if exist "%WORK%\.gradle\" (
    rmdir /s /q "%WORK%\.gradle"
)

REM ============================================================
REM 7. 显示变更
REM ============================================================

echo.
echo [4/6] Checking changes...
echo.
echo ------------------------------------------------------------
git status --short
echo ------------------------------------------------------------
echo.

REM ============================================================
REM 8. 加入 Git 暂存区
REM ============================================================

git add -A

if errorlevel 1 (
    echo.
    echo [ERROR] git add 失败。
    pause
    exit /b 1
)

REM ============================================================
REM 9. 检查是否有实际变化
REM ============================================================

git diff --cached --quiet

if not errorlevel 1 (
    echo.
    echo ============================================================
    echo 没有检测到新的文件变化。
    echo 不创建空 Commit。
    echo ============================================================
    echo.
    git status
    echo.
    pause
    exit /b 0
)

REM ============================================================
REM 10. 创建 Commit
REM ============================================================

echo.
echo [5/6] Creating Git commit...
echo.

git commit -m "Update CNPC-Lines 1.20.1Forge"

if errorlevel 1 (
    echo.
    echo [ERROR] git commit 失败。
    echo.
    echo 如果这是第一次使用 Git，
    echo 可能需要设置 user.name 和 user.email。
    echo.
    pause
    exit /b 1
)

REM ============================================================
REM 11. 推送到 GitHub
REM
REM 注意：
REM 不使用 --force
REM 不覆盖 Git 历史
REM ============================================================

echo.
echo [6/6] Pushing to GitHub...
echo.

git push origin HEAD

if errorlevel 1 (
    echo.
    echo ============================================================
    echo [ERROR] GitHub Push 失败。
    echo ============================================================
    echo.
    echo 本地 Commit 已经创建，不会丢失。
    echo.
    echo 临时 Git 仓库：
    echo %WORK%
    echo.
    echo 可以进入该目录后手动执行：
    echo git push origin HEAD
    echo.
    pause
    exit /b 1
)

REM ============================================================
REM 12. 完成
REM ============================================================

echo.
echo ============================================================
echo                 UPLOAD SUCCESS
echo ============================================================
echo.
echo GitHub：
echo https://github.com/XiaoBiniaoX/CNPC-Lines
echo.
echo 最新 Commit：
git log -1 --oneline
echo.
echo ============================================================
echo.
echo 原始开发目录没有被修改。
echo.
echo 被排除的内容：
echo   task_plan.md
echo   readme.md
echo   progress.md
echo   findings.md
echo   build-safe.bat
echo   .idea\
echo   libs\
echo   build\
echo   .gradle\
echo   .git\
echo.
echo ============================================================
echo.

pause
exit /b 0