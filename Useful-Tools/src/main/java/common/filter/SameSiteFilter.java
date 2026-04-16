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
            
            // Wrap the response to intercept cookie operations
            HttpServletResponse wrappedResponse = new SameSiteCookieWrapper(httpResponse);

            try {
                chain.doFilter(request, wrappedResponse);
            } finally {
                // After chain completes, check what headers were set via the wrapper
                java.util.Collection<String> headers = wrappedResponse.getHeaders("Set-Cookie");
                if (headers != null && !headers.isEmpty()) {
                    System.out.println("[SameSiteFilter] Final Set-Cookie headers in response:");
                    for (String header : headers) {
                        System.out.println("  └─ " + header);
                    }
                } else {
                    System.out.println("[SameSiteFilter] No Set-Cookie headers found (session might be set at Tomcat level)");
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
            System.out.println("[SameSiteFilter] addCookie() intercepted: " + cookie.getName() 
                + " = " + cookie.getValue());
            // Set the cookie to be sent on cross-origin requests
            cookie.setAttribute("SameSite", "None");
            // SameSite=None requires Secure flag
            cookie.setSecure(true);
            System.out.println("  └─ Modified to include: SameSite=None, Secure=true");
            super.addCookie(cookie);
        }

        /**
         * Intercept addHeader() calls for Set-Cookie headers.
         * This catches any direct Set-Cookie header manipulations.
         */
        @Override
        public void addHeader(String name, String value) {
            if ("Set-Cookie".equalsIgnoreCase(name)) {
                String original = value;
                value = enhanceSetCookieHeader(value);
                allSetCookieHeaders.add(value);
                if (!original.equals(value)) {
                    System.out.println("[SameSiteFilter] addHeader(Set-Cookie): Enhanced header");
                    System.out.println("  ├─ Before: " + original);
                    System.out.println("  └─ After:  " + value);
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
                String original = value;
                value = enhanceSetCookieHeader(value);
                if (!original.equals(value)) {
                    System.out.println("[SameSiteFilter] setHeader(Set-Cookie): Enhanced header");
                    System.out.println("  ├─ Before: " + original);
                    System.out.println("  └─ After:  " + value);
                }
            }
            super.setHeader(name, value);
        }

        /**
         * Override containsHeader() for Set-Cookie to ensure our interception works
         */
        @Override
        public boolean containsHeader(String name) {
            return super.containsHeader(name);
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

