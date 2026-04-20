package common.controller.webdev;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;

import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * RequestHeadersController — Sprint 17, HTTP Header Viewer tool.
 *
 * Returns all HTTP request headers sent by the browser on a GET request.
 * The Cookie header is excluded for privacy (it contains session IDs).
 *
 * GET /api/webdev/request-headers
 *
 * Response 200:
 * {
 *   "success": true,
 *   "data": {
 *     "headers": {
 *       "accept":          "text/html,application/xhtml+xml,...",
 *       "accept-encoding": "gzip, deflate, br",
 *       "accept-language": "en-US,en;q=0.9",
 *       "host":            "localhost:8080",
 *       "user-agent":      "Mozilla/5.0 ...",
 *       ...
 *     },
 *     "remoteAddr":  "127.0.0.1",
 *     "protocol":    "HTTP/1.1",
 *     "method":      "GET"
 *   }
 * }
 *
 * NOTE: The Cookie and X-XSRF-TOKEN headers are stripped from the response
 * to avoid exposing session credentials to the frontend JavaScript environment.
 */
@WebServlet("/api/webdev/request-headers")
public class RequestHeadersController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    /** Headers that should never be echoed back. */
    private static final Set<String> REDACTED_HEADERS = Set.of(
            "cookie",
            "x-xsrf-token"
    );

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            // Collect request headers in lowercase-key order
            Map<String, String> headers = new LinkedHashMap<>();
            Collections.list(request.getHeaderNames()).stream()
                .sorted()
                .forEach(name -> {
                    if (!REDACTED_HEADERS.contains(name.toLowerCase())) {
                        headers.put(name.toLowerCase(), request.getHeader(name));
                    }
                });

            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("headers",    headers);
            data.put("remoteAddr", request.getRemoteAddr());
            data.put("protocol",   request.getProtocol());
            data.put("method",     request.getMethod());

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print(gson.toJson(
                ApiResponse.fail("Failed to read request headers.", "INTERNAL_ERROR")));
        }
    }
}
