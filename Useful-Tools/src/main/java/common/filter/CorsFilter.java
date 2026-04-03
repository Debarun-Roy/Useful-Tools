package common.filter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import common.AppConfig;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * CORS filter — handles cross-origin requests from the React frontend.
 *
 * CHANGE (Sprint 6): Added X-XSRF-TOKEN to Access-Control-Allow-Headers so
 * the browser's preflight check permits the CSRF token header that React now
 * includes on every state-changing request.
 *
 * Registered exclusively in web.xml — no @WebFilter annotation — so that
 * filter execution order is guaranteed (CorsFilter runs first).
 */
public class CorsFilter implements Filter {

    private static final Set<String> DEFAULT_ALLOWED_ORIGINS = Set.of(
            "http://localhost:3000",
            "http://localhost:5173"
    );

    private Set<String> allowedOrigins = new LinkedHashSet<>(DEFAULT_ALLOWED_ORIGINS);

    @Override
    public void init(FilterConfig fc) throws ServletException {
        allowedOrigins = AppConfig.getCsvSet("cors_allowed_origins", DEFAULT_ALLOWED_ORIGINS);
    }

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String origin = request.getHeader("Origin");

        if (origin != null && allowedOrigins.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin",      origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Methods",     "GET, POST, PUT, DELETE, OPTIONS");
            // X-XSRF-TOKEN added for CSRF double-submit cookie pattern (Sprint 6).
            response.setHeader("Access-Control-Allow-Headers",
                    "Content-Type, Authorization, X-Requested-With, Accept, X-XSRF-TOKEN");
            response.setHeader("Access-Control-Max-Age", "3600");
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    @Override public void destroy() { }
}
