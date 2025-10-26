@echo off
setlocal enabledelayedexpansion

REM Spring Boot Gateway Management Script
REM Usage: scripts\gateway.bat {start|stop|status|restart|kill} [profile] [config_dir]

REM Script configuration
set SCRIPT_DIR=%~dp0
REM Remove trailing backslash if present
if "%SCRIPT_DIR:~-1%"=="\" set SCRIPT_DIR=%SCRIPT_DIR:~0,-1%
REM Get parent directory
for %%I in ("%SCRIPT_DIR%") do set APP_ROOT=%%~dpI
REM Remove trailing backslash from APP_ROOT
if "%APP_ROOT:~-1%"=="\" set APP_ROOT=%APP_ROOT:~0,-1%
set APP_NAME=gateway
set JAR_NAME=gateway-0.1.0.jar
set DEFAULT_PROFILE=development
set DEFAULT_CONFIG_DIR=%APP_ROOT%config
set PID_FILE=%APP_ROOT%%APP_NAME%.pid
set LOG_DIR=%APP_ROOT%logs
set LIBS_DIR=%APP_ROOT%libs

REM Parse command
set COMMAND=%1
set PROFILE=%2
set CONFIG_DIR=%3

if "%COMMAND%"=="" goto show_usage
if "%PROFILE%"=="" set PROFILE=%DEFAULT_PROFILE%
if "%CONFIG_DIR%"=="" set CONFIG_DIR=%DEFAULT_CONFIG_DIR%

set LOG_FILE=%LOG_DIR%\%APP_NAME%-%PROFILE%.log

REM Ensure directories exist
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
if not exist "%CONFIG_DIR%" mkdir "%CONFIG_DIR%"
if not exist "%LIBS_DIR%" mkdir "%LIBS_DIR%"

goto %COMMAND% 2>nul || goto show_usage

:start
call :check_if_running
if !errorlevel! equ 0 (
    echo ⚠️  Gateway is already running with PID !PID!
    exit /b 1
)

call :check_java
if !errorlevel! neq 0 exit /b 1

call :check_jar
if !errorlevel! neq 0 exit /b 1

REM Clean up stale PID file
if exist "%PID_FILE%" (
    echo ℹ️  Removing stale PID file
    del "%PID_FILE%"
)

echo 🚀 Starting Gateway on port 9090 with /gateway prefix routes
echo 📋 Profile: %PROFILE%
echo 📁 Config Directory: %CONFIG_DIR%
echo 📝 Log File: %LOG_FILE%
echo 🌐 Health URL: http://localhost:9090/gateway/actuator/health
echo 🌐 Direct Health: http://localhost:9090/actuator/health
!JAVA_CMD! -version 2>&1 | findstr /i "version"

REM JVM settings
set JVM_OPTS=-Xms512m -Xmx2g
set JVM_OPTS=%JVM_OPTS% -XX:+UseG1GC
set JVM_OPTS=%JVM_OPTS% -XX:+HeapDumpOnOutOfMemoryError
set JVM_OPTS=%JVM_OPTS% -XX:HeapDumpPath=%LOG_DIR%\

REM Spring Boot settings
set SPRING_OPTS=--spring.profiles.active=%PROFILE%
set SPRING_OPTS=%SPRING_OPTS% --spring.config.location=classpath:/application.yml,file:%CONFIG_DIR%/
set SPRING_OPTS=%SPRING_OPTS% --logging.file.name=%LOG_FILE%
set SPRING_OPTS=%SPRING_OPTS% --management.endpoints.web.exposure.include=health,info,metrics,prometheus,gateway

REM Start the application in background
start /B "" !JAVA_CMD! %JVM_OPTS% -jar "%LIBS_DIR%\%JAR_NAME%" %SPRING_OPTS% > "%LOG_FILE%" 2>&1

REM Get the PID (Windows way - approximate)
timeout /t 3 /nobreak >nul
for /f "tokens=2" %%i in ('tasklist /FI "IMAGENAME eq java.exe" /FI "COMMANDLINE eq *%JAR_NAME%*" /FO CSV ^| find /v "PID"') do (
    set PID=%%i
    set PID=!PID:"=!
)

if "!PID!"=="" (
    echo ❌ Failed to start Gateway or get PID
    exit /b 1
)

REM Save PID
echo !PID! > "%PID_FILE%"

REM Wait and verify
timeout /t 3 /nobreak >nul
tasklist /FI "PID eq !PID!" 2>nul | find /I "java.exe" >nul
if !errorlevel! equ 0 (
    echo ✅ Gateway started with PID !PID!
    echo 📋 Logs: type "%LOG_FILE%"
    echo 🛑 Stop: %0 stop
) else (
    echo ❌ Gateway failed to start. Check logs: %LOG_FILE%
    del "%PID_FILE%" 2>nul
    exit /b 1
)
goto :eof

:stop
call :get_pid
if "!PID!"=="" (
    echo ⚠️  Gateway is not running ^(no PID file^)
    goto :eof
)

tasklist /FI "PID eq !PID!" 2>nul | find /I "java.exe" >nul
if !errorlevel! neq 0 (
    echo ⚠️  Gateway process ^(PID !PID!^) is not running. Removing stale PID file.
    del "%PID_FILE%" 2>nul
    goto :eof
)

