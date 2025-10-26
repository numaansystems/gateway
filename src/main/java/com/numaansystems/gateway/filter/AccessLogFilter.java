package com.numaansystems.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Global filter for access logging and request/response tracking.
 * Logs essential request information for monitoring and debugging.
 */
@Component
public class AccessLogFilter implements GlobalFilter, Ordered {

    private static final Logger accessLogger = LoggerFactory.getLogger("gateway.access");
    private static final Logger logger = LoggerFactory.getLogger(AccessLogFilter.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long startTime = System.currentTimeMillis();
        
        // Store start time for response time calculation
        exchange.getAttributes().put("requestTime", startTime);
        
        // Log incoming request
        String requestLog = buildRequestLog(request, startTime);
        accessLogger.info(requestLog);
        
        return chain.filter(exchange)
            .doOnSuccess(aVoid -> {
                // Log successful response
                String responseLog = buildResponseLog(exchange, startTime, "SUCCESS");
                accessLogger.info(responseLog);
            })
            .doOnError(throwable -> {
                // Log error response
                String responseLog = buildResponseLog(exchange, startTime, "ERROR");
                accessLogger.error("{} - Error: {}", responseLog, throwable.getMessage());
                logger.error("Request processing error", throwable);
            });
    }

    private String buildRequestLog(ServerHttpRequest request, long startTime) {
        return String.format("[%s] REQUEST: %s %s | RemoteAddr: %s | UserAgent: %s | Headers: %d | QueryParams: %s",
            LocalDateTime.now().format(DATE_FORMATTER),
            request.getMethod(),
            request.getURI(),
            getClientIpAddress(request),
            getUserAgent(request),
            request.getHeaders().size(),
            request.getURI().getQuery() != null ? request.getURI().getQuery() : "none"
        );
    }

    private String buildResponseLog(ServerWebExchange exchange, long startTime, String status) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        long responseTime = System.currentTimeMillis() - startTime;
        
        return String.format("[%s] RESPONSE: %s %s | Status: %s | ResponseTime: %dms | ContentLength: %s",
            LocalDateTime.now().format(DATE_FORMATTER),
            request.getMethod(),
            request.getURI().getPath(),
            response.getStatusCode() != null ? response.getStatusCode().value() : "unknown",
            responseTime,
            response.getHeaders().getContentLength() >= 0 ? response.getHeaders().getContentLength() + "B" : "unknown"
        );
    }

    private String getClientIpAddress(ServerHttpRequest request) {
        // Check various headers for client IP (useful behind proxies/load balancers)
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        String xClientIp = request.getHeaders().getFirst("X-Client-IP");
        if (xClientIp != null && !xClientIp.isEmpty()) {
            return xClientIp;
        }
        
        // Fallback to remote address
        return request.getRemoteAddress() != null ? 
            request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    private String getUserAgent(ServerHttpRequest request) {
        String userAgent = request.getHeaders().getFirst("User-Agent");
        return userAgent != null ? userAgent : "unknown";
    }

    @Override
    public int getOrder() {
        // Execute early in the filter chain to capture all requests
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}