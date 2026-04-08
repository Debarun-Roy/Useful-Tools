package passwordgenerator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;

import org.mindrot.jbcrypt.BCrypt;

import com.google.gson.Gson;
import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import passwordgenerator.dao.PasswordHistoryDAO;
import passwordgenerator.dao.UserDAO;
import passwordgenerator.utilities.HashingUtils;

/**
 * Sprint 6 addition: password reuse prevention.
 *
 * Before accepting a new password, the controller fetches the last 5 BCrypt
 * hashes from password_history and calls BCrypt.checkpw() against each one.
 * If any hash matches, the request is rejected with PASSWORD_RECENTLY_USED
 * (HTTP 409 Conflict).
 *
 * On acceptance, the new hash is both written to user_table (existing) and
 * appended to password_history (new), keeping the history current.
 *
 * Security note: username is read from the HTTP session only — never from
 * request parameters (prevents one authenticated user from changing another
 * user's password).
 */
@WebServlet("/api/auth/update-password")
public class UpdatePasswordController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int HISTORY_DEPTH = 5;
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            // ── 1. Authenticate via session ────────────────────────────────
            HttpSession session = request.getSession(false);
            String username = (session != null)
                    ? (String) session.getAttribute("username")
                    : null;

            if (username == null || username.isBlank()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "You must be logged in to update your password.",
                        "UNAUTHENTICATED")));
                return;
            }

            // ── 2. Validate new password ───────────────────────────────────
            String updatedPassword = request.getParameter("updated_password");

            if (updatedPassword == null || updatedPassword.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "updated_password is required.", "MISSING_PARAMETERS")));
                return;
            }

            if (updatedPassword.length() < MIN_PASSWORD_LENGTH) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "New password must be at least " + MIN_PASSWORD_LENGTH
                        + " characters.", "PASSWORD_TOO_SHORT")));
                return;
            }

            // ── 3. Password history check (Sprint 6) ──────────────────────
            List<String> recentHashes =
                    PasswordHistoryDAO.getRecentHashes(username, HISTORY_DEPTH);
            for (String historicHash : recentHashes) {
                if (BCrypt.checkpw(updatedPassword, historicHash)) {
                    response.setStatus(HttpServletResponse.SC_CONFLICT); // 409
                    out.print(gson.toJson(ApiResponse.fail(
                            "This password was recently used. Please choose a different password.",
                            "PASSWORD_RECENTLY_USED")));
                    return;
                }
            }

            // ── 4. Apply the update ────────────────────────────────────────
            String hashedPassword = HashingUtils.generateHashedPassword(updatedPassword);
            UserDAO.updateUserDetails(username.trim(), hashedPassword);

            // ── 5. Record in history (Sprint 6) ───────────────────────────
            PasswordHistoryDAO.addToHistory(username.trim(), hashedPassword);

            LinkedHashMap<String, String> data = new LinkedHashMap<>();
            data.put("message",
                    "Password updated successfully. Please log in with your new password.");

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Failed to update the password. Please try again.",
                        "INTERNAL_ERROR")));
            }
        }
    }
}