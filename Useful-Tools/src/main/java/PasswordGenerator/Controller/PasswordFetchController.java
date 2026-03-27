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
import passwordgenerator.dao.UserPasswordDAO;

/**
 * Fetches stored passwords for the currently authenticated user.
 *
 * FIX (SECURITY): The original read username from the query parameter
 *   ?username=..., allowing any authenticated user to fetch another user's
 *   passwords simply by changing the query string. The username is now read
 *   exclusively from the HTTP session, which was written by LoginController
 *   after a successful authentication and cannot be forged by the client.
 *
 * CHANGE 5: HTTP status codes applied.
 * CHANGE 6: Path renamed /FetchPasswords → /api/passwords/fetch
 *
 * ── Request ───────────────────────────────────────────────────────────────
 * GET /api/passwords/fetch?choice=All+Passwords
 * GET /api/passwords/fetch?choice=Single&platform={platform}
 *
 * ── Response (All Passwords) ──────────────────────────────────────────────
 * 200: {
 *   "success": true,
 *   "data": {
 *     "1": { "platform": "github.com",  "decrypted_password": "abc123" },
 *     "2": { "platform": "google.com",  "decrypted_password": "xyz789" }
 *   }
 * }
 *
 * ── Response (Single Platform) ────────────────────────────────────────────
 * 200: {
 *   "success": true,
 *   "data": { "decrypted_password": "abc123" }
 * }
 */
@WebServlet("/api/passwords/fetch")
public class PasswordFetchController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            // ── Read username from session — never from query parameters ────
            HttpSession session = request.getSession(false);
            String username = (session != null)
                    ? (String) session.getAttribute("username")
                    : null;

            if (username == null || username.isBlank()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "You must be logged in to fetch passwords.",
                        "UNAUTHENTICATED")));
                return;
            }

            String choice = request.getParameter("choice");

            if (choice == null || choice.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Parameter 'choice' is required. Valid values: 'All Passwords', 'Single'.",
                        "MISSING_CHOICE")));
                return;
            }

            // ── Fetch ───────────────────────────────────────────────────────
            if (choice.equalsIgnoreCase("All Passwords")) {
                LinkedHashMap<Integer, LinkedHashMap<String, String>> passwords =
                        UserPasswordDAO.fetchUserPasswords(username);
                response.setStatus(HttpServletResponse.SC_OK);
                out.print(gson.toJson(ApiResponse.ok(passwords)));

            } else {
                // Single platform lookup
                String platform = request.getParameter("platform");
                if (platform == null || platform.isBlank()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print(gson.toJson(ApiResponse.fail(
                            "Parameter 'platform' is required when choice is 'Single'.",
                            "MISSING_PLATFORM")));
                    return;
                }
                LinkedHashMap<String, String> password =
                        UserPasswordDAO.fetchUserPlatformPassword(username.trim(), platform.trim());
                response.setStatus(HttpServletResponse.SC_OK);
                out.print(gson.toJson(ApiResponse.ok(password)));
            }

            out.flush();

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Failed to fetch passwords. Please try again.", "INTERNAL_ERROR")));
            }
        }
    }
}