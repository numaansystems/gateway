import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@WebServlet({"/auth/callback", "/auth/logout"})
public class AuthCallbackServlet extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getServletPath();
        if ("/auth/callback".equals(path)) {
            handleAuthCallback(request, response);
        } else if ("/auth/logout".equals(path)) {
            handleLogout(request, response);
        }
    }

    private void handleAuthCallback(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = request.getParameter("token");
        if (!isValidToken(token)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
            return;
        }
        createSession(response, token);
        response.sendRedirect("/home");
    }

    private boolean isValidToken(String token) throws IOException {
        URL url = new URL("http://gateway/validateToken");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setDoOutput(true);

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                JSONObject jsonResponse = new JSONObject(response.toString());
                return jsonResponse.getBoolean("valid");
            }
        }
        return false;
    }

    private void createSession(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("sessionToken", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        response.addCookie(cookie);
    }

    private void handleLogout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Cookie cookie = new Cookie("sessionToken", null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        response.addCookie(cookie);
        response.sendRedirect("/login");
    }
}

// AuthenticationFilter for static resource access
@WebFilter("/*")
public class AuthenticationFilter implements Filter {

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String path = req.getServletPath();
        if (path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".png") || path.endsWith(".jpg")) {
            chain.doFilter(request, response);
            return;
        }
        // Add your authentication logic here
        chain.doFilter(request, response);
    }

    public void init(FilterConfig filterConfig) throws ServletException {}
    public void destroy() {}
}

// web.xml configuration
<web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee" version="3.1">
    <servlet>
        <servlet-name>AuthCallbackServlet</servlet-name>
        <servlet-class>com.example.AuthCallbackServlet</servlet-class>
    </servlet>
    <servlet-mapping>
        <servlet-name>AuthCallbackServlet</servlet-name>
        <url-pattern>/auth/callback</url-pattern>
    </servlet-mapping>
    <servlet-mapping>
        <servlet-name>AuthCallbackServlet</servlet-name>
        <url-pattern>/auth/logout</url-pattern>
    </servlet-mapping>
</web-app>

// Dependencies
<dependency>
    <groupId>org.json</groupId>
    <artifactId>json</artifactId>
    <version>20210307</version>
</dependency>

// Testing instructions
1. Deploy the application on a servlet container.
2. Access the endpoint /auth/callback with a valid token to test authentication.
3. Access the endpoint /auth/logout to test logout functionality.

// Troubleshooting guide
- Ensure the token validation URL is accessible.
- Check for any issues with cookie settings.

// Security checklist
- Validate return URL to prevent open redirects.
- Ensure HTTP-Only and Secure flags are set on cookies.
- Review tokens' expiration and refresh strategy.
