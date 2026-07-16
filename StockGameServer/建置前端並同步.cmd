@echo off
setlocal EnableExtensions
chcp 65001 > nul
call "%~dp0build-frontend-and-sync.cmd"
exit /b %errorlevel%
