# Gateway Management Scripts

This directory contains the unified management scripts for the Spring Boot Gateway application.

## Directory Structure

```
<app_root>/
├── scripts/          # Management scripts
│   ├── gateway.sh    # Unix/Linux management script
│   └── gateway.bat   # Windows management script
├── config/           # Configuration files
│   ├── application-development.yml
│   ├── application-production.yml
│   └── application-sample.yml
├── logs/             # Application logs
│   └── gateway-{profile}.log
└── libs/             # Application JAR files
    └── gateway-0.1.0.jar
```

## Usage

### Unix/Linux
```bash
# Start the gateway
./scripts/gateway.sh start [profile] [config_dir]

# Stop the gateway
./scripts/gateway.sh stop

# Check status
./scripts/gateway.sh status

# Restart the gateway
./scripts/gateway.sh restart [profile] [config_dir]

# Force kill the gateway
./scripts/gateway.sh kill
```

### Windows
```cmd
REM Start the gateway
scripts\gateway.bat start [profile] [config_dir]

REM Stop the gateway
scripts\gateway.bat stop

REM Check status
scripts\gateway.bat status

REM Restart the gateway
scripts\gateway.bat restart [profile] [config_dir]

REM Force kill the gateway
scripts\gateway.bat kill
```

## Parameters

- **profile**: Spring Boot profile (default: `development`)
  - Available: `development`, `production`, `sample`
- **config_dir**: Configuration directory (default: `<app_root>/config`)

## Examples

```bash
# Start with default development profile
./scripts/gateway.sh start

# Start with production profile
./scripts/gateway.sh start production

# Start with custom config directory
./scripts/gateway.sh start development /custom/config

# Check if gateway is running
./scripts/gateway.sh status

# Gracefully stop the gateway
./scripts/gateway.sh stop

# Restart with production profile
./scripts/gateway.sh restart production
```

## Features

- ✅ **Unified Management**: Single script handles all operations
- ✅ **Cross-Platform**: Separate scripts for Unix/Linux and Windows
- ✅ **PID Management**: Tracks process IDs for reliable start/stop
- ✅ **Health Checks**: Automatically tests endpoints after startup
- ✅ **Graceful Shutdown**: Waits for clean shutdown before force-killing
- ✅ **Environment Validation**: Checks Java version and JAR existence
- ✅ **Colored Output**: Clear status messages with icons and colors
- ✅ **Process Monitoring**: Shows memory usage and process information

## Access URLs

After starting the gateway, you can access:

- **Health Check**: http://localhost:9090/gateway/actuator/health
- **Direct Health**: http://localhost:9090/actuator/health  
- **All Actuator Endpoints**: http://localhost:9090/gateway/actuator
- **Example Route**: http://localhost:9090/gateway/httpbin/get

## Log Files

Logs are written to `logs/gateway-{profile}.log` with automatic rotation:
- **Max Size**: 50MB per file
- **Retention**: 14 days (2 weeks)
- **Total Cap**: 500MB across all log files

## Requirements

- **Java**: 17 or higher
- **Memory**: Minimum 512MB, maximum 2GB (configurable in scripts)
- **Ports**: 9090 (main application port)

## Troubleshooting

### Gateway won't start
```bash
# Check logs
tail -f logs/gateway-development.log

# Verify Java version
java -version

# Check if JAR exists
ls -la libs/gateway-0.1.0.jar

# Force kill any stuck processes
./scripts/gateway.sh kill
```

### Port already in use
```bash
# Find what's using port 9090
lsof -i :9090

# Kill the process using the port
kill -9 <PID>
```

### Permission denied
```bash
# Make script executable
chmod +x scripts/gateway.sh
```