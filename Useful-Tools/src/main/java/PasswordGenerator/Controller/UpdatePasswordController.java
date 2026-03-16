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
 * CHANGE 6: Path renamed /UpdatePassword → /api/auth/update-password
 * All other content identical to the batch-1 version.
 */
@WebServlet("/api/auth/update-password")
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

            if (username == null || username.isBlank()
                    || updatedPassword == null || updatedPassword.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Username and updated_password are required.", "MISSING_PARAMETERS")));
                return;
            }

            if (updatedPassword.length() < MIN_PASSWORD_LENGTH) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "New password must be at least " + MIN_PASSWORD_LENGTH + " characters.",
                        "PASSWORD_TOO_SHORT")));
                return;
            }

            if (!UserDAO.checkIfUserExists(username.trim())) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print(gson.toJson(ApiResponse.fail(
                        "No account found for this username.", "USER_NOT_FOUND")));
                return;
            }

            String hashedPassword = HashingUtils.generateHashedPassword(updatedPassword);
            UserDAO.updateUserDetails(username.trim(), hashedPassword);

            LinkedHashMap<String, String> data = new LinkedHashMap<>();
            data.put("message", "Password updated successfully. Please log in with your new password.");

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Failed to update the password. Please try again.", "INTERNAL_ERROR")));
            }
        }
    }
}