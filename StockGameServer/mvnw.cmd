@echo off
setlocal

@REM ── 自動偵測 JAVA_HOME ──────────────────────────────────────────────────────
if not "%JAVA_HOME%"=="" goto OkJHome
if exist "C:\Program Files\Java\jdk-17" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-17"
    goto OkJHome
)
echo ERROR: 找不到 JAVA_HOME，請安裝 JDK 17 或手動設定 JAVA_HOME 環境變數
exit /B 1

:OkJHome

@REM ── Maven 路徑（與 StockGameServer 同層）─────────────────────────────────
set "MVN_HOME=%~dp0..\apache-maven-3.9.6"
set "MVN_CMD=%MVN_HOME%\bin\mvn.cmd"

if not exist "%MVN_CMD%" (
    echo ERROR: 找不到 Maven，請確認 %MVN_HOME% 目錄存在
    exit /B 1
)

"%MVN_CMD%" %*
endlocal
