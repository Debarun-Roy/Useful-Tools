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
import passwordgenerator.dao.UserDAO;

/**
 * Handles new user registration.
 *
 * CHANGE 3 — All request.getRequestDispatcher().forward() calls have been
 * replaced with structured JSON responses using ApiResponse.
 *
 * Previous behaviour:
 *   - Success      → forward to /Login (server-side redirect — meaningless to React)
 *   - Catch block  → forward to /errorPageReloader.jsp
 *
 * New behaviour:
 *   - Missing params      → 400  { success:false, errorCode:"MISSING_CREDENTIALS" }
 *   - Username too short  → 400  { success:false, errorCode:"INVALID_USERNAME" }
 *   - Password too short  → 400  { success:false, errorCode:"PASSWORD_TOO_SHORT" }
 *   - Username taken      → 409  { success:false, errorCode:"USERNAME_TAKEN" }
 *   - Success             → 201  { success:true,  data:{ message } }
 *   - Any exception       → 500  { success:false, errorCode:"INTERNAL_ERROR" }
 *
 * On success the React client is responsible for navigating to the login page.
 *
 * BUG FIX (carried from prior review batch):
 * The original controller called HashingUtils.generateHashedPassword(password)
 * and passed the already-hashed value to UserDAO.registerUser(), which hashed
 * it a second time internally. The password was double-hashed — login would
 * never succeed for any registered user. This controller now passes the plain
 * password to UserDAO.registerUser(), which performs the single correct hash.
 *
 * NOTE: This controller still reads credentials from form-encoded request
 * parameters. Migration to a JSON request body is handled in a later sprint.
 */
@WebServlet("/Registration")
public class RegistrationController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** Minimum acceptable password length. */
    private static final int MIN_PASSWORD_LENGTH = 8;

    /** Minimum acceptable username length. */
    private static final int MIN_USERNAME_LENGTH = 3;

    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            String username = request.getParameter("username");
            String password = request.getParameter("password");

            // ── Input presence check ────────────────────────────────────────
            if (username == null || username.isBlank()
                    || password == null || password.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Username and password are required.",
                        "MISSING_CREDENTIALS")));
                return;
            }

            // ── Username length check ───────────────────────────────────────
            if (username.trim().length() < MIN_USERNAME_LENGTH) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Username must be at least " + MIN_USERNAME_LENGTH + " characters.",
                        "INVALID_USERNAME")));
                return;
            }

            // ── Password length check ───────────────────────────────────────
            if (password.length() < MIN_PASSWORD_LENGTH) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.",
                        "PASSWORD_TOO_SHORT")));
                return;
            }

            // ── Duplicate username check ────────────────────────────────────
            // UserDAO.registerUser() uses ON CONFLICT DO NOTHING, which silently
            // ignores a duplicate without throwing an exception. We must check
            // for existence BEFORE inserting so we can return 409 Conflict.
            if (UserDAO.checkIfUserExists(username.trim())) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                out.print(gson.toJson(ApiResponse.fail(
                        "That username is already taken. Please choose another.",
                        "USERNAME_TAKEN")));
                return;
            }

            // ── Register ────────────────────────────────────────────────────
            // Pass the plain-text password. Hashing is performed exactly once,
            // inside UserDAO.registerUser() — not here. (Prior double-hash bug fixed.)
            UserDAO.registerUser(username.trim(), password);

            LinkedHashMap<String, String> data = new LinkedHashMap<>();
            data.put("message", "Registration successful. Please log in.");

            response.setStatus(HttpServletResponse.SC_CREATED);  // 201
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
}