#!/bin/bash

# Spring Boot Gateway Management Script
# Usage: ./scripts/gateway.sh {start|stop|status|restart|kill} [profile] [config_dir]

set -euo pipefail

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_ROOT="$(dirname "$SCRIPT_DIR")"
APP_NAME="gateway"
JAR_NAME="gateway-0.1.0.jar"
DEFAULT_PROFILE="development"
DEFAULT_CONFIG_DIR="$APP_ROOT/config"
PID_FILE="$APP_ROOT/$APP_NAME.pid"
LOG_DIR="$APP_ROOT/logs"
LIBS_DIR="$APP_ROOT/libs"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

show_usage() {
    echo "Usage: $0 {start|stop|status|restart|kill} [profile] [config_dir]"
    echo ""
    echo "Commands:"
    echo "  start     - Start the gateway service"
    echo "  stop      - Gracefully stop the gateway service"
    echo "  status    - Show gateway service status"
    echo "  restart   - Restart the gateway service"
    echo "  kill      - Force kill the gateway service"
    echo ""
    echo "Parameters:"
    echo "  profile     - Spring profile (default: development)"
    echo "  config_dir  - Configuration directory (default: $DEFAULT_CONFIG_DIR)"
    echo ""
    echo "Examples:"
    echo "  $0 start"
    echo "  $0 start production"
    echo "  $0 start development /custom/config"
    echo "  $0 status"
    echo "  $0 stop"
}

get_pid() {
    if [[ -f "$PID_FILE" ]]; then
        cat "$PID_FILE"
    else
        echo ""
    fi
}

is_running() {
    local pid=$(get_pid)
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
        return 0
    else
        return 1
    fi
}

wait_for_shutdown() {
    local max_wait=30
    local count=0
    
    while is_running && [[ $count -lt $max_wait ]]; do
        count=$((count + 1))
        log_info "Waiting for shutdown... ($count/$max_wait)"
        sleep 1
    done
    
    if is_running; then
        return 1
    else
        return 0
    fi
}

check_java() {
    if [[ -n "${JAVA_HOME:-}" ]]; then
        JAVA_CMD="$JAVA_HOME/bin/java"
    else
        JAVA_CMD="java"
    fi
    
    if ! command -v "$JAVA_CMD" &> /dev/null; then
        log_error "Java not found. Please set JAVA_HOME or add java to PATH"
        exit 1
    fi
    
    # Check Java version
    local java_version
    java_version=$("$JAVA_CMD" -version 2>&1 | grep -E "(openjdk|java) version" | cut -d'"' -f2 | cut -d'.' -f1)
    
    if [[ "$java_version" -lt 17 ]]; then
        log_error "Java 17 or higher is required. Found Java $java_version"
        exit 1
    fi
    
    log_info "Using Java: $("$JAVA_CMD" -version 2>&1 | head -1)"
}

check_jar() {
    local jar_path="$LIBS_DIR/$JAR_NAME"
    if [[ ! -f "$jar_path" ]]; then
        # Try to find in target directory (for development)
        if [[ -f "$APP_ROOT/target/$JAR_NAME" ]]; then
            log_info "Moving JAR from target to libs directory"
            cp "$APP_ROOT/target/$JAR_NAME" "$LIBS_DIR/"
        else
            log_error "JAR file not found at $jar_path"
            log_error "Please run 'mvn clean package' first"
            exit 1
        fi
    fi
}

