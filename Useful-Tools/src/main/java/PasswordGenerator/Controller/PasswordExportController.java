package passwordgenerator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import passwordgenerator.dao.UserPasswordDAO;
import passwordgenerator.utilities.VaultExportEncryptionUtils;

/**
 * Creates an encrypted vault-export JSON payload for the logged-in user.
 *
 * GET /api/passwords/export
 *
 * The returned payload is intended for browser download via Blob on the
 * frontend. The inner vault JSON is encrypted before being returned.
 */
@WebServlet("/api/passwords/export")
public class PasswordExportController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        try {
            HttpSession session = request.getSession(false);
            String username = (session != null)
                    ? (String) session.getAttribute("username")
                    : null;

            if (username == null || username.isBlank()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "You must be logged in to export your vault.",
                        "UNAUTHENTICATED")));
                return;
            }

            ArrayList<LinkedHashMap<String, String>> entries =
                    UserPasswordDAO.fetchVaultEntriesForExport(username.trim());

            if (entries.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print(gson.toJson(ApiResponse.fail(
                        "Your vault is empty. Save at least one password before exporting.",
                        "EMPTY_VAULT")));
                return;
            }

            String bcryptToken = UserDAO.getStoredHashPassword(username.trim());
            if (bcryptToken == null || bcryptToken.isBlank()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print(gson.toJson(ApiResponse.fail(
                        "Could not derive the export key for this account.",
                        "EXPORT_KEY_UNAVAILABLE")));
                return;
            }

            LinkedHashMap<String, Object> plainPayload = new LinkedHashMap<>();
            plainPayload.put("username", username.trim());
            plainPayload.put("exportedAt", Instant.now().toString());
            plainPayload.put("entryCount", entries.size());
            plainPayload.put("entries", entries);

            LinkedHashMap<String, Object> exportPayload = new LinkedHashMap<>();
            exportPayload.put("format", "usefultools-vault-export");
            exportPayload.put("version", 1);
            exportPayload.put("username", username.trim());
            exportPayload.put("exportedAt", Instant.now().toString());
            exportPayload.put("entryCount", entries.size());
            exportPayload.put("encryption",
                    VaultExportEncryptionUtils.encryptJson(gson.toJson(plainPayload), bcryptToken));

            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("fileName",
                    "usefultools-vault-" + username.trim() + "-" + FILE_TS.format(Instant.now()) + ".json");
            data.put("payload", exportPayload);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(gson.toJson(ApiResponse.fail(
                    "Failed to export vault entries.",
                    "INTERNAL_ERROR")));
        }
    }
}