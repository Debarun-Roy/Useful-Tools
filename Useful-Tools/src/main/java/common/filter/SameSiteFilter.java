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
                System.out.println("[SameSiteFilter] Intercepting " + method + " " + uri);
            }
            
            // Wrap the response
            HttpServletResponse wrappedResponse = new SameSiteCookieWrapper(httpResponse);

            try {
                chain.doFilter(request, wrappedResponse);
            } finally {
                System.out.println("[SameSiteFilter] After chain - checking response headers");
                
                java.util.Collection<String> headers = wrappedResponse.getHeaderNames();
                if (headers != null && !headers.isEmpty()) {
                    java.util.Collection<String> cookieHeaders = wrappedResponse.getHeaders("Set-Cookie");
                    if (cookieHeaders != null && !cookieHeaders.isEmpty()) {
                        System.out.println("[SameSiteFilter] Final Set-Cookie headers (" 
                            + cookieHeaders.size() + " total):");
                        for (String header : cookieHeaders) {
                            System.out.println("  └─ " + header);
                        }
                    }
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

        public SameSiteCookieWrapper(HttpServletResponse response) {
            super(response);
        }

        /**
         * Intercept addCookie() to add SameSite=None
         */
        @Override
        public void addCookie(Cookie cookie) {
            if (debugLogging) {
                System.out.println("[SameSiteCookieWrapper] ▶ addCookie() called");
                System.out.println("[SameSiteCookieWrapper]   Name: " + cookie.getName());
            }
            
            // Add SameSite=None to all cookies
            cookie.setAttribute("SameSite", "None");
            cookie.setSecure(true);
            
            if (debugLogging) {
                System.out.println("[SameSiteCookieWrapper]   ✓ Set SameSite=None");
            }
            
            super.addCookie(cookie);
        }

        /**
         * Intercept addHeader() for Set-Cookie headers
         */
        @Override
        public void addHeader(String name, String value) {
            if ("Set-Cookie".equalsIgnoreCase(name)) {
                if (debugLogging && value.contains("JSESSIONID")) {
                    System.out.println("[SameSiteCookieWrapper] ▶ addHeader(Set-Cookie) for JSESSIONID");
                    System.out.println("[SameSiteCookieWrapper]   Before: " + value);
                }
                
                String original = value;
                value = enhanceSetCookieHeader(value);
                
                if (!original.equals(value) && debugLogging) {
                    System.out.println("[SameSiteCookieWrapper]   After:  " + value);
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
                String original = value;
                value = enhanceSetCookieHeader(value);
                if (!original.equals(value) && debugLogging && value.contains("JSESSIONID")) {
                    System.out.println("[SameSiteCookieWrapper] ▶ setHeader(Set-Cookie) - modified to add SameSite=None");
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

