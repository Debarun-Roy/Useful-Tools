package common.filter;

import java.io.IOException;
import java.util.Set;

import com.google.gson.Gson;

import common.ApiResponse;
import common.UserContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Authentication filter — protects all /api/* endpoints.
 *
 * Sprint 17 change: also reads the "role" session attribute and sets it
 * in UserContext so AdminFilter and downstream code can read it without
 * re-querying the session.
 *
 * Filter chain position: 4th (after SameSite, CORS, RateLimit).
 * Registered in web.xml — do NOT add @WebFilter.
 */
public class AuthFilter implements Filter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/login-guest",
            "/api/auth/register"
    );

    private final Gson gson = new Gson();

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getServletPath();

        if (PUBLIC_PATHS.contains(path)) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        HttpSession session  = request.getSession(false);
        String      username = (session != null)
                ? (String) session.getAttribute("username") : null;

        if (username != null && !username.isBlank()) {
            // ── Sprint 17: read role from session ─────────────────────────
            String role = (session != null)
                    ? (String) session.getAttribute("role") : null;
            if (role == null || role.isBlank()) {
                role = UserContext.ROLE_USER; // default — handles pre-17 sessions
            }

            UserContext.set(username);
            UserContext.setRole(role);
            try {
                chain.doFilter(servletRequest, servletResponse);
            } finally {
                UserContext.clear(); // clears both username and role
            }
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(gson.toJson(
                ApiResponse.fail(
                        "You must be logged in to access this resource.",
                        "UNAUTHENTICATED")));
    }

    @Override public void init(FilterConfig fc) throws ServletException { }
    @Override public void destroy() { }
}
