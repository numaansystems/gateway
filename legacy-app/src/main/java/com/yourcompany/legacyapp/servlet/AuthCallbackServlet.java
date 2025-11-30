import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;

public class AuthCallbackServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Validate exchange token
        String exchangeToken = request.getParameter("token");
        if (!isValidToken(exchangeToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
            return;
        }

        // Create session
        HttpSession session = request.getSession();
        session.setAttribute("authenticated", true);

        // Handle logout
        String logout = request.getParameter("logout");
        if (logout != null && logout.equalsIgnoreCase("true")) {
            session.invalidate();
            response.sendRedirect("/logout-success");
            return;
        }

        response.sendRedirect("/success");
    }

    private boolean isValidToken(String token) {
        // Implement token validation logic here
        return token != null && token.equals("expected-token"); // Example logic
    }
}