@echo off
setlocal EnableExtensions
chcp 65001 > nul
title StockGameServer Frontend Build And Sync

set "ROOT=%~dp0"
set "FRONTEND_DIR=%ROOT%frontend"

where npm > nul 2>nul
if errorlevel 1 (
  echo [ERROR] npm was not found in PATH.
  exit /b 1
)

if not exist "%FRONTEND_DIR%\package.json" (
  echo [ERROR] Frontend package.json not found: %FRONTEND_DIR%\package.json
  exit /b 1
)

pushd "%FRONTEND_DIR%" > nul

echo [1/2] Building Vue frontend...
call npm run build
if errorlevel 1 (
  popd > nul
  echo [ERROR] Frontend build failed.
  exit /b 1
)

echo [2/2] Syncing dist to Spring Boot static...
call npm run sync:static
if errorlevel 1 (
  popd > nul
  echo [ERROR] Static sync failed.
  exit /b 1
)

popd > nul
echo [OK] Frontend build and sync completed.
exit /b 0
