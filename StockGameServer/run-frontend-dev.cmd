@echo off
setlocal EnableExtensions
chcp 65001 > nul
title StockGameServer Frontend

set "FRONTEND_DIR=%~dp0frontend"

where npm > nul 2>nul
if errorlevel 1 (
  echo [ERROR] npm was not found in PATH.
  pause
  exit /b 1
)

if not exist "%FRONTEND_DIR%\package.json" (
  echo [ERROR] Frontend package.json not found:
  echo   %FRONTEND_DIR%\package.json
  pause
  exit /b 1
)

cd /d "%FRONTEND_DIR%"

echo [INFO] Starting Vite dev server from:
echo   %CD%
echo [INFO] URL: http://localhost:5173/

call npm run dev
set "EXIT_CODE=%ERRORLEVEL%"

echo.
echo [INFO] Frontend process exited with code %EXIT_CODE%.
pause
exit /b %EXIT_CODE%
