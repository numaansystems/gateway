# Legacy Application Integration Guide

## AuthenticationFilter.java Implementation
```java
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AuthenticationFilter implements Filter {

    private static final String GATEWAY_URL = "https://gateway.example.com/gateway/auth/initiate";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization logic
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Bypass logic for static resources
        String uri = httpRequest.getRequestURI();
        if (shouldBypass(uri)) {
            chain.doFilter(request, response);
            return;
        }
        
        // Session checking logic here
        if (!isAuthenticated(httpRequest)) {
            httpResponse.sendRedirect(GATEWAY_URL);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean shouldBypass(String uri) {
        // Static resource patterns
        String[] staticPatterns = {
            ".css", ".js", ".jpg", ".png", ".gif",
            ".ico", ".svg", ".woff", ".woff2",
            ".ttf", ".eot", "/static/", "/assets/",
            "/images/", "/css/", "/js/", "/fonts/",
            "/resources/", "/public/", "/webjars/", "/gwt/"
        };
        for (String pattern : staticPatterns) {
            if (uri.contains(pattern)) {
                return true;
            }
        }
        // Excluded paths
        String[] excludedPaths = {
            "/auth/callback", "/auth/logout", "/error",
            "/health", "/login", "/WEB-INF/"
        };
        for (String path : excludedPaths) {
            if (uri.equals(path)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAuthenticated(HttpServletRequest request) {
        // Logic to check authenticated session
        return request.getSession(false) != null;
    }

    private String buildCurrentUrl(HttpServletRequest request) {
        // Logic to build current URL with query parameters
        return request.getRequestURL().toString();
    }

    @Override
    public void destroy() {
        // Cleanup logic
    }
}
```

## AuthCallbackController.java Implementation
```java
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/auth")
public class AuthCallbackController {

    private static final String GATEWAY_VALIDATE_URL = "https://gateway.example.com/gateway/auth/validate-token";

    @PostMapping("/callback")
    public String handleAuthCallback(HttpServletRequest request, @RequestParam String token, HttpSession session) {
        // Token validation logic
        RestTemplate restTemplate = new RestTemplate();
        ValidateResponse response = restTemplate.postForObject(GATEWAY_VALIDATE_URL, token, ValidateResponse.class);
        if (response != null && response.isValid()) {
            // Create session attributes
            session.setAttribute("authenticated", true);
            session.setAttribute("username", response.getUsername());
            session.setAttribute("email", response.getEmail());
            session.setAttribute("name", response.getName());
            session.setAttribute("roles", response.getRoles());
            // Set HTTP-Only and Secure cookies
            // Session timeout configuration
            session.setMaxInactiveInterval(1800);
            return "redirect:/success";
        } else {
            // Error handling
        }
        return "redirect:/error";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        // Logout logic
        session.invalidate();
        return "redirect:/login";
    }
}
```

## web.xml Configuration
```xml
<web-app>
    <filter>
        <filter-name>AuthenticationFilter</filter-name>
        <filter-class>com.example.AuthenticationFilter</filter-class>
    </filter>
    <filter-mapping>
        <filter-name>AuthenticationFilter</filter-name>
        <url-pattern>/legacy-app/*</url-pattern>
        <dispatcher>REQUEST</dispatcher>
        <dispatcher>FORWARD</dispatcher>
    </filter-mapping>
    <session-config>
        <session-timeout>30</session-timeout>
    </session-config>
    <cookie-config>
        <http-only>true</http-only>
        <secure>true</secure>
    </cookie-config>
</web-app>
```

## Configuration
- Update gateway URLs with actual domain names in the `AuthenticationFilter` and `AuthCallbackController` classes.

## Testing
- **Static Resource Bypass:** Ensure static resources load without authentication.
- **Authentication Flow:** Test the full authentication flow to ensure sessions are created properly.
- **Session Persistence:** Verify that sessions persist as expected across requests.
- **Logout:** Ensure logout functionality clears the session and cookies.

## Security Checklist
- Ensure HTTPS is enforced.
- Verify HTTP-Only cookies are used.
- Confirm session timeouts are set correctly.
- Validate open redirect protection logic.

## Troubleshooting
- **Authentication Loops:** Check filter configuration.
- **Static Resource Issues:** Review bypass patterns.
- **Token Validation Failures:** Log responses from the validation service.
- **Session Persistence Issues:** Confirm session management logic.

## Production Considerations
- Implement network security best practices.
- Enable logging and monitoring for security events.
- Optimize performance for session management.
- Handle errors gracefully to maintain user experience.