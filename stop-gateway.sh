#!/bin/bash

# Spring Boot Gateway Stop Script for Unix/Linux/macOS

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_NAME="gateway"
PID_FILE="$SCRIPT_DIR/$APP_NAME.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "PID file not found. Gateway may not be running."
    exit 1
fi

PID=$(cat "$PID_FILE")

if ! ps -p "$PID" > /dev/null 2>&1; then
    echo "Gateway process (PID $PID) is not running. Removing stale PID file."
    rm -f "$PID_FILE"
    exit 1
fi

echo "Stopping Gateway (PID $PID)..."

# Try graceful shutdown first
kill -TERM "$PID"

# Wait for graceful shutdown
for i in {1..30}; do
    if ! ps -p "$PID" > /dev/null 2>&1; then
        echo "✅ Gateway stopped gracefully"
        rm -f "$PID_FILE"
        exit 0
    fi
    echo "Waiting for shutdown... ($i/30)"
    sleep 1
done

# Force kill if graceful shutdown failed
echo "Forcing shutdown..."
kill -KILL "$PID" 2>/dev/null || true
rm -f "$PID_FILE"
echo "✅ Gateway stopped (forced)"