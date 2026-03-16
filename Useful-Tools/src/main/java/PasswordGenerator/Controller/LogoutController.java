package passwordgenerator.controller;

import java.io.IOException;
import java.io.PrintWriter;

import com.google.gson.Gson;
import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Terminates the current user session.
 *
 * ── What it does ──────────────────────────────────────────────────────────
 * Calls session.invalidate(), which:
 *   1. Removes all attributes stored in the session (username, any cached data).
 *   2. Marks the session as invalid so any subsequent request using the same
 *      JSESSIONID cookie is treated as unauthenticated.
 *   3. The browser's session cookie (JSESSIONID) becomes invalid — the next
 *      request from the browser will either create a new session or be
 *      rejected by AuthFilter with 401.
 *
 * React's responsibility on receiving this 200 response:
 *   1. Clear all locally-held user state.
 *   2. Navigate to the /login route.
 *
 * ── HTTP method ──────────────────────────────────────────────────────────
 * POST is used (not GET) because logout is a state-changing operation.
 * Using GET for logout is a CSRF risk — an attacker could embed
 * <img src="/api/auth/logout"> in any page the user visits and silently
 * log them out. POST requires a form or explicit fetch call, so it cannot
 * be triggered by a passive resource load.
 *
 * ── Public path consideration ────────────────────────────────────────────
 * This endpoint is NOT in AuthFilter.PUBLIC_PATHS. That is intentional —
 * there is no harm in "logging out" when not logged in (session is null or
 * already invalid, invalidate() is safely a no-op in that case), but the
 * AuthFilter should still run to maintain consistent behaviour. If the
 * session has already expired and AuthFilter returns 401, the React client
 * should treat that as a successful logout (the user is already unauthenticated).
 *
 * ── Eclipse setup ────────────────────────────────────────────────────────
 * Place in: src/passwordgenerator/controller/LogoutController.java
 */
@WebServlet("/api/auth/logout")
public class LogoutController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            // getSession(false) — do not create a new session if none exists.
            // If null, the user is already logged out; this is not an error.
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok("Logged out successfully.")));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Logout failed. Please try again.",
                        "INTERNAL_ERROR")));
            }
        }
    }
}