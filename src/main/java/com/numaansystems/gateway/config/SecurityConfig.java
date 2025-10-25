package com.numaansystems.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        // Basic reactive security configuration:
        // - enable oauth2Login (Azure AD)
        // - require authentication for gateway proxied paths
        http
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/actuator/**").permitAll()
                .pathMatchers("/login/**", "/oauth2/**").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2Login()
            .and()
            .logout().and()
            .csrf().disable();

        return http.build();
    }
}
