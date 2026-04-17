package common.filter;

import java.io.IOException;
import java.util.Set;

import com.google.gson.Gson;

import common.ApiResponse;
import common.UserContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class AuthFilter implements Filter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/login-guest",
            "/api/auth/register"
    );

    private final Gson gson = new Gson();

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getServletPath();

        if (PUBLIC_PATHS.contains(path)) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        HttpSession session = request.getSession(false);
        String username     = (session != null)
                ? (String) session.getAttribute("username")
                : null;

        if (username != null && !username.isBlank()) {
            // Make the authenticated username available to the entire call chain
            // (DAOs, services) without threading it through every method signature.
            UserContext.set(username);
            try {
                chain.doFilter(servletRequest, servletResponse);
            } finally {
                // Always clear — prevents ThreadLocal leaking between requests
                // on pooled servlet threads.
                UserContext.clear();
            }
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(gson.toJson(
                ApiResponse.fail(
                        "You must be logged in to access this resource.",
                        "UNAUTHENTICATED")));
    }

    @Override public void init(FilterConfig fc) throws ServletException { }
    @Override public void destroy() { }
}