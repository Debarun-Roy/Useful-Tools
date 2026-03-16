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
 * CHANGE 6: Path renamed /Registration → /api/auth/register
 * All other content identical to the batch-1 version.
 */
@WebServlet("/api/auth/register")
public class RegistrationController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int MIN_PASSWORD_LENGTH = 8;
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

            if (UserDAO.checkIfUserExists(username.trim())) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                out.print(gson.toJson(ApiResponse.fail(
                        "That username is already taken. Please choose another.", "USERNAME_TAKEN")));
                return;
            }

            UserDAO.registerUser(username.trim(), password);

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
}