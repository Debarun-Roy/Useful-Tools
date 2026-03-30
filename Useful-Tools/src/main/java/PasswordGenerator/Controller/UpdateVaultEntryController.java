package passwordgenerator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
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
 * Re-encrypts and replaces a stored vault password for the authenticated user.
 *
 * PUT /api/passwords/update
 * Content-Type: application/json
 * Body: { "platform": "github.com", "password": "newSecurePassword123!" }
 *
 * The body is JSON (not form-encoded) because servlet containers do not
 * reliably parse application/x-www-form-urlencoded for PUT requests.
 *
 * The DAOs use ON CONFLICT (username, platform) DO UPDATE, so calling
 * saveUserPasswordDetails() and saveEncryptionDetails() with new values
 * cleanly replaces the existing entry without a separate UPDATE statement.
 *
 * Username is read from the session only — never from the request body.
 *
 * Response 200: { "success": true, "data": { "message": "...", ... } }
 * Response 400: { "success": false, "errorCode": "...", "error": "..." }
 */
@WebServlet("/api/passwords/update")
public class UpdateVaultEntryController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = new UnifiedLogger().writeLogs("controller");
    private final Gson gson = new Gson();

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        try {
            HttpSession session = request.getSession(false);
            String username = (session != null)
                    ? (String) session.getAttribute("username") : null;
            if (username == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "Not authenticated.", "UNAUTHENTICATED")));
                return;
            }

            // Parse JSON body.
            JsonObject body;
            try {
                body = gson.fromJson(request.getReader(), JsonObject.class);
            } catch (JsonSyntaxException jse) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body must be valid JSON: { \"platform\": \"...\", \"password\": \"...\" }",
                        "INVALID_JSON")));
                return;
            }

            if (body == null
                    || !body.has("platform") || body.get("platform").isJsonNull()
                    || !body.has("password") || body.get("password").isJsonNull()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Both 'platform' and 'password' fields are required.",
                        "MISSING_PARAMETERS")));
                return;
            }

            String platform = body.get("platform").getAsString().trim();
            String password = body.get("password").getAsString();

            if (platform.isBlank() || password.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Platform and password must not be blank.",
                        "MISSING_PARAMETERS")));
                return;
            }

            // Re-encrypt with a fresh RSA key pair and overwrite both tables.
            PasswordModel pass = new PasswordModel();
            pass.setUsername(username.trim());
            pass.setPlatform(platform);

            LinkedHashMap<String, String> encryption =
                    EncryptionUtils.generateEncryptedPassword(password);
            pass.setEncryptedPassword(encryption.get("encrypted_password"));
            pass.setPrivateKey(encryption.get("private_key"));
            pass.setHashedPassword(HashingUtils.generateHashedPassword(password));
            pass.setCreatedDate(Instant.now());

            UserPasswordDAO.saveUserPasswordDetails(pass);
            UserPasswordDAO.saveEncryptionDetails(pass);
            logger.info("Vault entry updated for user=" + username
                    + " platform=" + platform);

            LinkedHashMap<String, String> data = new LinkedHashMap<>();
            data.put("message",  "Password updated successfully.");
            data.put("username", username.trim());
            data.put("platform", platform);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(gson.toJson(ApiResponse.fail(
                    "Failed to update vault entry.", "INTERNAL_ERROR")));
        }
    }
}