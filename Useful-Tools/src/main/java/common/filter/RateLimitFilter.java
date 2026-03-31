package common.filter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import common.ApiResponse;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rate-limiting filter for authentication endpoints.
 *
 * Applied to /api/auth/* via web.xml (second in the filter chain, after
 * CorsFilter and before AuthFilter). Only POST requests are counted —
 * GET requests to /api/auth/* are passed through without throttling.
 *
 * Policy: max 10 POST requests per IP address per 60-second sliding window.
 * When the limit is exceeded the filter returns HTTP 429 (Too Many Requests)
 * with a Retry-After header indicating seconds until the window resets.
 *
 * The rate map is a process-local static ConcurrentHashMap. A 1-in-100
 * lazy sweep removes entries whose window has fully expired to bound memory
 * growth over long server uptimes with many unique IPs.
 *
 * The client IP is extracted from X-Forwarded-For first (to handle reverse
 * proxies in production), falling back to getRemoteAddr().
 */
public class RateLimitFilter implements Filter {

    private static final int  MAX_REQUESTS    = 10;
    private static final long WINDOW_MS       = 60_000L; // 1 minute

    private static final ConcurrentHashMap<String, RateLimitEntry> RATE_MAP
            = new ConcurrentHashMap<>();

    private final Gson gson = new Gson();

    private static final class RateLimitEntry {
        volatile long        windowStart = System.currentTimeMillis();
        final AtomicInteger  count       = new AtomicInteger(0);
    }

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // Only rate-limit POST (login, register, logout, update-password).
        // GET requests to /api/auth/* pass through unthrottled.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        // Lazy cleanup: ~1% of requests trigger a sweep of expired entries.
        if (Math.random() < 0.01) {
            long cutoff = System.currentTimeMillis() - WINDOW_MS;
            RATE_MAP.entrySet().removeIf(e -> e.getValue().windowStart < cutoff);
        }

        String ip  = extractClientIp(request);
        long   now = System.currentTimeMillis();

        RateLimitEntry entry = RATE_MAP.computeIfAbsent(ip, k -> new RateLimitEntry());

        // Synchronise on the entry to make window-reset + increment atomic.
        synchronized (entry) {
            if (now - entry.windowStart >= WINDOW_MS) {
                entry.windowStart = now;
                entry.count.set(0);
            }

            if (entry.count.incrementAndGet() > MAX_REQUESTS) {
                long retryAfterSec =
                        Math.max(1L, (WINDOW_MS - (now - entry.windowStart)) / 1000L);

                response.setStatus(429); // 429 Too Many Requests
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Retry-After", String.valueOf(retryAfterSec));
                response.getWriter().print(gson.toJson(ApiResponse.fail(
                        "Too many attempts. Please try again in "
                        + retryAfterSec + " seconds.",
                        "RATE_LIMITED")));
                return;
            }
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    /**
     * Extracts the real client IP, handling reverse proxies.
     * Takes the first (leftmost) address in X-Forwarded-For.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Override public void init(FilterConfig fc) throws ServletException { }
    @Override public void destroy() { }
}