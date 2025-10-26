#!/bin/bash

# Spring Boot Gateway Startup Script for Unix/Linux/macOS
# Usage: ./start-gateway.sh [profile] [config-dir]
# Example: ./start-gateway.sh production /opt/gateway/config

set -e

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_NAME="gateway"
JAR_NAME="gateway-0.1.0.jar"
DEFAULT_PROFILE="development"
DEFAULT_CONFIG_DIR="$SCRIPT_DIR/config"
PID_FILE="$SCRIPT_DIR/$APP_NAME.pid"
LOG_DIR="$SCRIPT_DIR/logs"

# Parse arguments
PROFILE=${1:-$DEFAULT_PROFILE}
CONFIG_DIR=${2:-$DEFAULT_CONFIG_DIR}

# Set log file based on profile
LOG_FILE="$LOG_DIR/$APP_NAME-$PROFILE.log"

# Ensure directories exist
mkdir -p "$LOG_DIR"
mkdir -p "$CONFIG_DIR"

# Check if jar exists
if [ ! -f "$SCRIPT_DIR/target/$JAR_NAME" ]; then
    echo "ERROR: JAR file not found at $SCRIPT_DIR/target/$JAR_NAME"
    echo "Please run 'mvn clean package' first"
    exit 1
fi

# Check if already running
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "Gateway is already running with PID $PID"
        exit 1
    else
        echo "Removing stale PID file"
        rm -f "$PID_FILE"
    fi
fi

# Set Java environment
if [ -z "$JAVA_HOME" ]; then
    # Try to find Java 17+
    if command -v java >/dev/null 2>&1; then
        JAVA_CMD="java"
    else
        echo "ERROR: Java not found. Please set JAVA_HOME or add java to PATH"
        exit 1
    fi
else
    JAVA_CMD="$JAVA_HOME/bin/java"
fi

# Verify Java version (should be 17+)
JAVA_VERSION=$($JAVA_CMD -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "ERROR: Java 17 or higher is required. Found Java $JAVA_VERSION"
    exit 1
fi

# JVM settings
JVM_OPTS="-Xms512m -Xmx2g"
JVM_OPTS="$JVM_OPTS -XX:+UseG1GC"
JVM_OPTS="$JVM_OPTS -XX:+HeapDumpOnOutOfMemoryError"
JVM_OPTS="$JVM_OPTS -XX:HeapDumpPath=$SCRIPT_DIR/logs/"

# Spring Boot settings
SPRING_OPTS="--spring.profiles.active=$PROFILE"
SPRING_OPTS="$SPRING_OPTS --spring.config.location=classpath:/application.yml,file:$CONFIG_DIR/"
SPRING_OPTS="$SPRING_OPTS --logging.file.name=$LOG_FILE"

# Security settings (disable if not needed in production)
SPRING_OPTS="$SPRING_OPTS --management.endpoints.web.exposure.include=health,info,metrics,prometheus"

echo "Starting Spring Boot Gateway..."
echo "Profile: $PROFILE"
echo "Config Directory: $CONFIG_DIR"
echo "Log File: $LOG_FILE"
echo "Java Version: $($JAVA_CMD -version 2>&1 | head -n 1)"

# Start the application
nohup $JAVA_CMD $JVM_OPTS \
    -jar "$SCRIPT_DIR/target/$JAR_NAME" \
    $SPRING_OPTS \
    > "$LOG_FILE" 2>&1 &

# Save PID
echo $! > "$PID_FILE"
PID=$(cat "$PID_FILE")

echo "Gateway started with PID $PID"
echo "Logs: tail -f $LOG_FILE"
echo "Stop: ./stop-gateway.sh"

# Wait a moment and check if it's still running
sleep 3
if ps -p "$PID" > /dev/null 2>&1; then
    echo "✅ Gateway is running successfully"
else
    echo "❌ Gateway failed to start. Check logs: $LOG_FILE"
    exit 1
fi