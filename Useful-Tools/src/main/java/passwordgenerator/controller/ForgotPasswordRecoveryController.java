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
 * ForgotPasswordRecoveryController — Step 2 of the forgot-password flow.
 *
 * Checks the recovery code ALONE, before the client reveals the new-password
 * fields. Only reachable in practice after Step 1 (captcha) has already
 * passed, but this endpoint re-validates independently — it never trusts
 * client-reported state from a previous step.
 *
 * The final password reset (Step 3, ForgotPasswordController) re-checks the
 * recovery code again server-side before committing anything. That is
 * intentional defense-in-depth, not a duplicate bug: HTTP requests are
 * stateless, and this endpoint's "success" here does not by itself authorize
 * the later password change.
 *
 * Public, pre-authentication endpoint — see AuthFilter.PUBLIC_PATHS and
 * CsrfFilter.PUBLIC_PATHS.
 */
@WebServlet("/api/auth/forgot-password/recovery-code")
public class ForgotPasswordRecoveryController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            String username     = request.getParameter("username");
            String recoveryCode = request.getParameter("recoveryCode");

            if (username == null || username.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Username is required.", "USERNAME_REQUIRED")));
                return;
            }

            if (recoveryCode == null || recoveryCode.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Recovery code is required.", "RECOVERY_CODE_REQUIRED")));
                return;
            }

            if (!UserDAO.checkIfUserExists(username.trim())) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print(gson.toJson(ApiResponse.fail(
                        "No account found for this username.",
                        "USER_NOT_FOUND")));
                return;
            }

            boolean isValid = UserDAO.verifyRecoveryCode(username.trim(), recoveryCode.trim());
            if (!isValid) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "Invalid recovery code.", "INVALID_RECOVERY_CODE")));
                return;
            }

            LinkedHashMap<String, String> data = new LinkedHashMap<>();
            data.put("message", "Recovery code verified. Choose a new password.");

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "An unexpected error occurred. Please try again.",
                        "INTERNAL_ERROR")));
            }
        }
    }
}
