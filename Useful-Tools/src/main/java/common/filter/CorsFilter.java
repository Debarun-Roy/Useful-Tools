package common.filter;

import java.io.IOException;
import java.util.Set;

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
 * CHANGE FROM BATCH 1: The @WebFilter("/*") annotation has been REMOVED.
 * This filter is now registered exclusively in web.xml, which guarantees
 * it runs before AuthFilter. If @WebFilter were present alongside the
 * web.xml declaration, the filter would be registered twice, causing
 * duplicate Access-Control-* headers on every response. Duplicate headers
 * break the browser's CORS check — it expects exactly one value per header.
 *
 * ── How it works ─────────────────────────────────────────────────────────
 * 1. For every request: checks the Origin header against ALLOWED_ORIGINS.
 *    If matched, sets the three required Access-Control-* headers and echoes
 *    the exact origin back (required when credentials:true is used — the
 *    wildcard "*" is forbidden by the browser spec in that case).
 *
 * 2. For OPTIONS (preflight): responds immediately with 200 OK and returns.
 *    Does NOT call chain.doFilter(). This prevents AuthFilter from ever
 *    seeing preflight requests, which would incorrectly reject them with 401.
 *
 * ── Adding a production origin ───────────────────────────────────────────
 * Add your deployed React app URL to ALLOWED_ORIGINS before going live.
 * Example: "https://yourdomain.com"
 * Never add "*" to this set — credentials:true forbids the wildcard.
 *
 * ── Eclipse setup ────────────────────────────────────────────────────────
 * Place in: src/common/filter/CorsFilter.java
 * Register in: WebContent/WEB-INF/web.xml (do NOT add @WebFilter back)
 */
public class CorsFilter implements Filter {

    /**
     * All origins permitted to make credentialed cross-origin requests.
     * localhost:3000 is the default for create-react-app.
     * localhost:5173 is the default for Vite.
     */
    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "http://localhost:3000",
            "http://localhost:5173"
    );

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String origin = request.getHeader("Origin");

        /*
         * Only set CORS headers when the request comes from a known origin.
         * Requests without an Origin header (e.g. same-origin, curl, Postman)
         * are passed through without CORS headers — the browser does not need
         * them for same-origin requests, and non-browser clients do not check them.
         */
        if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
            // Echo the exact origin — wildcard is forbidden when credentials:true.
            response.setHeader("Access-Control-Allow-Origin",      origin);
            // Allow the browser to include the session cookie automatically.
            response.setHeader("Access-Control-Allow-Credentials", "true");
            // HTTP methods the React client will use.
            response.setHeader("Access-Control-Allow-Methods",     "GET, POST, PUT, DELETE, OPTIONS");
            // Request headers the React client will send.
            response.setHeader("Access-Control-Allow-Headers",
                    "Content-Type, Authorization, X-Requested-With, Accept");
            // Cache the preflight response for 1 hour to reduce round-trips.
            response.setHeader("Access-Control-Max-Age",           "3600");
        }

        /*
         * Short-circuit OPTIONS (the browser's preflight check).
         * Return 200 immediately without forwarding to any servlet or filter.
         * AuthFilter must never see a preflight — it would reject it with 401.
         */
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;  // Do NOT call chain.doFilter
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    @Override public void init(FilterConfig fc) throws ServletException { }
    @Override public void destroy() { }
}