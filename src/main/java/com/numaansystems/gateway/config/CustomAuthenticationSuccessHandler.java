package com.numaansystems.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class CustomAuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(CustomAuthenticationSuccessHandler.class);
    
    // Exchange token expiration: 2 minutes to accommodate MFA flows
    private static final long EXCHANGE_TOKEN_TTL_MS = 120_000;
    
    // In-memory token store (use Redis in production for multi-instance deployments)
    private final ConcurrentHashMap<String, ExchangeTokenData> exchangeTokens = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> onAuthenticationSuccess(
            WebFilterExchange webFilterExchange,
            Authentication authentication) {
        
        ServerWebExchange exchange = webFilterExchange.getExchange();
        ServerHttpResponse response = exchange.getResponse();
        
        // Get the return URL from session
        return exchange.getSession().flatMap(session -> {
            String returnUrl = (String) session.getAttributes().get("returnUrl");
            
            if (returnUrl != null && returnUrl.contains("/legacy-app")) {
                logger.info("Legacy app authentication detected. Creating exchange token for: {}", 
                    authentication.getName());
                
                // Create exchange token
                String exchangeToken = createExchangeToken(authentication);
                
                // Build callback URL for legacy app
                String callbackUrl = buildLegacyCallbackUrl(returnUrl, exchangeToken);
                
                logger.info("Redirecting to legacy app callback: {}", callbackUrl);
                
                // Clear session attribute
                session.getAttributes().remove("returnUrl");
                
                // Redirect to legacy app
                response.setStatusCode(HttpStatus.FOUND);
                response.getHeaders().setLocation(URI.create(callbackUrl));
                return response.setComplete();
            }
            
            // Default redirect for non-legacy apps
            logger.info("Standard authentication complete for: {}", authentication.getName());
            response.setStatusCode(HttpStatus.FOUND);
            response.getHeaders().setLocation(URI.create("/"));
            return response.setComplete();
        });
    }

    public String createExchangeToken(Authentication authentication) {
        String exchangeToken = UUID.randomUUID().toString();
        
        // Extract user info from Azure AD token
        OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
        Map<String, Object> attributes = oauth2Token.getPrincipal().getAttributes();
        
        ExchangeTokenData tokenData = new ExchangeTokenData();
        tokenData.username = (String) attributes.getOrDefault("preferred_username", 
            (String) attributes.getOrDefault("email", "unknown"));
        tokenData.email = (String) attributes.getOrDefault("email", "");
        tokenData.name = (String) attributes.getOrDefault("name", "");
        tokenData.roles = extractRoles(attributes);
        tokenData.expiresAt = System.currentTimeMillis() + EXCHANGE_TOKEN_TTL_MS;
        
        // Store token (single use)
        exchangeTokens.put(exchangeToken, tokenData);
        
        // Schedule cleanup
        scheduleTokenCleanup(exchangeToken);
        
        logger.info("Created exchange token for user: {} (expires in 2 minutes)", tokenData.username);
        
        return exchangeToken;
    }

    public ExchangeTokenData validateAndRemoveToken(String token) {
        ExchangeTokenData data = exchangeTokens.remove(token); // Single use!
        
        if (data == null) {
            logger.warn("Invalid or already used exchange token: {}", token);
            return null;
        }
        
        if (System.currentTimeMillis() > data.expiresAt) {
            logger.warn("Expired exchange token: {}", token);
            return null;
        }
        
        logger.info("Successfully validated exchange token for user: {}", data.username);
        return data;
    }

    private String buildLegacyCallbackUrl(String returnUrl, String exchangeToken) {
        // Extract base URL from return URL
        String baseUrl;
        if (returnUrl.contains("/legacy-app/")) {
            baseUrl = returnUrl.substring(0, returnUrl.indexOf("/legacy-app/") + "/legacy-app".length());
        } else {
            baseUrl = returnUrl;
        }
        
        // Build callback URL
        return baseUrl + "/auth/callback?token=" + exchangeToken + 
               "&returnUrl=" + java.net.URLEncoder.encode(returnUrl, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String[] extractRoles(Map<String, Object> attributes) {
        // Extract roles from Azure AD token
        Object rolesObj = attributes.get("roles");
        if (rolesObj instanceof java.util.List) {
            java.util.List<?> rolesList = (java.util.List<?>) rolesObj;
            return rolesList.stream()
                .map(Object::toString)
                .toArray(String[]::new);
        }
        return new String[0];
    }

    private void scheduleTokenCleanup(String token) {
        // Use scheduled cleanup (in production, use @Scheduled or Redis TTL)
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
            .schedule(() -> {
                if (exchangeTokens.remove(token) != null) {
                    logger.info("Cleaned up unused exchange token: {}", token);
                }
            }, EXCHANGE_TOKEN_TTL_MS, TimeUnit.MILLISECONDS);
    }

    public static class ExchangeTokenData {
        public String username;
        public String email;
        public String name;
        public String[] roles;
        public long expiresAt;
    }
}