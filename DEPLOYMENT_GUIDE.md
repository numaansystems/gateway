# Gateway Deployment Guide

## 📁 Project Structure

```
gateway/
├── start-gateway.sh         # Unix/Linux/macOS startup script
├── start-gateway.bat        # Windows startup script  
├── stop-gateway.sh          # Unix/Linux/macOS stop script
├── stop-gateway.bat         # Windows stop script
├── config/                  # External configuration directory
│   ├── application-development.yml
│   ├── application-production.yml
│   └── .env.template
├── logs/                    # Log files (created automatically)
├── target/
│   └── gateway-0.1.0.jar   # Built JAR file
└── gateway.pid              # Process ID file (created when running)
```

## 🚀 Quick Start

### **1. Build the Application**
```bash
mvn clean package
```

### **2. Configure Environment**
```bash
# Copy and customize environment template
cp config/.env.template config/.env
nano config/.env
```

### **3. Start the Gateway**

**Unix/Linux/macOS:**
```bash
# Development mode
./start-gateway.sh

# Production mode with custom config
./start-gateway.sh production /opt/gateway/config
```

**Windows:**
```cmd
REM Development mode
start-gateway.bat

REM Production mode with custom config
start-gateway.bat production C:\gateway\config
```

### **4. Stop the Gateway**

**Unix/Linux/macOS:**
```bash
./stop-gateway.sh
```

**Windows:**
```cmd
stop-gateway.bat
```

## ⚙️ Configuration Management

### **Configuration Precedence (highest to lowest):**
1. Command line arguments: `--server.port=8080`
2. Environment variables: `AZURE_CLIENT_ID`  
3. External config files: `config/application-{profile}.yml`
4. Embedded config: `src/main/resources/application.yml`

### **Environment-Specific Configuration:**

**Development (`application-development.yml`):**
- Detailed logging and debugging
- Relaxed circuit breaker settings
- Local service URLs (localhost:8081, localhost:8082)
- Extended timeouts for debugging

**Production (`application-production.yml`):**
- Minimal logging for performance
- Strict circuit breaker settings  
- Service discovery URLs (CLIENT1_URL, CLIENT2_URL)
- Optimized timeouts and security

### **Required Environment Variables:**
```bash
export AZURE_CLIENT_ID="your-azure-client-id"
export AZURE_CLIENT_SECRET="your-azure-client-secret"  
export AZURE_TENANT_ID="your-azure-tenant-id"
```

### **Optional Environment Variables:**
```bash
export CLIENT1_URL="http://client1-service:8080"
export CLIENT2_URL="http://client2-service:8080"
export ENVIRONMENT="production"
export SERVER_PORT="8080"
```

## 🐳 Docker Deployment

### **Create Dockerfile:**
```dockerfile
FROM openjdk:17-jre-slim

WORKDIR /app
COPY target/gateway-0.1.0.jar app.jar
COPY config/ config/

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=production
ENV SPRING_CONFIG_LOCATION=classpath:/application.yml,file:./config/

CMD ["java", "-jar", "app.jar"]
```

### **Docker Compose:**
```yaml
version: '3.8'
services:
  gateway:
    build: .
    ports:
      - "8080:8080"
    environment:
      - AZURE_CLIENT_ID=${AZURE_CLIENT_ID}
      - AZURE_CLIENT_SECRET=${AZURE_CLIENT_SECRET}
      - AZURE_TENANT_ID=${AZURE_TENANT_ID}
      - CLIENT1_URL=http://client1:8080
      - CLIENT2_URL=http://client2:8080
    volumes:
      - ./logs:/app/logs
    depends_on:
      - client1
      - client2
```

## 📊 Monitoring

### **Health Checks:**
- **Health**: `GET /actuator/health`
- **Routes**: `GET /actuator/gateway/routes`  
- **Circuit Breakers**: `GET /actuator/circuitbreakers`
- **Metrics**: `GET /actuator/prometheus`

### **Log Files:**
- **Location**: `logs/gateway.log`
- **Rotation**: Automatic (Spring Boot Logback)
- **Pattern**: Includes trace IDs for correlation

### **Process Management:**
- **PID File**: `gateway.pid`
- **Graceful Shutdown**: SIGTERM with 30s timeout
- **Force Kill**: SIGKILL if graceful fails

## 🔧 Script Features

### **Unix/Linux/macOS Scripts:**
✅ **Java Version Validation** - Ensures Java 17+  
✅ **Process Management** - PID tracking and validation  
✅ **Graceful Shutdown** - SIGTERM → SIGKILL escalation  
✅ **Health Checks** - Startup validation  
✅ **Flexible Configuration** - Profile and config directory arguments  
✅ **Logging** - Automatic log directory creation  

### **Windows Scripts:**  
✅ **Java Version Validation** - Ensures Java 17+  
✅ **Process Management** - PID tracking via tasklist  
✅ **Graceful Shutdown** - taskkill → taskkill /F escalation  
✅ **Health Checks** - Startup validation  
✅ **Flexible Configuration** - Profile and config directory arguments  
✅ **Logging** - Automatic log directory creation  

## 🛠️ Troubleshooting

### **Common Issues:**

**1. "JAR file not found"**
```bash
mvn clean package  # Rebuild the JAR
```

**2. "Java 17 or higher is required"**  
```bash
# Install Java 17+ or set JAVA_HOME
export JAVA_HOME=/path/to/java-17
```

**3. "Gateway is already running"**
```bash
./stop-gateway.sh  # Stop existing instance
./start-gateway.sh # Start fresh
```

**4. "Failed to start Gateway"**
```bash
tail -f logs/gateway.log  # Check logs for errors
```

### **Debug Commands:**
```bash
# Check if running
ps aux | grep gateway

# Monitor logs  
tail -f logs/gateway.log

# Check Java processes
jps -v | grep gateway

# Test endpoints
curl http://localhost:8080/actuator/health
```

## 🔒 Security Considerations

### **Production Checklist:**
- [ ] Use strong OAuth2 client credentials
- [ ] Enable HTTPS/TLS termination at load balancer
- [ ] Restrict actuator endpoints (production config does this)
- [ ] Use secrets management (K8s secrets, HashiCorp Vault, etc.)
- [ ] Enable audit logging
- [ ] Set up monitoring and alerting
- [ ] Regular security updates

### **Network Security:**
- Gateway should be behind a load balancer/reverse proxy
- Internal service communication should use private networks
- OAuth2 redirect URLs should use HTTPS in production

This deployment setup provides enterprise-grade configuration management with environment-specific settings and robust startup/shutdown scripts! 🚀