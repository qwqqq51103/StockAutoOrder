@echo off
chcp 65001 > nul
title 股市遊戲 GUI 控制台

if not "%JAVA_HOME%"=="" goto Ok
if exist "C:\Program Files\Java\jdk-17" ( set "JAVA_HOME=C:\Program Files\Java\jdk-17" )

:Ok
set "MVN=%~dp0..\apache-maven-3.9.6\bin\mvn.cmd"

echo 編譯並啟動 GUI 控制台...
call "%MVN%" clean compile -q -f "%~dp0pom.xml"
call "%MVN%" exec:java -f "%~dp0pom.xml" ^
    -Dexec.mainClass="com.stockgame.server.gui.ServerDashboard" ^
    -Dexec.classpathScope="runtime" ^
    "-Dexec.jvmArgs=-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8"
pause
