@echo off
chcp 65001 > nul
title 股市遊戲伺服器

if not "%JAVA_HOME%"=="" goto Ok
if exist "C:\Program Files\Java\jdk-17" ( set "JAVA_HOME=C:\Program Files\Java\jdk-17" )

:Ok
set "MVN=%~dp0..\apache-maven-3.9.6\bin\mvn.cmd"

echo 清除快取並啟動伺服器中，請稍候...
call "%MVN%" clean spring-boot:run -f "%~dp0pom.xml"
pause
