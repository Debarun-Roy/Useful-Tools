package passwordgenerator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

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
 * Sprint 6 addition: seeds the password_history table with the user's
 * initial password hash on successful registration. This prevents the user
 * from immediately "updating" back to the exact password they registered with,
 * enforcing consistent reuse protection from the very first login.
 *
 * A second BCrypt hash is generated for the history entry. BCrypt uses a
 * per-call random salt, so the history hash differs from the one stored in
 * user_table as a string — but both correctly verify via BCrypt.checkpw().
 */
@WebServlet("/api/auth/register")
public class RegistrationController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile(".*[^A-Za-z0-9].*");
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            String username = request.getParameter("username");
            String password = request.getParameter("password");

            if (username == null || username.isBlank()
                    || password == null || password.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Username and password are required.", "MISSING_CREDENTIALS")));
                return;
            }

            if (username.trim().length() < MIN_USERNAME_LENGTH) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Username must be at least " + MIN_USERNAME_LENGTH + " characters.",
                        "INVALID_USERNAME")));
                return;
            }

            if (password.length() < MIN_PASSWORD_LENGTH) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.",
                        "PASSWORD_TOO_SHORT")));
                return;
            }
            
            if (!meetsPasswordStrengthPolicy(password)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Password must include at least 1 uppercase letter, 1 digit, and 1 special character.",
                        "PASSWORD_WEAK")));
                return;
            }

            if (UserDAO.checkIfUserExists(username.trim())) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                out.print(gson.toJson(ApiResponse.fail(
                        "That username is already taken. Please choose another.",
                        "USERNAME_TAKEN")));
                return;
            }

            // Register the user (hashes password internally).
            UserDAO.registerUser(username.trim(), password);

            // Seed password history so the registration password cannot be
            // immediately reused after a "change password" action (Sprint 6).
            // A fresh BCrypt hash is generated — different salt from user_table
            // but verifiable by BCrypt.checkpw().
            String historyHash = HashingUtils.generateHashedPassword(password);
            PasswordHistoryDAO.addToHistory(username.trim(), historyHash);

            LinkedHashMap<String, String> data = new LinkedHashMap<>();
            data.put("message", "Registration successful. Please log in.");

            response.setStatus(HttpServletResponse.SC_CREATED);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "An unexpected error occurred during registration. Please try again.",
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