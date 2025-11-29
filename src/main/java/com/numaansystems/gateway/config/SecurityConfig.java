package com.numaansystems.gateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework. security.web.SecurityFilterChain;
import org.springframework.security.web. authentication.AuthenticationSuccessHandler;

/**
 * Security configuration for servlet-based Spring Security.
 * 
 * Configures OAuth2 login with Azure AD and custom authentication success handler.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private AuthenticationSuccessHandler customAuthenticationSuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                // Public endpoints - no authentication required
                . requestMatchers("/actuator/**", "/error", "/auth/**"). permitAll()
                // All other requests require authentication
                .anyRequest(). authenticated()
            )
            . oauth2Login(oauth2 -> oauth2
                // Use custom success handler to create exchange tokens
                .successHandler(customAuthenticationSuccessHandler)
            )
            // Disable CSRF for simplicity (re-enable in production if needed)
            .csrf(csrf -> csrf.disable());
        
        return http.build();
    }
}
