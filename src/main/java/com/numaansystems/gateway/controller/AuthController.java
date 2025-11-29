package com.numaansystems.gateway. controller;

import com.numaansystems.gateway.service.ExchangeTokenService;
import com.numaansystems.gateway. service.ExchangeTokenService.ExchangeTokenData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j. Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory. annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org. springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for authentication endpoints.
 * 
 * Provides three endpoints for legacy application integration:
 * 1. /auth/initiate - Entry point to start authentication
 * 2.  /auth/validate-token - Validate exchange token (called by legacy app backend)
 * 3. /auth/logout - Logout and clear session
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private ExchangeTokenService exchangeTokenService;

    /**
     * Initiate authentication - Entry point from legacy app
     * 
     * URL: https://gateway.example.com/gateway/auth/initiate? returnUrl=... 
     */
    @GetMapping("/initiate")
    public void initiateAuth(@RequestParam String returnUrl,
                            HttpServletRequest request,
                            HttpServletResponse response,
                            HttpSession session,
                            Authentication authentication) throws IOException {
        
        logger.info("Authentication initiation requested.  ReturnUrl: {}", returnUrl);
        
        // Store returnUrl in session
        session.setAttribute("returnUrl", returnUrl);
        
        if (authentication != null && authentication.isAuthenticated() 
            && ! authentication.getName().equals("anonymousUser")) {
            logger.info("User already authenticated: {}", authentication.getName());
            // Spring Security will handle this via success handler
        }
        
        // Redirect to OAuth2 login (Spring Security handles this)
        response. sendRedirect("/oauth2/authorization/azure");
    }

    /**
     * Validate exchange token - Called by legacy app backend
     * 
     * URL: https://gateway.example. com/gateway/auth/validate-token?token=xxx
     * Method: POST
     */
    @PostMapping("/validate-token")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestParam String token,
                                                             HttpServletRequest request) {
        
        logger.info("Token validation request from: {} for token: {}", 
            request.getRemoteAddr(), token. substring(0, Math.min(8, token.length())) + "...");
        
        ExchangeTokenData data = exchangeTokenService.validateAndRemoveToken(token);
        
        if (data == null) {
            logger.warn("Invalid or expired token validation attempt from: {}", request.getRemoteAddr());
            return ResponseEntity.status(401)
                .body(Map.of("success", false, "error", "Invalid or expired token"));
        }
        
        // Return user info
        Map<String, Object> response = new HashMap<>();
        response. put("success", true);
        response.put("username", data.username);
        response.put("email", data.email);
        response.put("name", data.name);
        response.put("authorities", data.authorities);
        
        logger.info("Token validated successfully for user: {} with {} authorities", 
            data.username, data.authorities.length);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Logout endpoint
     * 
     * URL: https://gateway.example.com/gateway/auth/logout?returnUrl=...
     */
    @GetMapping("/logout")
    public void logout(@RequestParam(required = false) String returnUrl,
                      HttpSession session,
                      HttpServletResponse response) throws IOException {
        
        logger. info("Logout requested. ReturnUrl: {}", returnUrl);
        
        // Invalidate session
        if (session != null) {
            session.invalidate();
        }
        
        // Redirect to returnUrl or home
        String redirectUrl = returnUrl != null ?  returnUrl : "/";
        response.sendRedirect(redirectUrl);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "azure-ad-gateway",
            "version", "1. 0.0"
        ));
    }
}
