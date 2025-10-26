package com.numaansystems.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/actuator/**").permitAll()
                .pathMatchers("/login/**", "/oauth2/**", "/error").permitAll()
                .pathMatchers("/fallback/**").permitAll() // Allow fallback endpoints
                .anyExchange().permitAll() // Temporarily allow all for testing
            )
            .csrf(csrf -> csrf.disable())
            .build();
    }
}
