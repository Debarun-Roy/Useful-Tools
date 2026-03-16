package common.filter;

import java.io.IOException;
import java.util.Set;

import com.google.gson.Gson;
import common.ApiResponse;
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
 * ── What it does ──────────────────────────────────────────────────────────
 * For every incoming request to /api/*:
 *   1. Checks whether the request targets a public (unauthenticated) path.
 *      If so, passes through immediately.
 *   2. Reads the current HTTP session (without creating a new one).
 *   3. Checks for a "username" attribute in the session, which is written
 *      by LoginController on successful authentication.
 *   4. If username is present: passes the request through to the servlet.
 *   5. If username is absent: returns 401 with a structured JSON error.
 *
 * ── Why no @WebFilter annotation ─────────────────────────────────────────
 * This filter is registered exclusively in web.xml to guarantee it runs
 * AFTER CorsFilter. See web.xml for a full explanation of why order matters.
 * Do not add @WebFilter here — it would cause double registration.
 *
 * ── Session mechanism ─────────────────────────────────────────────────────
 * This filter uses the standard HttpSession mechanism (server-side session
 * stored in memory, session ID in a JSESSIONID cookie). The React client
 * must include `credentials: 'include'` in every fetch call so the browser
 * sends the session cookie automatically on cross-origin requests.
 *
 * ── Adding new public endpoints ───────────────────────────────────────────
 * Add the servlet path to the PUBLIC_PATHS set below. The path must exactly
 * match the value in the servlet's @WebServlet annotation, without any
 * query string.
 *
 * ── Eclipse setup ────────────────────────────────────────────────────────
 * Place in: src/common/filter/AuthFilter.java
 * Register in: WebContent/WEB-INF/web.xml (do NOT add @WebFilter)
 */
public class AuthFilter implements Filter {

    /**
     * Paths under /api/* that do not require authentication.
     * These are the entry points — a user cannot have a session before logging in.
     */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
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

        // ── 1. Always allow public endpoints through ─────────────────────
        if (PUBLIC_PATHS.contains(path)) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        // ── 2. Check for an active, authenticated session ─────────────────
        // getSession(false) returns null if no session exists rather than
        // creating a new empty one. Creating a session here would leak
        // server memory and give the impression of a valid but empty session.
        HttpSession session = request.getSession(false);
        String username     = (session != null)
                ? (String) session.getAttribute("username")
                : null;

        // ── 3a. Authenticated — pass through ──────────────────────────────
        if (username != null && !username.isBlank()) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        // ── 3b. Not authenticated — return 401 ────────────────────────────
        // Return JSON so React can handle it programmatically (navigate to
        // /login) rather than receiving HTML it cannot parse.
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