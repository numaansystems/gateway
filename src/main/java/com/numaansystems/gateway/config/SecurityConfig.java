package com.numaansystems.gateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig {

    @Autowired
    private CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .authorizeExchange(exchanges -> exchanges
                // Public endpoints - no authentication required
                .pathMatchers("/actuator/**").permitAll()
                .pathMatchers("/login/**", "/oauth2/**", "/error").permitAll()
                .pathMatchers("/fallback/**").permitAll()
                
                // Legacy app authentication endpoints
                .pathMatchers("/auth/initiate", "/auth/validate-token", "/auth/health").permitAll()
                
                // Everything else requires authentication
                .anyExchange().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                // Use custom success handler for legacy app integration
                .authenticationSuccessHandler(customAuthenticationSuccessHandler)
            )
            .csrf(csrf -> csrf.disable())
            .build();
    }
}