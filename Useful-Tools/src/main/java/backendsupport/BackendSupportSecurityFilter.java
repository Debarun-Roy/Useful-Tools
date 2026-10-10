package backendsupport;

import java.io.IOException;
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
import jakarta.servlet.http.HttpSession;

/** Explicitly registered after AuthFilter, before legacy CSRF in web.xml. */
public final class BackendSupportSecurityFilter implements Filter {
    private Set<String> origins;
    @Override public void init(FilterConfig config) {
        origins = AppConfig.getCsvSet("cors_allowed_origins", Set.of("http://localhost:3000", "http://localhost:5173"));
    }
    @Override public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        response.setHeader("Cache-Control", "no-store");
        if (!request.getMethod().equals("POST")) {
            chain.doFilter(req,res);
            return;
        }
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("username") == null) {
            Responses.fail(response,401,"UNAUTHENTICATED");
            return;
        }
        synchronized (session) {
            long now = System.currentTimeMillis();
            long[] window = (long[]) session.getAttribute("backendSupportRate");
            if (window == null || now - window[0] >= 60000) {
                window = new long[]{now,0};
                session.setAttribute("backendSupportRate",window);
            }
            if (++window[1] > 40) {
                response.setHeader("Retry-After","60");
                Responses.fail(response,429,"RATE_LIMITED");
                return;
            }
        }
        Object stored = session.getAttribute("csrfToken");
        String supplied = request.getHeader("X-XSRF-TOKEN");
        if (!(stored instanceof String token) || token.isBlank() || !token.equals(supplied)) {
            Responses.fail(response,403,"CSRF_INVALID");
            return;
        }
        String origin = request.getHeader("Origin");
        int port = request.getServerPort();
        String ownOrigin = request.getScheme() + "://" + request.getServerName()
                + ((request.isSecure() && port == 443 || !request.isSecure() && port == 80) ? "" : ":" + port);
        if (origin == null || !(origin.equals(ownOrigin) || origins.contains(origin))) {
            Responses.fail(response,403,"ORIGIN_REJECTED");
            return;
        }
        chain.doFilter(req,res);
    }
}
