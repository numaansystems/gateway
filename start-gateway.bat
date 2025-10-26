@echo off
REM Spring Boot Gateway Startup Script for Windows
REM Usage: start-gateway.bat [profile] [config-dir]
REM Example: start-gateway.bat production C:\gateway\config

setlocal enabledelayedexpansion

REM Script configuration
set SCRIPT_DIR=%~dp0
set APP_NAME=gateway
set JAR_NAME=gateway-0.1.0.jar
set DEFAULT_PROFILE=development
set DEFAULT_CONFIG_DIR=%SCRIPT_DIR%config
set PID_FILE=%SCRIPT_DIR%%APP_NAME%.pid
set LOG_DIR=%SCRIPT_DIR%logs

REM Parse arguments
set PROFILE=%1
if "%PROFILE%"=="" set PROFILE=%DEFAULT_PROFILE%

set CONFIG_DIR=%2
if "%CONFIG_DIR%"=="" set CONFIG_DIR=%DEFAULT_CONFIG_DIR%

REM Set log file based on profile
set LOG_FILE=%LOG_DIR%\%APP_NAME%-%PROFILE%.log

REM Ensure directories exist
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
if not exist "%CONFIG_DIR%" mkdir "%CONFIG_DIR%"

REM Check if jar exists
if not exist "%SCRIPT_DIR%target\%JAR_NAME%" (
    echo ERROR: JAR file not found at %SCRIPT_DIR%target\%JAR_NAME%
    echo Please run 'mvn clean package' first
    exit /b 1
)

REM Check if already running
if exist "%PID_FILE%" (
    set /p PID=<"%PID_FILE%"
    tasklist /FI "PID eq !PID!" 2>nul | find /I "java.exe" >nul
    if !errorlevel! equ 0 (
        echo Gateway is already running with PID !PID!
        exit /b 1
    ) else (
        echo Removing stale PID file
        del "%PID_FILE%"
    )
)

REM Set Java environment
if "%JAVA_HOME%"=="" (
    set JAVA_CMD=java
) else (
    set JAVA_CMD=%JAVA_HOME%\bin\java
)

REM Verify Java exists
%JAVA_CMD% -version >nul 2>&1
if !errorlevel! neq 0 (
    echo ERROR: Java not found. Please set JAVA_HOME or add java to PATH
    exit /b 1
)

REM Verify Java version (should be 17+)
for /f "tokens=3" %%g in ('%JAVA_CMD% -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VERSION_STRING=%%g
)
set JAVA_VERSION_STRING=%JAVA_VERSION_STRING:"=%
for /f "delims=." %%a in ("%JAVA_VERSION_STRING%") do set JAVA_VERSION=%%a

if %JAVA_VERSION% lss 17 (
    echo ERROR: Java 17 or higher is required. Found Java %JAVA_VERSION%
    exit /b 1
)

REM JVM settings
set JVM_OPTS=-Xms512m -Xmx2g
set JVM_OPTS=%JVM_OPTS% -XX:+UseG1GC
set JVM_OPTS=%JVM_OPTS% -XX:+HeapDumpOnOutOfMemoryError
set JVM_OPTS=%JVM_OPTS% -XX:HeapDumpPath=%SCRIPT_DIR%logs\

REM Spring Boot settings
set SPRING_OPTS=--spring.profiles.active=%PROFILE%
set SPRING_OPTS=%SPRING_OPTS% --spring.config.location=classpath:/application.yml,file:%CONFIG_DIR%/
set SPRING_OPTS=%SPRING_OPTS% --logging.file.name=%LOG_FILE%
set SPRING_OPTS=%SPRING_OPTS% --management.endpoints.web.exposure.include=health,info,metrics,prometheus

echo 🚀 Starting Gateway on port 9090 with context path /gateway
echo 📋 Profile: %PROFILE%
echo 📁 Config Directory: %CONFIG_DIR%
echo 📝 Log File: %LOG_FILE%
echo 🌐 Access URL: http://localhost:9090/gateway/actuator/health
%JAVA_CMD% -version 2>&1 | findstr /i "version"

REM Start the application in background
start /B "" %JAVA_CMD% %JVM_OPTS% -jar "%SCRIPT_DIR%target\%JAR_NAME%" %SPRING_OPTS% > "%LOG_FILE%" 2>&1

REM Get the PID (Windows way - approximate)
timeout /t 2 /nobreak >nul
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
echo Gateway started with PID !PID!
echo Logs: type "%LOG_FILE%"
echo Stop: stop-gateway.bat

REM Wait and verify
timeout /t 3 /nobreak >nul
tasklist /FI "PID eq !PID!" 2>nul | find /I "java.exe" >nul
if !errorlevel! equ 0 (
    echo ✅ Gateway is running successfully
) else (
    echo ❌ Gateway failed to start. Check logs: %LOG_FILE%
    exit /b 1
)

endlocal