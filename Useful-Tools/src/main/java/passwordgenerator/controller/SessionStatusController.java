package passwordgenerator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import com.google.gson.Gson;

import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Session Status Endpoint for Analytics & Debugging.
 *
 * This endpoint allows the frontend to explicitly verify that the session is
 * still valid after login. It's primarily used for debugging session issues
 * and ensuring the JSESSIONID cookie is being transmitted properly in
 * cross-origin deployments (Vercel → Railway).
 *
 * GET /api/auth/session-status
 *
 * Response:
 *   200 OK (session valid):
 *   {
 *     "success": true,
 *     "data": {
 *       "username": "user123",
 *       "authenticated": true
 *     }
 *   }
 *
 *   401 Unauthorized (session expired or missing):
 *   {
 *     "success": false,
 *     "errorCode": "UNAUTHENTICATED",
 *     "error": "Session has expired. Please log in again."
 *   }
 */
@WebServlet("/api/auth/session-status")
public class SessionStatusController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            HttpSession session = request.getSession(false);
            
            if (session == null) {
                // No session exists at all (JSESSIONID cookie not sent or invalid)
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "No session found. Please log in.",
                        "UNAUTHENTICATED")));
                return;
            }

            String username = (String) session.getAttribute("username");

            if (username == null || username.isBlank()) {
                // Session exists but has no username (not authenticated)
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "Session exists but not authenticated. Please log in.",
                        "UNAUTHENTICATED")));
                return;
            }

            // Session is valid and authenticated
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("username", username);
            data.put("authenticated", true);
            data.put("sessionId", session.getId());
            data.put("creationTime", session.getCreationTime());
            data.put("lastAccessedTime", session.getLastAccessedTime());
            data.put("maxInactiveInterval", session.getMaxInactiveInterval());

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print(gson.toJson(ApiResponse.fail(
                    "Failed to check session status.",
                    "INTERNAL_ERROR")));
        }
    }
}