start_gateway() {
    local profile="${1:-$DEFAULT_PROFILE}"
    local config_dir="${2:-$DEFAULT_CONFIG_DIR}"
    local log_file="$LOG_DIR/$APP_NAME-$profile.log"
    
    # Ensure directories exist
    mkdir -p "$LOG_DIR" "$config_dir"
    
    # Check if already running
    if is_running; then
        local pid=$(get_pid)
        log_warning "Gateway is already running with PID $pid"
        return 1
    fi
    
    # Clean up stale PID file
    if [[ -f "$PID_FILE" ]]; then
        log_info "Removing stale PID file"
        rm -f "$PID_FILE"
    fi
    
    # Validate environment
    check_java
    check_jar
    
    log_info "🚀 Starting Gateway on port 9090 with /gateway prefix routes"
    log_info "📋 Profile: $profile"
    log_info "📁 Config directory: $config_dir"
    log_info "📝 Log file: $log_file"
    log_info "🌐 Health URL: http://localhost:9090/gateway/actuator/health"
    log_info "🌐 Direct Health: http://localhost:9090/actuator/health"
    
    # JVM settings
    local jvm_opts="-Xms512m -Xmx2g"
    jvm_opts="$jvm_opts -XX:+UseG1GC"
    jvm_opts="$jvm_opts -XX:+HeapDumpOnOutOfMemoryError"
    jvm_opts="$jvm_opts -XX:HeapDumpPath=$LOG_DIR/"
    
    # Spring Boot settings
    local spring_opts="--spring.profiles.active=$profile"
    spring_opts="$spring_opts --spring.config.location=classpath:/application.yml,file:$config_dir/"
    spring_opts="$spring_opts --logging.file.name=$log_file"
    spring_opts="$spring_opts --management.endpoints.web.exposure.include=health,info,metrics,prometheus,gateway"
    
    # Start the application
    nohup "$JAVA_CMD" $jvm_opts -jar "$LIBS_DIR/$JAR_NAME" $spring_opts > "$log_file" 2>&1 &
    local pid=$!
    
    # Save PID
    echo "$pid" > "$PID_FILE"
    
    # Wait a moment and verify
    sleep 3
    if is_running; then
        log_success "Gateway started with PID $pid"
        log_info "📋 Logs: tail -f $log_file"
        log_info "🛑 Stop: $0 stop"
        return 0
    else
        log_error "Gateway failed to start. Check logs: $log_file"
        rm -f "$PID_FILE"
        return 1
    fi
}

stop_gateway() {
    local pid=$(get_pid)
    
    if [[ -z "$pid" ]]; then
        log_warning "Gateway is not running (no PID file)"
        return 0
    fi
    
    if ! is_running; then
        log_warning "Gateway process (PID $pid) is not running. Removing stale PID file."
        rm -f "$PID_FILE"
        return 0
    fi
    
    log_info "Stopping Gateway (PID $pid)..."
    kill "$pid"
    
    if wait_for_shutdown; then
        log_success "Gateway stopped gracefully"
        rm -f "$PID_FILE"
        return 0
    else
        log_error "Gateway did not stop gracefully within 30 seconds"
        return 1
    fi
}

kill_gateway() {
    local pid=$(get_pid)
    
    if [[ -z "$pid" ]]; then
        log_warning "Gateway is not running (no PID file)"
        return 0
    fi
    
    if ! is_running; then
        log_warning "Gateway process (PID $pid) is not running. Removing stale PID file."
        rm -f "$PID_FILE"
        return 0
    fi
    
    log_warning "Force killing Gateway (PID $pid)..."
    kill -9 "$pid"
    sleep 2
    
    if ! is_running; then
        log_success "Gateway killed"
        rm -f "$PID_FILE"
        return 0
    else
        log_error "Failed to kill Gateway process"
        return 1
    fi
}

show_status() {
    local pid=$(get_pid)
    
    if [[ -z "$pid" ]]; then
        log_info "Gateway Status: NOT RUNNING (no PID file)"
        return 1
    fi
    
    if is_running; then
        log_success "Gateway Status: RUNNING (PID $pid)"
        
        # Show additional info if possible
        local memory_usage
        memory_usage=$(ps -o pid,vsz,rss,pcpu,pmem,etime,cmd -p "$pid" 2>/dev/null | tail -1)
        if [[ -n "$memory_usage" ]]; then
            echo "Process Info: $memory_usage"
        fi
        
        # Test health endpoint
        local health_status
        if health_status=$(curl -s -f http://localhost:9090/actuator/health 2>/dev/null); then
            local status=$(echo "$health_status" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
            log_success "Health Status: $status"
        else
            log_warning "Health endpoint not responding"
        fi
        
        return 0
    else
        log_error "Gateway Status: NOT RUNNING (stale PID file)"
        rm -f "$PID_FILE"
        return 1
    fi
}

restart_gateway() {
    log_info "Restarting Gateway..."
    
    if is_running; then
        stop_gateway
        if [[ $? -ne 0 ]]; then
            log_error "Failed to stop Gateway"
            return 1
        fi
    fi
    
    sleep 2
    start_gateway "$@"
}

# Main script logic
case "${1:-}" in
    start)
        start_gateway "${2:-}" "${3:-}"
        ;;
    stop)
        stop_gateway
        ;;
    status)
        show_status
        ;;
    restart)
        restart_gateway "${2:-}" "${3:-}"
        ;;
    kill)
        kill_gateway
        ;;
    *)
        show_usage
        exit 1
        ;;
esac