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
 * CSRF protection filter — double-submit cookie pattern.
 *
 * Flow:
 *   1. LoginController generates a UUID CSRF token on successful login.
 *   2. The token is stored in the HTTP session as "csrfToken".
 *   3. The token is also sent as the "XSRF-TOKEN" cookie (HttpOnly=false,
 *      SameSite=Strict) so JavaScript can read it via document.cookie.
 *   4. React reads the cookie and includes it as the "X-XSRF-TOKEN" header
 *      on every POST, PUT, and DELETE request.
 *   5. This filter validates that the header value matches the session value.
 *
 * Skip conditions (request passes through without CSRF check):
 *   - Safe HTTP methods: GET, HEAD, OPTIONS, TRACE
 *   - Public pre-authentication paths: /api/auth/login, /api/auth/register
 *   - No active session (AuthFilter will have already returned 401)
 *   - Session has no csrfToken attribute (pre-Sprint 6 sessions; these expire
 *     naturally as users log out and log back in)
 *
 * Registered in web.xml after AuthFilter so unauthenticated requests are
 * already rejected before this filter runs (except for public paths which
 * are explicitly skipped).
 */
public class CsrfFilter implements Filter {

    private static final Set<String> SAFE_METHODS = Set.of(
            "GET", "HEAD", "OPTIONS", "TRACE");

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/login-guest",
            "/api/auth/register");

    private final Gson gson = new Gson();

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // Safe HTTP methods carry no state-changing payload.
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase())) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        // Public paths (login, register) have no session CSRF token yet.
        if (PUBLIC_PATHS.contains(request.getServletPath())) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        // No active session — AuthFilter will have already sent 401.
        // Guard here defensively to avoid NPE.
        HttpSession session = request.getSession(false);
        if (session == null) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        String sessionToken = (String) session.getAttribute("csrfToken");
        if (sessionToken == null) {
            // Session exists but was created before Sprint 6 (no CSRF token).
            // Grant a grace pass — this window closes once the user logs out
            // and logs back in, receiving a new token.
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        String headerToken = request.getHeader("X-XSRF-TOKEN");
        if (headerToken == null || !sessionToken.equals(headerToken)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().print(gson.toJson(ApiResponse.fail(
                    "CSRF token missing or invalid. Please refresh the page and try again.",
                    "CSRF_INVALID")));
            return;
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    @Override public void init(FilterConfig fc) throws ServletException { }
    @Override public void destroy() { }
}