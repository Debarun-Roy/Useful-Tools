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
import passwordgenerator.utilities.RecaptchaUtils;

/**
 * ForgotPasswordCaptchaController — Step 1 of the forgot-password flow.
 *
 * Verifies the username exists and the supplied reCAPTCHA v3 token is
 * genuine, BEFORE the client is allowed to move on to entering a recovery
 * code. Split into its own endpoint (rather than folded into
 * ForgotPasswordController) so the UI can show "invalid captcha/username"
 * immediately, without asking for a recovery code first.
 *
 * This is a public, pre-authentication endpoint — registered in
 * AuthFilter.PUBLIC_PATHS and CsrfFilter.PUBLIC_PATHS (no session exists at
 * this point in the flow). Already rate-limited by RateLimitFilter, which
 * applies to all of /api/auth/*.
 */
@WebServlet("/api/auth/forgot-password/captcha")
public class ForgotPasswordCaptchaController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String RECAPTCHA_ACTION = "forgot_password";
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            String username       = request.getParameter("username");
            String recaptchaToken = request.getParameter("recaptchaToken");

            if (username == null || username.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Username is required.", "USERNAME_REQUIRED")));
                return;
            }

            if (recaptchaToken == null || recaptchaToken.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Captcha verification is required.", "CAPTCHA_REQUIRED")));
                return;
            }

            boolean captchaValid;
            try {
                captchaValid = RecaptchaUtils.verify(
                        recaptchaToken, RECAPTCHA_ACTION, request.getRemoteAddr());
            } catch (IllegalStateException configError) {
                // RECAPTCHA_SECRET_KEY missing — a server misconfiguration, not
                // the caller's fault. Fail closed and surface a distinct code
                // so this doesn't get confused with "you typed the captcha wrong".
                configError.printStackTrace();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print(gson.toJson(ApiResponse.fail(
                        "Captcha verification is not configured on the server.",
                        "CAPTCHA_NOT_CONFIGURED")));
                return;
            }

            if (!captchaValid) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                out.print(gson.toJson(ApiResponse.fail(
                        "Captcha verification failed. Please try again.",
                        "CAPTCHA_INVALID")));
                return;
            }

            if (!UserDAO.checkIfUserExists(username.trim())) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print(gson.toJson(ApiResponse.fail(
                        "No account found for this username.",
                        "USER_NOT_FOUND")));
                return;
            }

            LinkedHashMap<String, String> data = new LinkedHashMap<>();
            data.put("message", "Identity check passed. Enter your recovery code.");

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
