package com.numaansystems.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final Logger logger = LoggerFactory.getLogger(FallbackController.class);

    @GetMapping("/client1")
    public Mono<ResponseEntity<Map<String, Object>>> client1Fallback() {
        logger.warn("Client1 service is unavailable, returning fallback response");
        
        Map<String, Object> response = Map.of(
            "error", "Service Temporarily Unavailable",
            "message", "Client1 service is currently experiencing issues. Please try again later.",
            "timestamp", LocalDateTime.now(),
            "service", "client1",
            "status", "fallback"
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }

    @GetMapping("/client2")
    public Mono<ResponseEntity<Map<String, Object>>> client2Fallback() {
        logger.warn("Client2 service is unavailable, returning fallback response");
        
        Map<String, Object> response = Map.of(
            "error", "Service Temporarily Unavailable",
            "message", "Client2 service is currently experiencing issues. Please try again later.",
            "timestamp", LocalDateTime.now(),
            "service", "client2",
            "status", "fallback"
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }

    @GetMapping("/{service}")
    public Mono<ResponseEntity<Map<String, Object>>> genericFallback(@PathVariable String service) {
        logger.warn("{} service is unavailable, returning generic fallback response", service);
        
        Map<String, Object> response = Map.of(
            "error", "Service Temporarily Unavailable",
            "message", String.format("%s service is currently experiencing issues. Please try again later.", service),
            "timestamp", LocalDateTime.now(),
            "service", service,
            "status", "fallback"
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }
}