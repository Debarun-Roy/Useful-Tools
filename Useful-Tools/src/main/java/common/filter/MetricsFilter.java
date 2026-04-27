package common.filter;

import java.io.IOException;

import common.UserContext;
import common.dao.MetricsDAO;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * MetricsFilter — Sprint 18.
 *
 * Auto-instruments server-side tool endpoints by wrapping each request with
 * a timer and success/failure check, then writing one row to tool_metrics
 * via {@link MetricsDAO#log}.
 *
 * ── Filter chain position ────────────────────────────────────────────────
 * Placed LAST in the chain in web.xml — after SameSite, CORS, RateLimit,
 * Auth, GuestRestriction, Admin, and CSRF. That means:
 *
 *   • We only record metrics for requests that actually reached the servlet
 *     (i.e. the user was authenticated, not a restricted guest, passed CSRF,
 *     and either wasn't trying to hit /api/admin/* or was an admin).
 *
 *   • Pre-auth rejections (401 from AuthFilter, 429 from RateLimitFilter,
 *     403 from Guest/Admin filters, 403 from CsrfFilter) do NOT generate
 *     metric rows. We're measuring real tool usage, not failed auth noise.
 *
 *   • Because we sit after CsrfFilter, the request body is guaranteed to have
 *     been CSRF-validated before we start the timer. There's no risk of
 *     counting an aborted request toward tool latency.
 *
 * ── What gets instrumented ───────────────────────────────────────────────
 * Only paths with a mapping in {@link #resolveToolName(String)} are recorded.
 * Anything else (auth, favorites, activity logging, admin endpoints, metrics
 * endpoints themselves, history reads) is ignored — the filter still calls
 * chain.doFilter() but writes nothing.
 *
 * Instrumenting arbitrary paths would flood the table with noise and make
 * the rankings meaningless. The explicit allow-list keeps the signal clean.
 *
 * ── Memory measurement ───────────────────────────────────────────────────
 * We sample (totalMemory - freeMemory) before and after chain.doFilter().
 * Between the two samples, other threads in the JVM can allocate and free
 * heap memory, so the delta is NOT a precise measurement of what this
 * request allocated. It's a coarse proxy that's meaningful only in
 * aggregate across many samples. See MetricsDAO's file header.
 *
 * ── Self-instrumentation ─────────────────────────────────────────────────
 * This filter never records metrics for /api/metrics/log (the endpoint that
 * the client-side tools use to report their own metrics) — it's a meta
 * endpoint, not a tool, and including it would be a feedback loop.
 *
 * Registered in web.xml — do NOT add @WebFilter here.
 */
public class MetricsFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getServletPath();
        String toolName = resolveToolName(path);

        if (toolName == null) {
            // Not a server-side tool endpoint — pass through without timing.
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        // Sample memory and time before the operation.
        long   startNs    = System.nanoTime();
        Runtime rt        = Runtime.getRuntime();
        long   memBefore  = rt.totalMemory() - rt.freeMemory();

        // Track whether the servlet itself threw, for accurate success flag.
        boolean servletThrew = false;
        try {
            chain.doFilter(servletRequest, servletResponse);
        } catch (RuntimeException | IOException | ServletException e) {
            servletThrew = true;
            throw e;  // never swallow; let the container log it
        } finally {
            // Compute metrics and log — wrapped in try/catch so a metrics
            // failure never surfaces to the user.
            try {
                long   endNs       = System.nanoTime();
                long   execMs      = (endNs - startNs) / 1_000_000L;
                long   memAfter    = rt.totalMemory() - rt.freeMemory();
                Long   memDelta    = memAfter - memBefore;

                int    status      = response.getStatus();
                // 2xx/3xx count as success; 4xx/5xx as failure.
                // servletThrew overrides — thrown exceptions are always failures.
                boolean success    = !servletThrew && status >= 200 && status < 400;

                String errorCode   = null;
                if (!success) {
                    errorCode = servletThrew ? "SERVLET_EXCEPTION"
                                             : "HTTP_" + status;
                }

                String username = UserContext.get();
                // AuthFilter clears UserContext in its finally block BEFORE we
                // reach this finally (nested filter finallies unwind inward
                // first). Fall back to the session-bound value.
                if (username == null || username.isBlank()) {
                    username = readUsernameFromSession(request);
                }
                // Still null on public paths (shouldn't happen for mapped tools,
                // but defence in depth). Skip writing in that case.
                if (username == null || username.isBlank()) return;

                // Server-side latency == execution time for our simple model.
                // Round-trip network time is indistinguishable from compute
                // here; if we later add client-side latency, that would be
                // a richer metric.
                MetricsDAO.log(
                        username,
                        toolName,
                        execMs,
                        memDelta,
                        execMs,              // latency_ms = execMs for server-side
                        success,
                        errorCode);
            } catch (Throwable t) {
                // Metrics are best-effort. A failure in the metrics path
                // must never leak into the user-visible response.
                t.printStackTrace();
            }
        }
    }

    /**
     * Walk order matters here — equality checks before prefix checks, and
     * longer prefixes before shorter ones. Read-only history endpoints are
     * explicitly returned as null so we don't count list reads as tool
     * invocations.
     */
    private static String resolveToolName(String path) {
        if (path == null) return null;

        // Skip read-only history / listing endpoints.
        if (path.equals("/api/calculator/history"))           return null;
        if (path.equals("/api/calculator/financial-history")) return null;
        if (path.equals("/api/passwords/generated-history"))  return null;
        if (path.equals("/api/passwords/export"))             return null;
        // Don't self-instrument the metrics log endpoint.
        if (path.equals("/api/metrics/log"))                  return null;

        // ── Vault ───────────────────────────────────────────────────────────
        if (path.equals("/api/passwords/generate"))           return "password.generate";
        if (path.equals("/api/passwords/save"))               return "password.save";
        if (path.equals("/api/passwords/update"))             return "password.save";
        if (path.equals("/api/passwords/fetch"))              return "password.fetch";
        if (path.equals("/api/passwords/delete"))             return "password.fetch";

        // ── Web Dev ─────────────────────────────────────────────────────────
        if (path.equals("/api/webdev/request-headers"))       return "webdev.headers";

        // ── Number Analyser ─────────────────────────────────────────────────
        if (path.startsWith("/api/analyzer/base-"))           return "analyzer.base";
        if (path.startsWith("/api/analyzer/series"))          return "analyzer.series";
        if (path.startsWith("/api/analyzer/"))                return "analyzer.classify";

        // ── Calculator ──────────────────────────────────────────────────────
        if (path.equals("/api/calculator/compound-interest")) return "calculator.financial";
        if (path.equals("/api/calculator/emi"))               return "calculator.financial";
        if (path.equals("/api/calculator/tax"))               return "calculator.financial";
        if (path.equals("/api/calculator/salary"))            return "calculator.financial";
        if (path.equals("/api/calculator/probability"))       return "calculator.probability";
        if (path.startsWith("/api/calculator/"))              return "calculator.standard";

        return null;
    }

    /**
     * Fallback lookup for the authenticated username when UserContext has
     * already been cleared by the time this filter's finally block runs.
     * AuthFilter's ThreadLocal clear is nested inside the outer chain.doFilter
     * call, so the order of finally unwinding depends on filter nesting —
     * this is a belt-and-braces safeguard.
     */
    private static String readUsernameFromSession(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) return null;
        Object u = session.getAttribute("username");
        return (u instanceof String s) ? s : null;
    }

    @Override public void init(FilterConfig fc) throws ServletException { }
    @Override public void destroy() { }
}
