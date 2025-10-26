package com.numaansystems.gateway.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class GatewayResilienceConfig {

    private static final Logger logger = LoggerFactory.getLogger(GatewayResilienceConfig.class);

    /**
     * Global error handling filter for production resilience
     */
    @Bean
    @Order(-1) // High priority
    public GlobalFilter globalErrorHandlingFilter() {
        return (exchange, chain) -> {
            return chain.filter(exchange)
                .doOnError(error -> {
                    logger.error("Gateway error for request: {} {}", 
                        exchange.getRequest().getMethod(), 
                        exchange.getRequest().getURI(), error);
                })
                .onErrorResume(error -> {
                    // Handle different types of errors gracefully
                    if (error instanceof org.springframework.security.oauth2.core.OAuth2AuthorizationException) {
                        return handleOAuth2Error(exchange, error);
                    } else if (error instanceof java.net.ConnectException) {
                        return handleServiceUnavailable(exchange, error);
                    } else {
                        return handleGenericError(exchange, error);
                    }
                });
        };
    }

    private Mono<Void> handleOAuth2Error(ServerWebExchange exchange, Throwable error) {
        logger.warn("OAuth2 authentication error, redirecting to login: {}", error.getMessage());
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().add("Location", "/oauth2/authorization/azure");
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> handleServiceUnavailable(ServerWebExchange exchange, Throwable error) {
        logger.error("Downstream service unavailable: {}", error.getMessage());
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().add("Retry-After", "30");
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> handleGenericError(ServerWebExchange exchange, Throwable error) {
        logger.error("Unexpected gateway error: {}", error.getMessage());
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        return exchange.getResponse().setComplete();
    }

    /**
     * Fallback routes for error scenarios
     */
    @Bean
    public RouteLocator fallbackRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("fallback", r -> r.path("/**")
                .and().predicate(exchange -> {
                    // Only trigger fallback if main routes failed
                    return exchange.getResponse().getStatusCode() != null && 
                           exchange.getResponse().getStatusCode().is5xxServerError();
                })
                .uri("forward:/error"))
            .build();
    }
}