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
                System.out.println("[SameSiteFilter] ▶ Intercepting " + method + " " + uri);
                System.out.flush();
            }
            
            // Wrap the response
            HttpServletResponse wrappedResponse = new SameSiteCookieWrapper(httpResponse);

            try {
                chain.doFilter(request, wrappedResponse);
            } finally {
                System.out.println("[SameSiteFilter] ◀ Response chain completed");
                System.out.flush();
                
                try {
                    java.util.Collection<String> headers = wrappedResponse.getHeaderNames();
                    if (headers != null && !headers.isEmpty()) {
                        java.util.Collection<String> cookieHeaders = wrappedResponse.getHeaders("Set-Cookie");
                        if (cookieHeaders != null && !cookieHeaders.isEmpty()) {
                            System.out.println("[SameSiteFilter] ✓ Final Set-Cookie headers (" 
                                + cookieHeaders.size() + " total):");
                            System.out.flush();
                            for (String header : cookieHeaders) {
                                System.out.println("    └─ " + header);
                                System.out.flush();
                            }
                        } else {
                            System.out.println("[SameSiteFilter] ✗ No Set-Cookie headers found");
                            System.out.flush();
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[SameSiteFilter] ✗ Error reading headers: " + e.getMessage());
                    e.printStackTrace();
                    System.out.flush();
                }
            }
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
            System.out.println("[SameSiteCookieWrapper] ▶ sendError() called");
            System.out.flush();
            super.sendError(sc);
        }

        /**
         * Override sendError with message
         */
        @Override
        public void sendError(int sc, String msg) throws IOException {
            System.out.println("[SameSiteCookieWrapper] ▶ sendError() with message called");
            System.out.flush();
            super.sendError(sc, msg);
        }

        /**
         * Override sendRedirect to catch potential cookie additions
         */
        @Override
        public void sendRedirect(String location) throws IOException {
            System.out.println("[SameSiteCookieWrapper] ▶ sendRedirect() called");
            System.out.flush();
            super.sendRedirect(location);
        }

        /**
         * Intercept addCookie() to add SameSite=None
         */
        @Override
        public void addCookie(Cookie cookie) {
            System.out.println("[SameSiteCookieWrapper] ▶ addCookie() called");
            System.out.println("[SameSiteCookieWrapper]   → Name: " + cookie.getName());
            System.out.println("[SameSiteCookieWrapper]   → Value: " + (cookie.getValue() != null ? cookie.getValue().substring(0, Math.min(20, cookie.getValue().length())) + "..." : "null"));
            System.out.flush();
            
            // CRITICAL: Detect duplicate JSESSIONID
            if ("JSESSIONID".equals(cookie.getName())) {
                if (jsessionidHandled) {
                    System.out.println("[SameSiteCookieWrapper]   ⚠ SKIPPING: This is Tomcat's duplicate JSESSIONID");
                    System.out.println("[SameSiteCookieWrapper]      (We already have one with SameSite=None from LoginController)");
                    System.out.flush();
                    return; // Don't add this duplicate!
                } else {
                    jsessionidHandled = true;
                    System.out.println("[SameSiteCookieWrapper]   ✓ This is the JSESSIONID from LoginController - marking as handled");
                    System.out.flush();
                }
            }
            
            // Add SameSite=None to all cookies
            cookie.setAttribute("SameSite", "None");
            cookie.setSecure(true);
            
            System.out.println("[SameSiteCookieWrapper]   ✓ Set SameSite=None, Secure=true");
            System.out.flush();
            
            super.addCookie(cookie);
        }

        /**
         * Intercept addHeader() for Set-Cookie headers
         */
        @Override
        public void addHeader(String name, String value) {
            if ("Set-Cookie".equalsIgnoreCase(name)) {
                System.out.println("[SameSiteCookieWrapper] ▶ addHeader('Set-Cookie')");
                System.out.println("[SameSiteCookieWrapper]   Before: " + value.substring(0, Math.min(60, value.length())));
                System.out.flush();
                
                // Check for duplicate JSESSIONID via header
                if (value.contains("JSESSIONID=")) {
                    if (jsessionidHandled) {
                        System.out.println("[SameSiteCookieWrapper]   ⚠ SKIPPING: Duplicate JSESSIONID via addHeader");
                        System.out.flush();
                        return; // Don't add this header!
                    }
                    jsessionidHandled = true;
                }
                
                String original = value;
                value = enhanceSetCookieHeader(value);
                
                if (!original.equals(value)) {
                    System.out.println("[SameSiteCookieWrapper]   After:  " + value.substring(0, Math.min(80, value.length())) + (value.length() > 80 ? "..." : ""));
                    System.out.flush();
                }
            }
            super.addHeader(name, value);
        }

        /**
         * Intercept setHeader() for Set-Cookie headers
         */
        @Override
        public void setHeader(String name, String value) {
            if ("Set-Cookie".equalsIgnoreCase(name)) {
                System.out.println("[SameSiteCookieWrapper] ▶ setHeader('Set-Cookie')");
                
                // Check for duplicate JSESSIONID via header
                if (value.contains("JSESSIONID=")) {
                    if (jsessionidHandled) {
                        System.out.println("[SameSiteCookieWrapper]   ⚠ SKIPPING: Duplicate JSESSIONID via setHeader");
                        System.out.flush();
                        return; // Don't set this header!
                    }
                    jsessionidHandled = true;
                }
                
                String original = value;
                value = enhanceSetCookieHeader(value);
                if (!original.equals(value)) {
                    System.out.println("[SameSiteCookieWrapper]   Enhanced with SameSite=None");
                    System.out.flush();
                }
            }
            super.setHeader(name, value);
        }



        /**
         * Add SameSite=None to Set-Cookie header if missing
         */
        private String enhanceSetCookieHeader(String cookieValue) {
            if (cookieValue == null || cookieValue.toLowerCase().contains("samesite")) {
                return cookieValue;
            }
            return cookieValue + SAMESITE_NONE;
        }
    }
}

