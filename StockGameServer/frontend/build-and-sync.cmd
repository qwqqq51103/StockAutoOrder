@echo off
setlocal

call npm run build
if errorlevel 1 exit /b %errorlevel%

node .\scripts\sync-static.mjs
exit /b %errorlevel%
