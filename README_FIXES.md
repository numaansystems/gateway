# Gateway Routing Fixes

## Issues Fixed

### 1. Removed Custom TokenRelay Filter
**Problem**: Custom `TokenRelayGatewayFilterFactory` was unnecessary and potentially conflicting with Spring Cloud Gateway's built-in `TokenRelay` filter.

**Solution**: 
- Deleted custom `TokenRelayGatewayFilterFactory` entirely
- Use Spring Cloud Gateway's built-in `TokenRelay` filter (much more robust)
- Added proper reactive OAuth2 client manager configuration

### 2. Simplified Configuration
**Problem**: Complex custom filter logic was creating redirect loops and maintenance overhead.

**Solution**:
- Clean application.yml using only built-in Spring Cloud Gateway filters
- Added `StripPrefix=1` to properly forward paths to downstream services
- Configured proper OAuth2 client manager for reactive environment

## Current Architecture

The gateway now uses only Spring Cloud Gateway built-in features:

1. **Built-in TokenRelay Filter**: Automatically handles OAuth2 token forwarding
2. **Spring Security OAuth2 Login**: Handles Azure AD authentication
3. **Reactive OAuth2 Client Manager**: Manages token refresh and client credentials

## Testing the Gateway

1. **Start the gateway**:
   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
   export PATH=$JAVA_HOME/bin:$PATH
   java -jar target/gateway-0.1.0.jar
   ```

2. **Test authentication flow**:
   - Navigate to `http://localhost:8080/client1/api/some-endpoint`
   - Should redirect to Azure AD for authentication
   - After successful auth, forwards to `http://localhost:8081/api/some-endpoint` (note: `/client1` is stripped)

3. **Debug endpoints**:
   - Health: `http://localhost:8080/actuator/health`
   - Gateway routes: `http://localhost:8080/actuator/gateway/routes`

## Environment Variables Required

Set these before running:
```bash
export AZURE_CLIENT_ID="your-actual-client-id"
export AZURE_CLIENT_SECRET="your-actual-client-secret"  
export AZURE_TENANT_ID="your-actual-tenant-id"
```

## Key Benefits of This Approach

1. **No Custom Code**: Uses only proven Spring Cloud Gateway features
2. **No Redirect Loops**: Spring Security handles authentication flow properly
3. **Automatic Token Refresh**: Built-in client manager handles token lifecycle
4. **Less Maintenance**: No custom filter logic to maintain
5. **Better Error Handling**: Built-in filters have comprehensive error handling

## Files Changed

1. **Deleted**: `TokenRelayGatewayFilterFactory.java` (replaced with built-in)
2. **Updated**: `SecurityConfig.java` (Spring Security 6+ reactive API)
3. **Updated**: `application.yml` (simplified to use built-in filters)
4. **Added**: `OAuth2Config.java` (reactive OAuth2 client manager)