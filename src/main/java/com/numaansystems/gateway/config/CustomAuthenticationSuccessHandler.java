package com.numaansystems.gateway.config;

import com.numaansystems.gateway. service.ExchangeTokenService;
import com.numaansystems.gateway. service.UserAuthorityService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core. GrantedAuthority;
import org.springframework.security.oauth2. client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user. OAuth2User;
import org. springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java. nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Custom authentication success handler for Azure AD OAuth2 integration.
 * 
 * After successful Azure AD authentication, this handler:
 * 1. Extracts user information from OAuth2 token
 * 2. Loads additional authorities from database (if available)
 * 3. Creates a short-lived exchange token
 * 4. Redirects to legacy application callback with the token
 */
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(CustomAuthenticationSuccessHandler.class);

    @Autowired
    private ExchangeTokenService exchangeTokenService;

    @Autowired(required = false)
    private UserAuthorityService userAuthorityService;

    @Value("${gateway.allowed-redirect-domains}")
    private List<String> allowedRedirectDomains;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, 
                                       HttpServletResponse response,
                                       Authentication authentication) throws IOException, ServletException {
        
        HttpSession session = request.getSession();
        String returnUrl = (String) session.getAttribute("returnUrl");
        
        if (returnUrl != null && ! returnUrl.isEmpty()) {
            logger.info("Processing legacy app authentication for: {}", authentication.getName());
            
            // Validate returnUrl domain
            if (!isAllowedDomain(returnUrl)) {
                logger.warn("Attempted redirect to unauthorized domain: {}", returnUrl);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized redirect domain");
                return;
            }
            
            // Extract user info
            OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
            OAuth2User oauth2User = oauth2Token.getPrincipal();
            Map<String, Object> attributes = oauth2User.getAttributes();
            
            String username = (String) attributes.getOrDefault("preferred_username", 
                (String) attributes.getOrDefault("email", "unknown"));
            String email = (String) attributes.getOrDefault("email", "");
            String name = (String) attributes.getOrDefault("name", "");
            
            // Get authorities (Azure AD + Database)
            Set<String> authorities = extractAuthorities(username, oauth2User);
            
            // Create exchange token
            String exchangeToken = exchangeTokenService.createToken(username, email, name, authorities);
            
            logger.info("Created exchange token for user: {} with {} authorities", username, authorities. size());
            
            // Build callback URL
            String callbackUrl = buildCallbackUrl(returnUrl, exchangeToken);
            
            // Clear returnUrl from session
            session.removeAttribute("returnUrl");
            
            // Redirect to legacy app callback
            response.sendRedirect(callbackUrl);
        } else {
            // No returnUrl - default redirect
            logger.info("Standard authentication complete for: {}", authentication.getName());
            response.sendRedirect("/");
        }
    }

    /**
     * Extract authorities from Azure AD and optionally from database
     */
    private Set<String> extractAuthorities(String username, OAuth2User oauth2User) {
        Set<String> authorities = new HashSet<>();
        
        // 1. Extract Azure AD roles
        Object rolesObj = oauth2User.getAttributes().get("roles");
        if (rolesObj instanceof List) {
            ((List<?>) rolesObj).forEach(role -> authorities.add(role.toString()));
        }
        
        // 2. Add Spring Security authorities
        oauth2User.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .forEach(authorities::add);
        
        // 3. Load from database if service available
        if (userAuthorityService != null) {
            try {
                Collection<String> dbAuthorities = userAuthorityService.loadAuthoritiesByUsername(username);
                authorities.addAll(dbAuthorities);
                logger.info("Loaded {} authorities from database for user: {}", dbAuthorities.size(), username);
            } catch (Exception e) {
                logger.error("Error loading authorities from database for user: {}", username, e);
            }
        }
        
        logger.info("Total authorities for user {}: {}", username, authorities);
        return authorities;
    }

    /**
     * Build callback URL for legacy application
     */
    private String buildCallbackUrl(String returnUrl, String exchangeToken) {
        try {
            // Extract base URL for callback
            String baseUrl = returnUrl;
            if (returnUrl.contains("/legacy-app/")) {
                baseUrl = returnUrl.substring(0, returnUrl.indexOf("/legacy-app/") + "/legacy-app". length());
            }
            
            return baseUrl + "/auth/callback? token=" + exchangeToken + 
                   "&returnUrl=" + URLEncoder. encode(returnUrl, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Error building callback URL", e);
            throw new RuntimeException("Error building callback URL", e);
        }
    }

    /**
     * Validate that returnUrl is from an allowed domain
     */
    private boolean isAllowedDomain(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            
            boolean allowed = allowedRedirectDomains.stream()
                .anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
            
            if (!allowed) {
                logger. warn("Domain {} not in allowed list: {}", host, allowedRedirectDomains);
            }
            
            return allowed;
        } catch (Exception e) {
            logger.error("Invalid URL: {}", url, e);
            return false;
        }
    }
}
