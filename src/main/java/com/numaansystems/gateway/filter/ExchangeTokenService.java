package com.numaansystems.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent. Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util. concurrent.TimeUnit;

/**
 * Service for managing exchange tokens. 
 * 
 * Exchange tokens are short-lived (2 minutes), single-use tokens that:
 * - Are created after successful Azure AD authentication
 * - Contain user information and authorities
 * - Are validated once by the legacy application
 * - Are automatically cleaned up after expiration
 * 
 * Production: Replace in-memory storage with Redis for multi-instance deployments. 
 */
@Service
public class ExchangeTokenService {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeTokenService.class);

    @Value("${gateway.exchange-token. ttl-minutes:2}")
    private long ttlMinutes;

    // In-memory token store (use Redis in production)
    private final ConcurrentHashMap<String, ExchangeTokenData> tokens = new ConcurrentHashMap<>();
    
    // Executor for scheduled cleanup
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * Create a new exchange token
     */
    public String createToken(String username, String email, String name, Set<String> authorities) {
        String token = UUID.randomUUID().toString();
        
        ExchangeTokenData data = new ExchangeTokenData();
        data.username = username;
        data.email = email;
        data.name = name;
        data.authorities = authorities. toArray(new String[0]);
        data.expiresAt = System.currentTimeMillis() + (ttlMinutes * 60 * 1000);
        
        tokens.put(token, data);
        
        // Schedule automatic cleanup
        scheduler.schedule(
            () -> {
                if (tokens.remove(token) != null) {
                    logger. info("Cleaned up unused exchange token for user: {}", username);
                }
            },
            ttlMinutes,
            TimeUnit.MINUTES
        );
        
        logger.info("Created exchange token for user: {} (expires in {} minutes)", username, ttlMinutes);
        
        return token;
    }

    /**
     * Validate and remove exchange token (single use)
     */
    public ExchangeTokenData validateAndRemoveToken(String token) {
        // Remove token immediately (single use)
        ExchangeTokenData data = tokens.remove(token);
        
        if (data == null) {
            logger.warn("Invalid or already used exchange token");
            return null;
        }
        
        // Check expiration
        if (System.currentTimeMillis() > data.expiresAt) {
            logger.warn("Expired exchange token for user: {}", data.username);
            return null;
        }
        
        logger.info("Successfully validated exchange token for user: {} with {} authorities", 
            data.username, data.authorities.length);
        return data;
    }

    /**
     * Get token statistics (for monitoring)
     */
    public int getActiveTokenCount() {
        return tokens.size();
    }

    /**
     * Data structure for exchange tokens
     */
    public static class ExchangeTokenData {
        public String username;
        public String email;
        public String name;
        public String[] authorities;
        public long expiresAt;
    }
}
