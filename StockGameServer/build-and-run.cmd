@echo off
chcp 65001 > nul
title 股市遊戲 - 編譯與啟動

echo ================================================
echo   股市遊戲伺服器  Build ^& Run
echo ================================================
echo.

@REM ── 自動偵測 JAVA_HOME ────────────────────────────────────────────────────
if not "%JAVA_HOME%"=="" goto OkJHome
if exist "C:\Program Files\Java\jdk-17" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-17"
    goto OkJHome
)
echo [錯誤] 找不到 JDK 17，請安裝或設定 JAVA_HOME 環境變數
pause & exit /B 1

:OkJHome
echo [OK] JAVA_HOME = %JAVA_HOME%

set "MVN=%~dp0..\apache-maven-3.9.6\bin\mvn.cmd"
set "PROJECT=%~dp0"

if not exist "%MVN%" (
    echo [錯誤] 找不到 Maven: %MVN%
    pause & exit /B 1
)
echo [OK] Maven = %MVN%
echo.

@REM ── 選擇模式 ──────────────────────────────────────────────────────────────
echo 請選擇操作：
echo   [1] 編譯 + 啟動伺服器  (spring-boot:run)
echo   [2] 僅編譯              (compile)
echo   [3] 清除 + 重新編譯     (clean compile)
echo   [4] 清除 + 啟動伺服器  (clean spring-boot:run)  ← 遇到錯誤時用這個
echo   [5] 啟動 GUI 控制台     (ServerDashboard)
echo   [6] 離開
echo.
set /p CHOICE=請輸入數字 (預設=1): 
if "%CHOICE%"=="" set CHOICE=1

if "%CHOICE%"=="1" goto RunServer
if "%CHOICE%"=="2" goto CompileOnly
if "%CHOICE%"=="3" goto CleanCompile
if "%CHOICE%"=="4" goto CleanRun
if "%CHOICE%"=="5" goto RunGui
if "%CHOICE%"=="6" exit /B 0

echo [錯誤] 無效選項
pause & exit /B 1

@REM ── 1. 編譯 + 啟動 ────────────────────────────────────────────────────────
:RunServer
echo.
echo [步驟 1/2] 增量編譯...
call "%MVN%" compile -q -f "%PROJECT%pom.xml"
if ERRORLEVEL 1 (
    echo.
    echo [失敗] 編譯發生錯誤，嘗試清除後重新編譯...
    call "%MVN%" clean compile -q -f "%PROJECT%pom.xml"
    if ERRORLEVEL 1 ( echo [失敗] 重新編譯也失敗，請查看上方錯誤訊息 & pause & exit /B 1 )
)
echo [OK] 編譯成功
echo.
echo [步驟 2/2] 啟動 Spring Boot 伺服器 (port 8080)...
echo [提示] 按 Ctrl+C 可停止伺服器
echo.
call "%MVN%" spring-boot:run -f "%PROJECT%pom.xml"
goto End

@REM ── 2. 僅編譯 ────────────────────────────────────────────────────────────
:CompileOnly
echo.
echo [執行] 增量編譯...
call "%MVN%" compile -f "%PROJECT%pom.xml"
if ERRORLEVEL 1 ( echo [失敗] 請查看上方錯誤 & pause & exit /B 1 )
echo.
echo [OK] 編譯成功！class 檔案在 target\classes\
pause
goto End

@REM ── 3. 清除 + 重新編譯 ────────────────────────────────────────────────────
:CleanCompile
echo.
echo [執行] 清除 target 目錄...
call "%MVN%" clean -q -f "%PROJECT%pom.xml"
echo [執行] 重新編譯...
call "%MVN%" compile -f "%PROJECT%pom.xml"
if ERRORLEVEL 1 ( echo [失敗] 請查看上方錯誤 & pause & exit /B 1 )
echo.
echo [OK] 編譯成功！
pause
goto End

@REM ── 4. 清除 + 啟動 ────────────────────────────────────────────────────────
:CleanRun
echo.
echo [步驟 1/2] 清除舊的編譯快取...
call "%MVN%" clean -q -f "%PROJECT%pom.xml"
echo [OK] 清除完成
echo.
echo [步驟 2/2] 重新編譯並啟動伺服器...
echo [提示] 按 Ctrl+C 可停止伺服器
echo.
call "%MVN%" spring-boot:run -f "%PROJECT%pom.xml"
goto End

@REM ── 5. GUI 控制台 ─────────────────────────────────────────────────────────
:RunGui
echo.
echo [步驟 1/2] 編譯...
call "%MVN%" compile -q -f "%PROJECT%pom.xml"
if ERRORLEVEL 1 (
    call "%MVN%" clean compile -q -f "%PROJECT%pom.xml"
    if ERRORLEVEL 1 ( echo [失敗] 編譯失敗 & pause & exit /B 1 )
)
echo [OK] 編譯成功
echo.
echo [步驟 2/2] 啟動 GUI 控制台...
call "%MVN%" exec:java ^
    -f "%PROJECT%pom.xml" ^
    -Dexec.mainClass="com.stockgame.server.gui.ServerDashboard" ^
    -Dexec.classpathScope="runtime" ^
    "-Dexec.jvmArgs=-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8"
goto End

:End
echo.
echo ================================================
echo   完成
echo ================================================
