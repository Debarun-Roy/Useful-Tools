package passwordgenerator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

import org.mindrot.jbcrypt.BCrypt;

import com.google.gson.Gson;

import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import passwordgenerator.dao.PasswordHistoryDAO;
import passwordgenerator.dao.UserDAO;
import passwordgenerator.utilities.HashingUtils;

/**
 * ForgotPasswordController — Step 3 (final) of the forgot-password flow.
 *
 * Takes username + recoveryCode + newPassword together and, if everything
 * checks out, actually changes the password.
 *
 * FIXES applied to the original draft of this controller:
 *   1. It never wrote a success response — after UserDAO.updateUserPassword()
 *      the method just returned, leaving the client with an empty body that
 *      apiClient.js's parseResponseBody() turns into {} (falsy .success),
 *      so the UI could never detect success. Now returns
 *      ApiResponse.ok({ message: "Password changed successfully." }).
 *   2. It never checked MIN_PASSWORD_LENGTH — RegistrationController and
 *      UpdatePasswordController both enforce a minimum length in addition to
 *      the uppercase/digit/special-character policy; this controller now
 *      does the same for consistency.
 *   3. It never checked password-reuse history — UpdatePasswordController
 *      rejects a new password that matches one of the last 5 hashes; this
 *      controller now does the same and appends the new hash to history,
 *      exactly like UpdatePasswordController and RegistrationController do.
 *   4. It had no outer try/catch, so an unexpected exception (e.g. a DB
 *      error) would produce a raw container error page instead of a JSON
 *      error the frontend can parse.
 *   5. Recovery code is re-verified here rather than trusted from Step 2
 *      (ForgotPasswordRecoveryController) — defense-in-depth, since HTTP
 *      requests are stateless and a client could otherwise call this
 *      endpoint directly, skipping Step 2 entirely.
 *
 * Public, pre-authentication endpoint — see AuthFilter.PUBLIC_PATHS and
 * CsrfFilter.PUBLIC_PATHS.
 */
@WebServlet("/api/auth/forgot-password")
public class ForgotPasswordController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int HISTORY_DEPTH = 5;
    private final Gson gson = new Gson();
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile(".*[^A-Za-z0-9].*");

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            String username = request.getParameter("username");
            if (username == null || username.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Username is required.",
                        "USERNAME_REQUIRED")));
                return;
            }
            username = username.trim();

            // ── 1. Check if user exists ───────────────────────────────────
            if (!UserDAO.checkIfUserExists(username)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print(gson.toJson(ApiResponse.fail(
                        "No account found for this username.",
                        "USER_NOT_FOUND")));
                return;
            }

            // ── 2. Recovery code required ──────────────────────────────────
            String recoveryCode = request.getParameter("recoveryCode");
            if (recoveryCode == null || recoveryCode.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Recovery code is required.",
                        "RECOVERY_CODE_REQUIRED")));
                return;
            }

            // ── 3. Verify recovery code (re-checked — see class comment) ──
            boolean isRecoveryCodeValid = UserDAO.verifyRecoveryCode(username, recoveryCode.trim());
            if (!isRecoveryCodeValid) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "Invalid recovery code.",
                        "INVALID_RECOVERY_CODE")));
                return;
            }

            // ── 4. New password required ───────────────────────────────────
            String newPassword = request.getParameter("newPassword");
            if (newPassword == null || newPassword.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "New password is required.",
                        "NEW_PASSWORD_REQUIRED")));
                return;
            }

            // ── 5. Minimum length (FIX — previously missing) ───────────────
            if (newPassword.length() < MIN_PASSWORD_LENGTH) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "New password must be at least " + MIN_PASSWORD_LENGTH
                        + " characters.", "PASSWORD_TOO_SHORT")));
                return;
            }

            // ── 6. Strength policy ──────────────────────────────────────────
            if (!meetsPasswordStrengthPolicy(newPassword)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "New password must include at least 1 uppercase letter, 1 digit, and 1 special character.",
                        "PASSWORD_WEAK")));
                return;
            }

            // ── 7. Reuse prevention (FIX — previously missing) ─────────────
            List<String> recentHashes =
                    PasswordHistoryDAO.getRecentHashes(username, HISTORY_DEPTH);
            for (String historicHash : recentHashes) {
                if (BCrypt.checkpw(newPassword, historicHash)) {
                    response.setStatus(HttpServletResponse.SC_CONFLICT); // 409
                    out.print(gson.toJson(ApiResponse.fail(
                            "This password was recently used. Please choose a different password.",
                            "PASSWORD_RECENTLY_USED")));
                    return;
                }
            }

            // ── 8. Apply the update ─────────────────────────────────────────
            UserDAO.updateUserPassword(username, newPassword);

            // ── 9. Record in history (FIX — previously missing) ────────────
            String historyHash = HashingUtils.generateHashedPassword(newPassword);
            PasswordHistoryDAO.addToHistory(username, historyHash);

            // ── 10. Success response (FIX — previously never sent at all) ──
            LinkedHashMap<String, String> data = new LinkedHashMap<>();
            data.put("message", "Password changed successfully.");

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Failed to reset the password. Please try again.",
                        "INTERNAL_ERROR")));
            }
        }
    }

    private boolean meetsPasswordStrengthPolicy(String password) {
        return UPPERCASE_PATTERN.matcher(password).matches()
                && DIGIT_PATTERN.matcher(password).matches()
                && SPECIAL_PATTERN.matcher(password).matches();
    }
}
