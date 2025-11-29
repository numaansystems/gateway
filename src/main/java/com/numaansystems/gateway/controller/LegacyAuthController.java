package com.numaansystems.gateway.controller;

import com.numaansystems.gateway.config.CustomAuthenticationSuccessHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class LegacyAuthController {

    private static final Logger logger = LoggerFactory.getLogger(LegacyAuthController.class);

    @Autowired
    private CustomAuthenticationSuccessHandler successHandler;

    @GetMapping("/initiate")
    public Mono<Void> initiateAuth(
            @RequestParam String returnUrl,
            ServerWebExchange exchange,
            Authentication authentication) {
        
        logger.info("Legacy app authentication initiation requested. ReturnUrl: {}", returnUrl);
        
        return exchange.getSession().flatMap(session -> {
            session.getAttributes().put("returnUrl", returnUrl);
            
            if (authentication != null && authentication.isAuthenticated()) {
                logger.info("User already authenticated: {}", authentication.getName());
                String exchangeToken = successHandler.createExchangeToken(authentication);
                String callbackUrl = returnUrl.contains("/index.html")
                    ? returnUrl.replace("/index.html", "/auth/callback") + "?token=" + exchangeToken + "&returnUrl=" + returnUrl
                    : returnUrl + "/auth/callback?token=" + exchangeToken + "&returnUrl=" + returnUrl;
                
                exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                exchange.getResponse().getHeaders().setLocation(URI.create(callbackUrl));
                return exchange.getResponse().setComplete();
            }
            
            logger.info("User not authenticated. Redirecting to Azure AD login...");
            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
            exchange.getResponse().getHeaders().setLocation(URI.create("/oauth2/authorization/azure"));
            return exchange.getResponse().setComplete();
        });
    }

    @PostMapping("/validate-token")
    public Mono<ResponseEntity<Map<String, Object>>> validateExchangeToken(
            @RequestParam String token,
            ServerHttpRequest request) {
        
        logger.info("Token validation request from: {}", request.getRemoteAddress());
        
        CustomAuthenticationSuccessHandler.ExchangeTokenData data = 
            successHandler.validateAndRemoveToken(token);
        
        if (data == null) {
            logger.warn("Invalid or expired token validation attempt");
            return Mono.just(ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid or expired token")));
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("username", data.username);
        response.put("email", data.email);
        response.put("name", data.name);
        response.put("roles", data.roles);
        response.put("success", true);
        
        logger.info("Token validated successfully for user: {}", data.username);
        
        return Mono.just(ResponseEntity.ok(response));
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, String>>> health() {
        return Mono.just(ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "legacy-auth-gateway"
        )));
    }
}