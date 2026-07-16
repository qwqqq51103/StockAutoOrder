@echo off
setlocal EnableExtensions
chcp 65001 > nul
title StockGameServer Backend

cd /d "%~dp0"

where mvn > nul 2>nul
if errorlevel 1 (
  echo [ERROR] Maven was not found in PATH.
  echo [ERROR] Please install Maven or add mvn to PATH.
  pause
  exit /b 1
)

echo [INFO] Starting Spring Boot backend from:
echo   %CD%
echo [INFO] URL: http://localhost:8080/

call mvn spring-boot:run
set "EXIT_CODE=%ERRORLEVEL%"

echo.
echo [INFO] Backend process exited with code %EXIT_CODE%.
pause
exit /b %EXIT_CODE%
