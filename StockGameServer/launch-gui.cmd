@echo off
setlocal
title 股市遊戲 伺服器控制台
chcp 65001 > nul

@REM ── 自動偵測 JAVA_HOME ─────────────────────────────────────────────────────
if not "%JAVA_HOME%"=="" goto OkJHome
if exist "C:\Program Files\Java\jdk-17" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-17"
    goto OkJHome
)
echo 找不到 JDK 17，請安裝或設定 JAVA_HOME
pause & exit /B 1

:OkJHome

set "MVN=%~dp0..\apache-maven-3.9.6\bin\mvn.cmd"

echo 編譯中，請稍候...
call "%MVN%" compile -q -f "%~dp0pom.xml"
if ERRORLEVEL 1 (
    echo 編譯失敗
    pause & exit /B 1
)

echo 啟動伺服器控制台 GUI...
call "%MVN%" exec:java ^
    -f "%~dp0pom.xml" ^
    -Dexec.mainClass="com.stockgame.server.gui.ServerDashboard" ^
    -Dexec.classpathScope="runtime" ^
    -Dexec.jvmArgs="-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Dswing.defaultlaf=com.sun.java.swing.plaf.windows.WindowsLookAndFeel"

endlocal
