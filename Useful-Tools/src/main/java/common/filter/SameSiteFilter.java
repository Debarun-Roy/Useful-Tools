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
 *   recognized (warning: "failed to set property [sameSiteCookies] to [none]").
 *   As a result, JSESSIONID cookies don't get SameSite=None, and the browser
 *   won't send them on cross-origin requests (Vercel → Railway).
 *
 * SOLUTION:
 *   This filter intercepts ALL cookie operations (both addCookie() AND
 *   Set-Cookie headers) and ensures each cookie gets "; SameSite=None".
 *   This ensures:
 *   1. JSESSIONID gets SameSite=None (allows cross-origin transmission)
 *   2. XSRF-TOKEN gets SameSite=None (for CSRF double-submit pattern)
 *   3. Any other session cookies also get it (future-proof)
 *
 * IMPORTANT:
 *   SameSite=None requires Secure flag. Web.xml already sets
 *   <secure>true</secure> for the session cookie, and Railway serves HTTPS.
 *   The cookies will already have Secure=true, so we just append SameSite=None.
 *
 * FILTER ORDER:
 *   This runs FIRST on all requests (/* mapping in web.xml).
 *   Runs BEFORE CorsFilter to ensure all responses are modified.
 */
@WebFilter(filterName = "SameSiteFilter", urlPatterns = "/*")
public class SameSiteFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("SameSiteFilter: INITIALIZED (Tomcat 11 cookie fix active)");
        System.out.println("  └─ This filter adds SameSite=None to all cookies for cross-origin");
        System.out.println("═══════════════════════════════════════════════════════════════");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain)
            throws IOException, ServletException {

        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            
            // Log the incoming request
            if (request instanceof jakarta.servlet.http.HttpServletRequest) {
                String method = ((jakarta.servlet.http.HttpServletRequest) request).getMethod();
                String uri = ((jakarta.servlet.http.HttpServletRequest) request).getRequestURI();
                System.out.println("[SameSiteFilter] Intercepting " + method + " " + uri);
            }
            
            // Wrap the response to intercept cookie operations
            HttpServletResponse wrappedResponse = new SameSiteCookieWrapper(httpResponse);

            try {
                chain.doFilter(request, wrappedResponse);
            } finally {
                // After chain completes, check what headers were set in the wrapper
                System.out.println("[SameSiteFilter] After chain - checking response headers");
                
                // Get all headers from the wrapped response
                java.util.Collection<String> headers = wrappedResponse.getHeaderNames();
                if (headers != null && !headers.isEmpty()) {
                    System.out.println("[SameSiteFilter] Response has " + headers.size() 
                        + " header types: " + headers);
                    
                    // Check specifically for Set-Cookie
                    java.util.Collection<String> cookieHeaders = wrappedResponse.getHeaders("Set-Cookie");
                    if (cookieHeaders != null && !cookieHeaders.isEmpty()) {
                        System.out.println("[SameSiteFilter] Final Set-Cookie headers in response (" 
                            + cookieHeaders.size() + " total):");
                        for (String header : cookieHeaders) {
                            System.out.println("  └─ " + header);
                        }
                    } else {
                        System.out.println("[SameSiteFilter] No Set-Cookie headers in response");
                    }
                } else {
                    System.out.println("[SameSiteFilter] Response has no headers set");
                }
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }

    /**
     * Custom response wrapper that adds SameSite=None to all cookies.
     * Intercepts BOTH addCookie() method calls AND Set-Cookie headers.
     */
    private static class SameSiteCookieWrapper extends HttpServletResponseWrapper {
        
        private static final String SAMESITE_NONE = "; SameSite=None";
        private java.util.List<String> allSetCookieHeaders = new java.util.ArrayList<>();
        private boolean debugLogging = true; // Enable verbose logging

        public SameSiteCookieWrapper(HttpServletResponse response) {
            super(response);
        }

        /**
         * Intercept addCookie() calls (the primary method Tomcat uses).
         * This is called when servlets use response.addCookie(cookie).
         * We modify the cookie's attributes BEFORE it's sent.
         */
        @Override
        public void addCookie(Cookie cookie) {
            if (debugLogging) {
                System.out.println("[SameSiteCookieWrapper] ▶ addCookie() called");
                System.out.println("[SameSiteCookieWrapper]   Name: " + cookie.getName());
                System.out.println("[SameSiteCookieWrapper]   Value: " + cookie.getValue());
                System.out.println("[SameSiteCookieWrapper]   Before: Secure=" + cookie.getSecure() 
                    + ", HttpOnly=" + cookie.isHttpOnly());
            }
            
            // Set the cookie to be sent on cross-origin requests
            cookie.setAttribute("SameSite", "None");
            // SameSite=None requires Secure flag
            cookie.setSecure(true);
            
            if (debugLogging) {
                System.out.println("[SameSiteCookieWrapper]   After: Added SameSite=None, Secure=true");
            }
            
            super.addCookie(cookie);
        }

        /**
         * Intercept addHeader() calls for Set-Cookie headers.
         * This catches any direct Set-Cookie header manipulations.
         */
        @Override
        public void addHeader(String name, String value) {
            if ("Set-Cookie".equalsIgnoreCase(name)) {
                if (debugLogging) {
                    System.out.println("[SameSiteCookieWrapper] ▶ addHeader(Set-Cookie) called");
                    System.out.println("[SameSiteCookieWrapper]   Before: " + value);
                }
                
                String original = value;
                value = enhanceSetCookieHeader(value);
                allSetCookieHeaders.add(value);
                
                if (!original.equals(value) && debugLogging) {
                    System.out.println("[SameSiteCookieWrapper]   After:  " + value);
                }
            }
            super.addHeader(name, value);
        }

        /**
         * Intercept setHeader() calls for Set-Cookie headers.
         * This catches any Set-Cookie header replacements.
         */
        @Override
        public void setHeader(String name, String value) {
            if ("Set-Cookie".equalsIgnoreCase(name)) {
                if (debugLogging) {
                    System.out.println("[SameSiteCookieWrapper] ▶ setHeader(Set-Cookie) called");
                    System.out.println("[SameSiteCookieWrapper]   Value: " + value);
                }
                
                String original = value;
                value = enhanceSetCookieHeader(value);
                
                if (!original.equals(value) && debugLogging) {
                    System.out.println("[SameSiteCookieWrapper]   Modified to: " + value);
                }
            }
            super.setHeader(name, value);
        }

        /**
         * Intercept flushBuffer() to detect when response is committed
         */
        @Override
        public void flushBuffer() throws IOException {
            if (debugLogging) {
                System.out.println("[SameSiteCookieWrapper] ▶ flushBuffer() called - response being sent");
            }
            super.flushBuffer();
        }

        /**
         * Enhances a Set-Cookie header string by appending SameSite=None if missing.
         * 
         * Examples:
         *   "JSESSIONID=abc123; Path=/; Secure; HttpOnly"
         *   → "JSESSIONID=abc123; Path=/; Secure; HttpOnly; SameSite=None"
         *
         * @param cookieValue The Set-Cookie header value
         * @return The enhanced value with SameSite=None appended (if not already present)
         */
        private String enhanceSetCookieHeader(String cookieValue) {
            if (cookieValue == null) {
                return cookieValue;
            }

            // Check if SameSite is already present
            if (cookieValue.toLowerCase().contains("samesite")) {
                return cookieValue;
            }

            // Add SameSite=None to the Set-Cookie header
            // Note: Secure flag should already be present from web.xml configuration
            return cookieValue + SAMESITE_NONE;
        }
    }
}

