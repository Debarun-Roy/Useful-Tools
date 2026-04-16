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
        private boolean debugLogging = true;
        private boolean jsessionidHandled = false; // Track if we've already handled JSESSIONID

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
         * Override getHeaders() to filter duplicate JSESSIONID cookies
         * We'll keep only the one WITH SameSite=None
         */
        @Override
        public java.util.Collection<String> getHeaders(String name) {
            if (!"Set-Cookie".equalsIgnoreCase(name)) {
                return super.getHeaders(name);
            }
            
            java.util.Collection<String> originalCookies = super.getHeaders(name);
            if (originalCookies == null || originalCookies.isEmpty()) {
                return originalCookies;
            }
            
            // Check if we have duplicate JSESSIONID cookies
            java.util.List<String> jsessionidCookies = new java.util.ArrayList<>();
            java.util.List<String> otherCookies = new java.util.ArrayList<>();
            
            for (String cookie : originalCookies) {
                if (cookie.contains("JSESSIONID=")) {
                    jsessionidCookies.add(cookie);
                } else {
                    otherCookies.add(cookie);
                }
            }
            
            // If we have multiple JSESSIONID cookies, keep only the one with SameSite=None
            if (jsessionidCookies.size() > 1) {
                System.out.println("[SameSiteCookieWrapper] ▶ getHeaders('Set-Cookie'): Found " + jsessionidCookies.size() + " JSESSIONID cookies");
                
                String jsessionidWithSameSite = null;
                String jsessionidWithoutSameSite = null;
                
                for (String cookie : jsessionidCookies) {
                    if (cookie.toLowerCase().contains("samesite=none")) {
                        jsessionidWithSameSite = cookie;
                        System.out.println("[SameSiteCookieWrapper]   → Cookie WITH SameSite=None: " + cookie.substring(0, Math.min(50, cookie.length())));
                    } else {
                        jsessionidWithoutSameSite = cookie;
                        System.out.println("[SameSiteCookieWrapper]   ⚠ Cookie WITHOUT SameSite: " + cookie.substring(0, Math.min(50, cookie.length())));
                    }
                }
                System.out.flush();
                
                // Keep only the one with SameSite=None
                if (jsessionidWithSameSite != null) {
                    otherCookies.add(jsessionidWithSameSite);
                    System.out.println("[SameSiteCookieWrapper]   ✓ Kept JSESSIONID with SameSite=None, removed duplicate");
                    System.out.flush();
                } else if (jsessionidWithoutSameSite != null) {
                    // No SameSite version found, add the one without (not ideal but better than nothing)
                    otherCookies.add(jsessionidWithoutSameSite);
                    System.out.println("[SameSiteCookieWrapper]   ⚠ No JSESSIONID with SameSite found, keeping first");
                    System.out.flush();
                }
                
                return otherCookies;
            }
            
            return originalCookies;
        }

        /**
         * Override flushBuffer to ensure we've handled all cookies
         */
        @Override
        public void flushBuffer() throws IOException {
            System.out.println("[SameSiteCookieWrapper] ▶ flushBuffer()");
            
            try {
                java.util.Collection<String> cookies = super.getHeaders("Set-Cookie");
                if (cookies != null) {
                    System.out.println("[SameSiteCookieWrapper]   Total Set-Cookie headers: " + cookies.size());
                    int jsessionidCount = 0;
                    for (String cookie : cookies) {
                        if (cookie.contains("JSESSIONID=")) {
                            jsessionidCount++;
                            System.out.println("[SameSiteCookieWrapper]   - JSESSIONID: " + 
                                (cookie.toLowerCase().contains("samesite") ? "✓ HAS SameSite" : "⚠ NO SameSite"));
                        }
                    }
                    if (jsessionidCount != 1) {
                        System.out.println("[SameSiteCookieWrapper]   ⚠ WARNING: Expected 1 JSESSIONID but found " + jsessionidCount);
                    }
                }
                System.out.flush();
            } catch (Exception e) {
                System.out.println("[SameSiteCookieWrapper]   Error in flushBuffer: " + e.getMessage());
                System.out.flush();
            }
            
            super.flushBuffer();
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

