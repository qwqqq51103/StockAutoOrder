@echo off
setlocal EnableExtensions
chcp 65001 > nul
title StockGameServer Prod Launcher

set "ROOT=%~dp0"
set "HELPER=%ROOT%build-frontend-and-sync.cmd"
set "BACKEND_RUNNER=%ROOT%run-backend.cmd"

if not exist "%HELPER%" (
  echo [ERROR] Helper script not found: %HELPER%
  exit /b 1
)

if not exist "%BACKEND_RUNNER%" (
  echo [ERROR] Backend runner script not found: %BACKEND_RUNNER%
  exit /b 1
)

call "%HELPER%"
if errorlevel 1 exit /b 1

echo [INFO] Starting backend with built frontend on http://localhost:8080

if defined SG_NO_START goto :skip_start

call :check_backend_ready
if not errorlevel 1 (
  echo [INFO] Backend is already running on http://localhost:8080/
  goto :open_browser
)

start "StockGameServer Backend" /D "%ROOT%" "%BACKEND_RUNNER%"

if defined SG_NO_BROWSER goto :launch_done

call :wait_for_backend
if errorlevel 1 exit /b 1

:open_browser
if defined SG_NO_BROWSER goto :launch_done
start "" "http://localhost:8080/"
start "" "http://localhost:8080/#/admin"

:launch_done
echo [OK] Prod mode launch command sent.
exit /b 0

:skip_start
echo [DRYRUN] Backend command:
echo   "%BACKEND_RUNNER%"
exit /b 0

:check_backend_ready
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r=Invoke-WebRequest -UseBasicParsing -TimeoutSec 1 'http://localhost:8080/'; if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { exit 0 } } catch { }; exit 1"
exit /b %errorlevel%

:wait_for_backend
echo [INFO] Waiting for backend readiness: http://localhost:8080/
powershell -NoProfile -ExecutionPolicy Bypass -Command "$deadline=(Get-Date).AddSeconds(120); do { try { $r=Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 'http://localhost:8080/'; if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { exit 0 } } catch { }; Start-Sleep -Seconds 2 } while ((Get-Date) -lt $deadline); exit 1"
if errorlevel 1 (
  echo [ERROR] Backend did not become ready within 120 seconds.
  echo [ERROR] Check the StockGameServer Backend window for Spring Boot errors.
  exit /b 1
)
echo [OK] Backend is ready.
exit /b 0
