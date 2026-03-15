package common.filter;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * CORS filter — must be the FIRST filter in the chain.
 *
 * Applies to every request ("/*") and does two things:
 *   1. Sets the appropriate Access-Control-* headers on every response so the
 *      React dev server (or production build) can communicate with this backend.
 *   2. Short-circuits HTTP OPTIONS preflight requests immediately with 200 OK,
 *      because the browser sends a preflight before every cross-origin request
 *      that carries credentials or uses a non-simple method/header. If the
 *      preflight is not answered correctly, the actual request is never sent.
 *
 * ── Why Access-Control-Allow-Origin cannot be "*" ───────────────────────────
 * When Access-Control-Allow-Credentials is "true" (required so the browser
 * sends the session cookie automatically), the browser spec forbids the wildcard
 * "*" as the allowed origin. It must be the exact requesting origin. We handle
 * this by checking the incoming Origin header against a whitelist and echoing
 * back the matching origin, or blocking by not setting the header at all.
 *
 * ── Production note ─────────────────────────────────────────────────────────
 * Add your deployed React app origin to ALLOWED_ORIGINS before going live.
 * Never add "*" to the allowed origins set when credentials are enabled.
 *
 * ── Eclipse setup ───────────────────────────────────────────────────────────
 * Place in: src/common/filter/CorsFilter.java
 * No web.xml entry is needed — @WebFilter handles registration (Servlet 3.0+).
 */
@WebFilter("/*")
public class CorsFilter implements Filter {

    /**
     * All origins permitted to make credentialed cross-origin requests.
     * Add your production frontend URL here before deploying.
     */
    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "http://localhost:3000",   // React dev server (create-react-app default)
            "http://localhost:5173"    // React dev server (Vite default)
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
         * Only set Access-Control-Allow-Origin if the request comes from a
         * known origin. If origin is null (same-origin or non-browser client)
         * or not in the whitelist, we simply don't add the CORS headers — the
         * browser will block the response, which is the correct security behaviour.
         */
        if (origin != null && ALLOWED_ORIGINS.contains(origin)) {

            // Echo the exact origin back — required when credentials are true.
            response.setHeader("Access-Control-Allow-Origin",      origin);

            // Allow the browser to send the session cookie automatically.
            response.setHeader("Access-Control-Allow-Credentials", "true");

            // Methods the React app will use.
            response.setHeader("Access-Control-Allow-Methods",
                    "GET, POST, PUT, DELETE, OPTIONS");

            // Headers React (and Gson) will send.
            response.setHeader("Access-Control-Allow-Headers",
                    "Content-Type, Authorization, X-Requested-With, Accept");

            // Tell the browser it can cache the preflight result for 1 hour,
            // reducing the number of preflight round-trips.
            response.setHeader("Access-Control-Max-Age", "3600");
        }

        /*
         * OPTIONS is the browser's preflight request.
         * It must be answered immediately with 200 — do not pass it down the
         * filter chain, as servlet logic should not run on a preflight.
         */
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException { }

    @Override
    public void destroy() { }
}