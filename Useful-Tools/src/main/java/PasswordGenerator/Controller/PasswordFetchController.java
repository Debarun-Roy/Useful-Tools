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
import passwordgenerator.dao.UserPasswordDAO;

/**
 * Fetches stored passwords for a user — either all platforms or one specific platform.
 *
 * CHANGE 3: The forward() call in the catch block has been replaced with a
 *   structured JSON error response. The success paths already returned JSON
 *   but were not wrapped in ApiResponse — they now are.
 *
 * CHANGE 5: HTTP status codes applied.
 *   400 for missing/invalid parameters, 500 for server errors.
 *
 * CHANGE 6: Path renamed /FetchPasswords → /api/passwords/fetch
 *
 * ── Request ───────────────────────────────────────────────────────────────
 * GET /api/passwords/fetch?username={user}&choice=All+Passwords
 * GET /api/passwords/fetch?username={user}&choice=Single&platform={platform}
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

            String username = request.getParameter("username");
            String choice   = request.getParameter("choice");

            // ── Input validation ────────────────────────────────────────────
            if (username == null || username.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Parameter 'username' is required.", "MISSING_USERNAME")));
                return;
            }

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
                        UserPasswordDAO.fetchUserPasswords(username.trim());
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