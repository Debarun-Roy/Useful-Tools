package common.filter;

import java.net.URI;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;

/** Source checks for the explicitly enabled localhost profile only. */
public final class LocalRequestPolicy {
    private static final Set<String> ORIGINS = Set.of("http://localhost:5173", "http://localhost:8080");
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private LocalRequestPolicy() { }

    public static String rejection(HttpServletRequest request) throws java.io.IOException {
        if (!java.net.InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) {
            return "Local requests require a loopback connection.";
        }
        if (!"localhost".equals(request.getServerName())) {
            return "Open http://localhost:5173/login using localhost, not an IP address or another hostname.";
        }
        String origin = request.getHeader("Origin");
        // An explicit invalid/opaque Origin must never be rescued by Referer.
        if (origin != null) {
            return ORIGINS.contains(origin) ? null
                    : "Local request Origin is not trusted. Open http://localhost:5173/login in a regular browser tab.";
        }
        if (SAFE_METHODS.contains(request.getMethod())) return null;
        return trustedReferer(request.getHeader("Referer")) ? null
                : "Local requests require a trusted Origin or Referer. Open http://localhost:5173/login and preserve browser source headers.";
    }

    private static boolean trustedReferer(String value) {
        if (value == null) return false;
        try {
            URI uri = new URI(value);
            // Parse components, never prefix-match localhost.attacker or userinfo.
            return "http".equals(uri.getScheme()) && "localhost".equals(uri.getHost())
                    && (uri.getPort() == 5173 || uri.getPort() == 8080)
                    && uri.getRawUserInfo() == null && uri.getRawFragment() == null;
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }
}