echo ℹ️  Stopping Gateway ^(PID !PID!^)...
taskkill /PID !PID! >nul 2>&1

REM Wait for graceful shutdown
set /a count=0
:wait_loop
if !count! geq 30 goto force_kill
set /a count+=1
echo ℹ️  Waiting for shutdown... ^(!count!/30^)

timeout /t 1 /nobreak >nul
tasklist /FI "PID eq !PID!" 2>nul | find /I "java.exe" >nul
if !errorlevel! neq 0 (
    echo ✅ Gateway stopped gracefully
    del "%PID_FILE%" 2>nul
    goto :eof
)
goto wait_loop

:force_kill
echo ❌ Gateway did not stop gracefully within 30 seconds
exit /b 1

:kill
call :get_pid
if "!PID!"=="" (
    echo ⚠️  Gateway is not running ^(no PID file^)
    goto :eof
)

tasklist /FI "PID eq !PID!" 2>nul | find /I "java.exe" >nul
if !errorlevel! neq 0 (
    echo ⚠️  Gateway process ^(PID !PID!^) is not running. Removing stale PID file.
    del "%PID_FILE%" 2>nul
    goto :eof
)

echo ⚠️  Force killing Gateway ^(PID !PID!^)...
taskkill /F /PID !PID! >nul 2>&1
timeout /t 2 /nobreak >nul

tasklist /FI "PID eq !PID!" 2>nul | find /I "java.exe" >nul
if !errorlevel! neq 0 (
    echo ✅ Gateway killed
    del "%PID_FILE%" 2>nul
) else (
    echo ❌ Failed to kill Gateway process
    exit /b 1
)
goto :eof

:status
call :get_pid
if "!PID!"=="" (
    echo ℹ️  Gateway Status: NOT RUNNING ^(no PID file^)
    exit /b 1
)

tasklist /FI "PID eq !PID!" 2>nul | find /I "java.exe" >nul
if !errorlevel! equ 0 (
    echo ✅ Gateway Status: RUNNING ^(PID !PID!^)
    
    REM Show process info
    tasklist /FI "PID eq !PID!" 2>nul
    
    REM Test health endpoint
    curl -s -f http://localhost:9090/actuator/health >nul 2>&1
    if !errorlevel! equ 0 (
        echo ✅ Health Status: Available
    ) else (
        echo ⚠️  Health endpoint not responding
    )
) else (
    echo ❌ Gateway Status: NOT RUNNING ^(stale PID file^)
    del "%PID_FILE%" 2>nul
    exit /b 1
)
goto :eof

:restart
echo ℹ️  Restarting Gateway...
call :check_if_running
if !errorlevel! equ 0 (
    call :stop
    if !errorlevel! neq 0 (
        echo ❌ Failed to stop Gateway
        exit /b 1
    )
)
timeout /t 2 /nobreak >nul
goto start

:check_if_running
call :get_pid
if "!PID!"=="" exit /b 1
tasklist /FI "PID eq !PID!" 2>nul | find /I "java.exe" >nul
exit /b !errorlevel!

:get_pid
set PID=
if exist "%PID_FILE%" (
    set /p PID=<"%PID_FILE%"
)
exit /b 0

:check_java
if "%JAVA_HOME%"=="" (
    set JAVA_CMD=java
) else (
    set JAVA_CMD=%JAVA_HOME%\bin\java
)

!JAVA_CMD! -version >nul 2>&1
if !errorlevel! neq 0 (
    echo ❌ Java not found. Please set JAVA_HOME or add java to PATH
    exit /b 1
)

REM Check Java version
for /f "tokens=3" %%g in ('!JAVA_CMD! -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VERSION_STRING=%%g
)
set JAVA_VERSION_STRING=!JAVA_VERSION_STRING:"=!
for /f "delims=." %%a in ("!JAVA_VERSION_STRING!") do set JAVA_VERSION=%%a

if !JAVA_VERSION! lss 17 (
    echo ❌ Java 17 or higher is required. Found Java !JAVA_VERSION!
    exit /b 1
)
exit /b 0

:check_jar
set JAR_PATH=%LIBS_DIR%\%JAR_NAME%
if not exist "%JAR_PATH%" (
    if exist "%APP_ROOT%target\%JAR_NAME%" (
        echo ℹ️  Moving JAR from target to libs directory
        copy "%APP_ROOT%target\%JAR_NAME%" "%LIBS_DIR%\" >nul
    ) else (
        echo ❌ JAR file not found at %JAR_PATH%
        echo ❌ Please run 'mvn clean package' first
        exit /b 1
    )
)
exit /b 0

:show_usage
echo Usage: %0 {start^|stop^|status^|restart^|kill} [profile] [config_dir]
echo.
echo Commands:
echo   start     - Start the gateway service
echo   stop      - Gracefully stop the gateway service
echo   status    - Show gateway service status
echo   restart   - Restart the gateway service
echo   kill      - Force kill the gateway service
echo.
echo Parameters:
echo   profile     - Spring profile ^(default: development^)
echo   config_dir  - Configuration directory ^(default: %DEFAULT_CONFIG_DIR%^)
echo.
echo Examples:
echo   %0 start
echo   %0 start production
echo   %0 start development C:\custom\config
echo   %0 status
echo   %0 stop
exit /b 1

endlocal