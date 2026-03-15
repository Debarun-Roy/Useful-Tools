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
import passwordgenerator.utilities.HashingUtils;

/**
 * Updates the master account password for a registered user.
 *
 * CHANGE 3 — All request.getRequestDispatcher().forward() calls have been
 * replaced with structured JSON responses using ApiResponse.
 *
 * Previous behaviour:
 *   - Success      → forward to /success.jsp
 *   - Catch block  → e.printStackTrace() only; NO response was ever written.
 *                    The browser/client would hang waiting for a response that
 *                    never came. This was a silent failure bug.
 *
 * New behaviour:
 *   - Missing params      → 400  { success:false, errorCode:"MISSING_PARAMETERS" }
 *   - Password too short  → 400  { success:false, errorCode:"PASSWORD_TOO_SHORT" }
 *   - User not found      → 404  { success:false, errorCode:"USER_NOT_FOUND" }
 *   - Success             → 200  { success:true,  data:{ message } }
 *   - Any exception       → 500  { success:false, errorCode:"INTERNAL_ERROR" }
 *
 * NOTE: This controller still reads parameters from form-encoded request data.
 * Migration to a JSON request body is handled in a later sprint.
 */
@WebServlet("/UpdatePassword")
public class UpdatePasswordController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            String username        = request.getParameter("username");
            String updatedPassword = request.getParameter("updated_password");

            // ── Input validation ────────────────────────────────────────────
            if (username == null || username.isBlank()
                    || updatedPassword == null || updatedPassword.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Username and updated_password are required.",
                        "MISSING_PARAMETERS")));
                return;
            }

            if (updatedPassword.length() < MIN_PASSWORD_LENGTH) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "New password must be at least " + MIN_PASSWORD_LENGTH + " characters.",
                        "PASSWORD_TOO_SHORT")));
                return;
            }

            // ── Verify the user actually exists ─────────────────────────────
            // Prevents silently updating a non-existent account.
            if (!UserDAO.checkIfUserExists(username.trim())) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print(gson.toJson(ApiResponse.fail(
                        "No account found for this username.",
                        "USER_NOT_FOUND")));
                return;
            }

            // ── Hash and update ─────────────────────────────────────────────
            String hashedPassword = HashingUtils.generateHashedPassword(updatedPassword);
            UserDAO.updateUserDetails(username.trim(), hashedPassword);

            // ── Success response ────────────────────────────────────────────
            LinkedHashMap<String, String> data = new LinkedHashMap<>();
            data.put("message", "Password updated successfully. Please log in with your new password.");

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            // BUG FIX: The original catch block only called e.printStackTrace()
            // and wrote no response. The client would hang indefinitely.
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Failed to update the password. Please try again.",
                        "INTERNAL_ERROR")));
            }
        }
    }
}