@echo off
REM Spring Boot Gateway Stop Script for Windows

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set APP_NAME=gateway
set PID_FILE=%SCRIPT_DIR%%APP_NAME%.pid

if not exist "%PID_FILE%" (
    echo PID file not found. Gateway may not be running.
    exit /b 1
)

set /p PID=<"%PID_FILE%"

tasklist /FI "PID eq %PID%" 2>nul | find /I "java.exe" >nul
if !errorlevel! neq 0 (
    echo Gateway process ^(PID %PID%^) is not running. Removing stale PID file.
    del "%PID_FILE%"
    exit /b 1
)

echo Stopping Gateway ^(PID %PID%^)...

REM Try graceful shutdown first (send CTRL+C equivalent)
taskkill /PID %PID% >nul 2>&1

REM Wait for graceful shutdown
set /a counter=0
:wait_loop
if !counter! geq 30 goto force_kill
tasklist /FI "PID eq %PID%" 2>nul | find /I "java.exe" >nul
if !errorlevel! neq 0 (
    echo ✅ Gateway stopped gracefully
    del "%PID_FILE%"
    goto end
)
echo Waiting for shutdown... ^(!counter!/30^)
timeout /t 1 /nobreak >nul
set /a counter+=1
goto wait_loop

:force_kill
echo Forcing shutdown...
taskkill /F /PID %PID% >nul 2>&1
del "%PID_FILE%"
echo ✅ Gateway stopped ^(forced^)

:end
endlocal