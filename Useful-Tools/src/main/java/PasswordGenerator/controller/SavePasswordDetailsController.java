package passwordgenerator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

import com.google.gson.Gson;
import common.ApiResponse;
import common.UnifiedLogger;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import passwordgenerator.dao.UserPasswordDAO;
import passwordgenerator.models.PasswordModel;
import passwordgenerator.utilities.EncryptionUtils;
import passwordgenerator.utilities.HashingUtils;

/**
 * SECURITY FIX — username is now read from the HTTP session, not from the
 * request parameter.
 *
 * The original accepted username as a form parameter. Any authenticated user
 * could call this endpoint with username=victim to insert vault entries into
 * another user's account. Because the INSERT uses ON CONFLICT (username, platform)
 * DO UPDATE, this could also silently overwrite a victim's stored passwords.
 *
 * Fix: username is extracted exclusively from the session.
 *
 * CHANGE 6: Path renamed /SavePassword → /api/passwords/save
 */
@WebServlet("/api/passwords/save")
public class SavePasswordDetailsController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = new UnifiedLogger().writeLogs("controller");
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            // ── Username from session only ───────────────────────────────────
            HttpSession session = request.getSession(false);
            String username = (session != null)
                    ? (String) session.getAttribute("username")
                    : null;

            if (username == null || username.isBlank()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "You must be logged in to save passwords.",
                        "UNAUTHENTICATED")));
                return;
            }

            String password = request.getParameter("password");
            String platform = request.getParameter("platform");

            if (password == null || password.isBlank()
                    || platform == null || platform.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Platform and password are required.", "MISSING_PARAMETERS")));
                return;
            }

            PasswordModel pass = new PasswordModel();
            pass.setUsername(username.trim());
            pass.setPlatform(platform.trim());

            LinkedHashMap<String, String> encryption =
                    EncryptionUtils.generateEncryptedPassword(password);
            pass.setEncryptedPassword(encryption.get("encrypted_password"));
            pass.setPrivateKey(encryption.get("private_key"));
            pass.setHashedPassword(HashingUtils.generateHashedPassword(password));
            pass.setCreatedDate(Instant.now());

            UserPasswordDAO.saveUserPasswordDetails(pass);
            UserPasswordDAO.saveEncryptionDetails(pass);
            logger.info("Password saved for user=" + username + " platform=" + platform);

            LinkedHashMap<String, String> data = new LinkedHashMap<>();
            data.put("username", username.trim());
            data.put("platform", platform.trim());
            data.put("message",  "Password saved successfully.");

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            logger.severe("Failed to save password: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Failed to save the password. Please try again.", "INTERNAL_ERROR")));
            }
        }
    }
}