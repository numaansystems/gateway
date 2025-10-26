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
                .anyExchange().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .authenticationSuccessHandler((exchange, authentication) -> {
                    // Redirect to original requested URL after successful authentication
                    return exchange.getExchange().getResponse().setComplete();
                }))
            .logout(logout -> logout
                .logoutUrl("/logout"))
            .csrf(csrf -> csrf.disable())
            .build();
    }
}
