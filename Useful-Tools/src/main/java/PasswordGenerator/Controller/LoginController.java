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
import passwordgenerator.dao.UserDAO;
import passwordgenerator.utilities.LoginUtils;

/**
 * CHANGE 6: Path renamed /Login → /api/auth/login
 * All other content identical to the batch-1 version.
 */
@WebServlet("/api/auth/login")
public class LoginController extends HttpServlet {

    private static final long serialVersionUID = 1L;
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

            if (!UserDAO.checkIfUserExists(username)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print(gson.toJson(ApiResponse.fail(
                        "No account found for this username. Please register.", "USER_NOT_FOUND")));
                return;
            }

            String storedHash = UserDAO.getStoredHashPassword(username);
            if (!LoginUtils.verifyUser(password, storedHash)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "Incorrect password.", "INVALID_CREDENTIALS")));
                return;
            }

            HttpSession session = request.getSession(true);
            session.setAttribute("username", username);

            LinkedHashMap<String, String> data = new LinkedHashMap<>();
            data.put("username", username);
            data.put("message",  "Login successful.");

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "An unexpected error occurred. Please try again.", "INTERNAL_ERROR")));
            }
        }
    }
}