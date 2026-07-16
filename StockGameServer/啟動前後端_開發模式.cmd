@echo off
setlocal EnableExtensions
chcp 65001 > nul
title StockGameServer Dev Launcher

set "ROOT=%~dp0"
set "FRONTEND_DIR=%ROOT%frontend"
set "BACKEND_RUNNER=%ROOT%run-backend.cmd"
set "FRONTEND_RUNNER=%ROOT%run-frontend-dev.cmd"

where npm > nul 2>nul
if errorlevel 1 (
  echo [ERROR] npm was not found in PATH.
  exit /b 1
)

if not exist "%FRONTEND_DIR%\package.json" (
  echo [ERROR] Frontend package.json not found: %FRONTEND_DIR%\package.json
  exit /b 1
)

if not exist "%BACKEND_RUNNER%" (
  echo [ERROR] Backend runner script not found: %BACKEND_RUNNER%
  exit /b 1
)

if not exist "%FRONTEND_RUNNER%" (
  echo [ERROR] Frontend runner script not found: %FRONTEND_RUNNER%
  exit /b 1
)

echo [INFO] Starting backend on http://localhost:8080
echo [INFO] Starting frontend dev server on http://localhost:5173

if defined SG_NO_START goto :skip_start

start "StockGameServer Backend" /D "%ROOT%" "%BACKEND_RUNNER%"
start "StockGameServer Frontend" /D "%ROOT%" "%FRONTEND_RUNNER%"

if not defined SG_NO_BROWSER (
  timeout /t 8 /nobreak > nul
  start "" "http://localhost:5173/"
  start "" "http://localhost:5173/#/admin"
)

echo [OK] Dev mode launch commands sent.
exit /b 0

:skip_start
echo [DRYRUN] Backend command:
echo   "%BACKEND_RUNNER%"
echo [DRYRUN] Frontend command:
echo   "%FRONTEND_RUNNER%"
exit /b 0
