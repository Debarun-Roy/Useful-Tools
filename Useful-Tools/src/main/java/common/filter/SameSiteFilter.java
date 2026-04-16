package common.filter;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
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
 *   This filter intercepts Set-Cookie headers and appends "; SameSite=None"
 *   to any cookies that need it. This ensures:
 *   1. JSESSIONID gets SameSite=None (allows cross-origin transmission)
 *   2. XSRF-TOKEN already has it (LoginController sets it explicitly)
 *   3. Any other session cookies also get it (future-proof)
 *
 * IMPORTANT:
 *   SameSite=None requires Secure flag. Web.xml already sets
 *   <secure>true</secure> for the session cookie, and Railway serves HTTPS.
 *   The cookie will already have "; Secure" so we just append "; SameSite=None".
 *
 * FILTER ORDER:
 *   This must run BEFORE CorsFilter (to avoid double-processing).
 *   Mapped to /* so it processes all response headers.
 */
@WebFilter(filterName = "SameSiteFilter", urlPatterns = "/*")
public class SameSiteFilter implements Filter {

    private static final String SAMESITE_NONE = "; SameSite=None";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No initialization needed
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain)
            throws IOException, ServletException {

        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            
            // Wrap the response to intercept Set-Cookie headers
            HttpServletResponse wrappedResponse = new HttpServletResponseWrapper(httpResponse) {
                @Override
                public void addHeader(String name, String value) {
                    if ("Set-Cookie".equalsIgnoreCase(name)) {
                        value = addSameSiteNone(value);
                    }
                    super.addHeader(name, value);
                }

                @Override
                public void setHeader(String name, String value) {
                    if ("Set-Cookie".equalsIgnoreCase(name)) {
                        value = addSameSiteNone(value);
                    }
                    super.setHeader(name, value);
                }
            };

            chain.doFilter(request, wrappedResponse);
        } else {
            chain.doFilter(request, response);
        }
    }

    /**
     * Adds SameSite=None to a Set-Cookie header value if not already present.
     * 
     * Examples:
     *   "JSESSIONID=abc123; Path=/; Secure; HttpOnly"
     *   → "JSESSIONID=abc123; Path=/; Secure; HttpOnly; SameSite=None"
     *
     * @param cookieValue The Set-Cookie header value
     * @return The modified value with SameSite=None appended (if not already present)
     */
    private String addSameSiteNone(String cookieValue) {
        if (cookieValue == null) {
            return cookieValue;
        }

        // Don't add SameSite=None if it's already there
        if (cookieValue.toLowerCase().contains("samesite")) {
            return cookieValue;
        }

        // Append SameSite=None (note: no leading semicolon, the existing cookie
        // value already has one or will get one from the preceding flags)
        return cookieValue + SAMESITE_NONE;
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }
}
