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
 * GuestRestrictionFilter — enforces server-side role restrictions for guest sessions.
 *
 * WHY THIS IS NEEDED:
 * Guest user restrictions were previously enforced only in the React frontend
 * (greyed-out tabs, locked overlays). Nothing prevented a guest from calling
 * vault or account endpoints directly — e.g. via curl or the browser console.
 *
 * This filter is the authoritative server-side enforcement layer. It runs
 * after AuthFilter (so the session is guaranteed to exist) and before the
 * servlet handler. Any request from "Guest User" to a restricted path is
 * rejected with HTTP 403 and a machine-readable GUEST_RESTRICTED error code.
 *
 * Restricted paths — require a full registered account:
 *   /api/passwords/save     — encrypt and store a password
 *   /api/passwords/fetch    — decrypt and retrieve stored passwords
 *   /api/passwords/delete   — delete a vault entry
 *   /api/passwords/update   — re-encrypt a vault entry
 *   /api/passwords/export   — download encrypted vault JSON
 *   /api/auth/update-password — change account password
 *   /api/user/profile       — view activity summary
 *
 * Unrestricted for guests (tools that require no personal data):
 *   /api/passwords/generate — password generation is stateless and allowed
 *   All calculator endpoints
 *   All analyser endpoints
 */
public class GuestRestrictionFilter implements Filter {

    private static final String GUEST_USERNAME = "Guest User";

    private static final Set<String> RESTRICTED_PATHS = Set.of(
            "/api/passwords/save",
            "/api/passwords/fetch",
            "/api/passwords/delete",
            "/api/passwords/update",
            "/api/passwords/export",
            "/api/passwords/generated-history",
            "/api/auth/update-password",
            "/api/user/profile"
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
        
        // Sprint 17: block guests from all admin endpoints
        if (path.startsWith("/api/admin/")) {
            HttpSession session  = request.getSession(false);
            String      uname    = (session != null)
                    ? (String) session.getAttribute("username") : null;
            if (GUEST_USERNAME.equals(uname)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().print(gson.toJson(
                        ApiResponse.fail("Guest users cannot access admin features.",
                                         "GUEST_RESTRICTED")));
                return;
            }
        }

        if (RESTRICTED_PATHS.contains(path)) {
            HttpSession session  = request.getSession(false);
            String      username = (session != null)
                    ? (String) session.getAttribute("username")
                    : null;

            if (GUEST_USERNAME.equals(username)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().print(gson.toJson(ApiResponse.fail(
                        "This feature requires a registered account. "
                        + "Please sign up to access it.",
                        "GUEST_RESTRICTED")));
                return;
            }
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    @Override public void init(FilterConfig fc) throws ServletException { }
    @Override public void destroy() { }
}
