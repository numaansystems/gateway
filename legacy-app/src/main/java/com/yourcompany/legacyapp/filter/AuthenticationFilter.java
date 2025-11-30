package com.yourcompany.legacyapp.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Authentication filter for legacy application.
 * 
 * Checks if user is authenticated in session. If not, redirects to gateway.
 * Allows public resources (CSS, JS, images, GWT) to bypass authentication.
 */
@WebFilter(urlPatterns = "/*")
public class AuthenticationFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

    // Gateway configuration
    private static final String GATEWAY_INITIATE_URL = "https://gateway.example.com/gateway/auth/initiate";
    
    // Public paths that don't require authentication
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
        "/auth/callback",
        "/login",
        "/error",
        "/health"
    );
    
    // Static resource patterns
    private static final List<String> STATIC_PATTERNS = Arrays.asList(
        ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".ico", 
        ".woff", ".woff2", ".ttf", ".svg", ".map"
    );
    
    // GWT patterns
    private static final List<String> GWT_PATTERNS = Arrays.asList(
        ".nocache.js", ".cache.html", ".cache.js", ".gwt.rpc"
    );

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("AuthenticationFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String requestURI = httpRequest.getRequestURI();
        String contextPath = httpRequest.getContextPath();
        String path = requestURI.substring(contextPath.length());
        
        logger.debug("Processing request: {}", path);
        
        // 1. Check if path is public (no auth required)
        if (isPublicPath(path)) {
            logger.debug("Public path, bypassing authentication: {}", path);
            chain.doFilter(request, response);
            return;
        }
        
        // 2. Check if static resource (CSS, JS, images, GWT)
        if (isStaticResource(path)) {
            logger.debug("Static resource, bypassing authentication: {}", path);
            chain.doFilter(request, response);
            return;
        }
        
        // 3. Check if user is authenticated
        HttpSession session = httpRequest.getSession(false);
        if (session != null && isAuthenticated(session)) {
            logger.debug("User authenticated: {}", session.getAttribute("username"));
            chain.doFilter(request, response);
            return;
        }
        
        // 4. Not authenticated - redirect to gateway
        logger.info("User not authenticated, redirecting to gateway for path: {}", path);
        redirectToGateway(httpRequest, httpResponse);
    }

    /**
     * Check if user is authenticated in session
     */
    private boolean isAuthenticated(HttpSession session) {
        Boolean authenticated = (Boolean) session.getAttribute("authenticated");
        return authenticated != null && authenticated;
    }

    /**
     * Check if path is public (doesn't require authentication)
     */
    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Check if path is a static resource
     */
    private boolean isStaticResource(String path) {
        String lowerPath = path.toLowerCase();
        
        // Check static file extensions
        if (STATIC_PATTERNS.stream().anyMatch(lowerPath::endsWith)) {
            return true;
        }
        
        // Check GWT patterns
        if (GWT_PATTERNS.stream().anyMatch(lowerPath::contains)) {
            return true;
        }
        
        // Check common static directories
        if (lowerPath.startsWith("/static/") || 
            lowerPath.startsWith("/css/") || 
            lowerPath.startsWith("/js/") || 
            lowerPath.startsWith("/images/") ||
            lowerPath.startsWith("/resources/")) {
            return true;
        }
        
        return false;
    }

    /**
     * Redirect to gateway for authentication
     */
    private void redirectToGateway(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        // Build full return URL
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        
        StringBuilder returnUrl = new StringBuilder();
        returnUrl.append(scheme).append("://").append(serverName);
        
        // Add port if not default
        if ((scheme.equals("http") && serverPort != 80) || 
            (scheme.equals("https") && serverPort != 443)) {
            returnUrl.append(":").append(serverPort);
        }
        
        returnUrl.append(requestURI);
        
        if (queryString != null) {
            returnUrl.append("?").append(queryString);
        }
        
        // Build gateway redirect URL
        String gatewayUrl = GATEWAY_INITIATE_URL + "?returnUrl=" + 
            URLEncoder.encode(returnUrl.toString(), StandardCharsets.UTF_8);
        
        logger.info("Redirecting to gateway: {}", gatewayUrl);
        response.sendRedirect(gatewayUrl);
    }

    @Override
    public void destroy() {
        logger.info("AuthenticationFilter destroyed");
    }
}