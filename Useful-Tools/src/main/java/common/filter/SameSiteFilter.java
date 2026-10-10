package common.filter;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * SameSiteFilter — Adds SameSite=None attribute to session and CSRF cookies.
 *
 * PROBLEM:
 *   In Tomcat 11.0.21, the context.xml attribute "sameSiteCookies" is not
 *   recognized. JSESSIONID cookies are sent without SameSite=None and won't
 *   be transmitted on cross-origin requests (Vercel → Railway).
 *
 * SOLUTION:
 *   Wrap response to intercept ALL cookie operations (addCookie, addHeader,
 *   setHeader) and ensure every cookie gets SameSite=None appended.
 *   
 *   Strategy:
 *   1. Don't manually add JSESSIONID in servlet code
 *   2. Let Tomcat add it automatically when getSession() is called
 *   3. Wrapper intercepts Tomcat's addHeader("Set-Cookie",...) call
 *   4. We enhance the header to include SameSite=None
 */
@WebFilter(filterName = "SameSiteFilter", urlPatterns = "/*")
public class SameSiteFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain)
            throws IOException, ServletException {

        if (common.AppConfig.isLocal() && request instanceof jakarta.servlet.http.HttpServletRequest localRequest) {
            String origin = localRequest.getHeader("Origin");
            boolean loopback = java.net.InetAddress.getByName(localRequest.getRemoteAddr()).isLoopbackAddress();
            boolean allowedOrigin = origin == null || origin.equals("http://localhost:5173") || origin.equals("http://localhost:8080");
            boolean unsafe = !java.util.Set.of("GET", "HEAD", "OPTIONS").contains(localRequest.getMethod());
            if (!loopback || !"localhost".equals(localRequest.getServerName()) || !allowedOrigin || unsafe && origin == null) {
                ((HttpServletResponse) response).sendError(403, "Local requests require localhost and an explicit local Origin");
                return;
            }
        }
        if (request instanceof jakarta.servlet.http.HttpServletRequest req && req.getServletPath().startsWith("/api/backend-support/")) {
            ((HttpServletResponse) response).setHeader("Cache-Control", "no-store");
            chain.doFilter(request, response);
            return;
        }
        if (response instanceof HttpServletResponse httpResponse) {
            chain.doFilter(request, new SameSiteCookieWrapper(httpResponse));
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
    }

    /**
     * Response wrapper that adds SameSite=None to all cookies
     */
    private static class SameSiteCookieWrapper extends HttpServletResponseWrapper {
        
        private static final String SAMESITE_NONE = "; SameSite=None";
        private boolean jsessionidHandled = false;

        public SameSiteCookieWrapper(HttpServletResponse response) {
            super(response);
        }

        /**
         * Override sendError to catch JSESSIONID additions
         */
        @Override
        public void sendError(int sc) throws IOException {
            super.sendError(sc);
        }

        /**
         * Override sendError with message
         */
        @Override
        public void sendError(int sc, String msg) throws IOException {
            super.sendError(sc, msg);
        }

        /**
         * Override sendRedirect to catch potential cookie additions
         */
        @Override
        public void sendRedirect(String location) throws IOException {
            super.sendRedirect(location);
        }

        /**
         * Intercept addCookie() to add SameSite=None
         */
        @Override
        public void addCookie(Cookie cookie) {
            
            // CRITICAL: Detect duplicate JSESSIONID
            if ("JSESSIONID".equals(cookie.getName())) {
                if (jsessionidHandled) {
                    return; // Don't add this duplicate!
                } else {
                    jsessionidHandled = true;
                }
            }
            
            // Add SameSite=None to all cookies
            cookie.setAttribute("SameSite", common.AppConfig.isLocal() ? "Lax" : "None");
            cookie.setSecure(!common.AppConfig.isLocal());
            
            
            super.addCookie(cookie);
        }

        /**
         * Intercept addHeader() for Set-Cookie headers
         */
        @Override
        public void addHeader(String name, String value) {
            if ("Set-Cookie".equalsIgnoreCase(name)) {
                
                // Check for duplicate JSESSIONID via header
                if (value.contains("JSESSIONID=")) {
                    if (jsessionidHandled) {
                        return; // Don't add this header!
                    }
                    jsessionidHandled = true;
                }
                
                value = enhanceSetCookieHeader(value);
                
            }
            super.addHeader(name, value);
        }

        /**
         * Intercept setHeader() for Set-Cookie headers
         */
        @Override
        public void setHeader(String name, String value) {
            if ("Set-Cookie".equalsIgnoreCase(name)) {
                
                // Check for duplicate JSESSIONID via header
                if (value.contains("JSESSIONID=")) {
                    if (jsessionidHandled) {
                        return; // Don't set this header!
                    }
                    jsessionidHandled = true;
                }
                
                value = enhanceSetCookieHeader(value);
            }
            super.setHeader(name, value);
        }



        /**
         * Add SameSite=None to Set-Cookie header if missing
         */
        private String enhanceSetCookieHeader(String cookieValue) {
            if (cookieValue != null && common.AppConfig.isLocal()) {
                return common.LocalCookiePolicy.rewrite(cookieValue);
            }
            if (cookieValue == null || cookieValue.toLowerCase().contains("samesite")) {
                return cookieValue;
            }
            return cookieValue + SAMESITE_NONE;
        }
    }
}
