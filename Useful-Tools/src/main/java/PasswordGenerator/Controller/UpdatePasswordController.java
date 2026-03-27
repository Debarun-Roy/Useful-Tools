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
import passwordgenerator.utilities.HashingUtils;

/**
 * SECURITY FIX — username is now read from the HTTP session, never from the
 * request parameter.
 *
 * The original read username from request.getParameter("username"). Any
 * authenticated user could change another user's password simply by sending
 * a different username in the form body — e.g. posting username=victim to
 * update victim's password without knowing their current one. This is an
 * account-takeover vulnerability.
 *
 * Fix: username is extracted exclusively from the session (set by
 * LoginController after successful authentication). The client-supplied
 * username parameter is ignored entirely.
 *
 * CHANGE 6: Path renamed /UpdatePassword → /api/auth/update-password
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

            // ── Read username from session — NEVER from request parameters ──
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
                        "New password must be at least " + MIN_PASSWORD_LENGTH + " characters.",
                        "PASSWORD_TOO_SHORT")));
                return;
            }

            // Username is taken from session — no need to verify it exists separately.
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