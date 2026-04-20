package common.filter;

import java.io.IOException;

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

/**
 * AdminFilter — Sprint 17 RBAC.
 *
 * Restricts all /api/admin/* endpoints to users whose session role is 'admin'.
 * Runs AFTER AuthFilter (which sets UserContext.setRole) in the filter chain.
 *
 * Non-admin authenticated users receive HTTP 403 FORBIDDEN.
 * Unauthenticated users are already rejected by AuthFilter with 401 before
 * this filter ever runs.
 *
 * Registered in web.xml after GuestRestrictionFilter, before CsrfFilter.
 * Do NOT add @WebFilter — uses web.xml registration for order control.
 */
public class AdminFilter implements Filter {

    private final Gson gson = new Gson();

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // Only intercept /api/admin/* paths
        String path = request.getServletPath();
        if (!path.startsWith("/api/admin/")) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        // UserContext.getRole() was populated by AuthFilter.
        // If it's not 'admin', return 403.
        if (!UserContext.isAdmin()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().print(gson.toJson(
                ApiResponse.fail(
                    "Administrator access required.",
                    "FORBIDDEN")));
            return;
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    @Override public void init(FilterConfig fc) throws ServletException { }
    @Override public void destroy() { }
}
