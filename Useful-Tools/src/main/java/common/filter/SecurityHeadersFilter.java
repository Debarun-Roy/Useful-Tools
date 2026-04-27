package common.filter;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * SecurityHeadersFilter — sets defensive HTTP response headers on every
 * response served by the application.
 *
 * ── Why these specific headers ───────────────────────────────────────────
 *   X-Content-Type-Options: nosniff
 *       Stops the browser from "MIME sniffing" responses to a different
 *       content-type than the server declared. Without this, a JSON file
 *       containing well-formed HTML can be rendered as HTML and run
 *       embedded scripts.
 *
 *   X-Frame-Options: DENY
 *       Refuses to be embedded in <iframe>. Defends against clickjacking
 *       attacks where a hostile page overlays our app and tricks the user
 *       into clicking buttons they can't see.
 *
 *   Referrer-Policy: strict-origin-when-cross-origin
 *       When the user navigates away from our site, only the origin
 *       (https://usefultools-deba.vercel.app) is sent — never the path,
 *       which could leak resource ids, password-vault entries, or
 *       sensitive query strings.
 *
 *   Permissions-Policy
 *       Disables browser APIs the app does not use (camera, microphone,
 *       geolocation, etc.). If a future XSS happened, the attacker could
 *       not abuse these capabilities to spy on the user.
 *
 *   Strict-Transport-Security (HSTS)
 *       Tells the browser to ALWAYS use HTTPS for our domain for the next
 *       year. Defeats SSL-stripping attacks. Only emitted on requests that
 *       came in over HTTPS — emitting it on plain-HTTP responses is a
 *       no-op at best and a misconfiguration signal at worst.
 *
 *   Content-Security-Policy
 *       Restricts where scripts/styles/fonts/images can be loaded from.
 *       Our build is essentially self-hosted (Vercel for the SPA, Railway
 *       for the API), so 'self' covers the legitimate case. The connect-src
 *       includes the API origin so fetch calls work; the REST tester does
 *       call arbitrary user-supplied URLs but those come from the user's
 *       browser, not our origin context, so they bypass our CSP entirely.
 *       Inline styles ('unsafe-inline') stay enabled because Vite's CSS
 *       modules emit some inline; inline scripts are forbidden.
 *
 * ── Why a separate filter ────────────────────────────────────────────────
 * Putting these in CorsFilter would couple two unrelated concerns and risk
 * either being switched off if the other needs tuning. A separate, single-
 * responsibility filter keeps the chain readable.
 *
 * Mapped early in web.xml (right after SameSiteFilter) so the headers are
 * set before any other filter or servlet runs and end up on every response,
 * including 401/403 short-circuits.
 */
public class SecurityHeadersFilter implements Filter {

    private static final String CSP =
            "default-src 'self'; "
          + "script-src 'self'; "
          + "style-src 'self' 'unsafe-inline'; "
          + "img-src 'self' data: blob:; "
          + "font-src 'self' data:; "
          + "connect-src 'self' https: wss:; "
          + "frame-ancestors 'none'; "
          + "base-uri 'self'; "
          + "form-action 'self'";

    private static final String PERMISSIONS_POLICY =
            "camera=(), microphone=(), geolocation=(), payment=(), "
          + "usb=(), magnetometer=(), accelerometer=(), gyroscope=()";

    private static final String HSTS =
            "max-age=31536000; includeSubDomains";

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // Only set headers we don't already have; gives downstream filters
        // (e.g. a future per-endpoint stricter CSP) a way to override.
        if (!response.containsHeader("X-Content-Type-Options")) {
            response.setHeader("X-Content-Type-Options", "nosniff");
        }
        if (!response.containsHeader("X-Frame-Options")) {
            response.setHeader("X-Frame-Options", "DENY");
        }
        if (!response.containsHeader("Referrer-Policy")) {
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        }
        if (!response.containsHeader("Permissions-Policy")) {
            response.setHeader("Permissions-Policy", PERMISSIONS_POLICY);
        }
        if (!response.containsHeader("Content-Security-Policy")) {
            response.setHeader("Content-Security-Policy", CSP);
        }

        // HSTS is only meaningful on HTTPS — emitting it on plain-http
        // responses is ignored by browsers but smells like misconfiguration
        // when audited, so we suppress it explicitly.
        if (request.isSecure() && !response.containsHeader("Strict-Transport-Security")) {
            response.setHeader("Strict-Transport-Security", HSTS);
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    @Override public void init(FilterConfig fc) throws ServletException { }
    @Override public void destroy() { }
}
