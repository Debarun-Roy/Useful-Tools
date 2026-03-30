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
import passwordgenerator.dao.UserPasswordDAO;

/**
 * Deletes a vault entry for the currently authenticated user.
 *
 * DELETE /api/passwords/delete?platform=github.com
 *
 * Username is read from the session only — never from a request parameter.
 * This prevents one user from deleting another user's vault entries.
 *
 * Response 200: { "success": true,  "data": { "message": "...", "platform": "..." } }
 * Response 404: { "success": false, "errorCode": "NOT_FOUND",  "error": "..." }
 */
@WebServlet("/api/passwords/delete")
public class DeleteVaultEntryController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
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

            String platform = request.getParameter("platform");
            if (platform == null || platform.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Parameter 'platform' is required.", "MISSING_PLATFORM")));
                return;
            }

            boolean deleted = UserPasswordDAO.deleteVaultEntry(
                    username.trim(), platform.trim());

            if (!deleted) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print(gson.toJson(ApiResponse.fail(
                        "No vault entry found for platform: " + platform,
                        "NOT_FOUND")));
                return;
            }

            LinkedHashMap<String, String> data = new LinkedHashMap<>();
            data.put("message",  "Vault entry deleted successfully.");
            data.put("platform", platform.trim());

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(gson.toJson(ApiResponse.fail(
                    "Failed to delete vault entry.", "INTERNAL_ERROR")));
        }
    }
}