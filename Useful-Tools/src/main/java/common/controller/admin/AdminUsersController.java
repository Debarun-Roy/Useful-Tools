package common.controller.admin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import common.ApiResponse;
import common.UserContext;
import common.dao.RoleDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * AdminUsersController — Sprint 17 RBAC.
 *
 * Handles user management operations for admin users.
 * Protected by AdminFilter (requires role=admin in session).
 *
 * ── Endpoints ─────────────────────────────────────────────────────────────
 *
 * GET /api/admin/users
 *   Returns all registered users (excluding Guest User) with username, role,
 *   and createdDate.
 *   Response 200: { "success": true, "data": { "users": [...] } }
 *
 * PUT /api/admin/users
 *   Updates a user's role.
 *   Body (JSON): { "username": "...", "role": "admin" | "user" }
 *   Response 200: { "success": true, "data": { "message": "..." } }
 *   Response 400: invalid role or self-demotion attempt
 *   Response 409: last admin protection
 *
 * DELETE /api/admin/users
 *   Deletes a user and all their data.
 *   Body (JSON): { "username": "..." }
 *   Response 200: { "success": true, "data": { "message": "..." } }
 *   Response 400: cannot delete self or last admin
 */
@WebServlet("/api/admin/users")
public class AdminUsersController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    // ── GET — list all users ──────────────────────────────────────────────

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {
            List<Map<String, String>> users = RoleDAO.listUsers();

            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("users", users);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));
        } catch (Exception e) {
            e.printStackTrace();
            writeError(response, 500, "Failed to list users.", "INTERNAL_ERROR");
        }
    }

    // ── PUT — update role ─────────────────────────────────────────────────

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            JsonObject body = parseJsonBody(request);
            String targetUsername = getString(body, "username");
            String newRole        = getString(body, "role");

            if (targetUsername == null || targetUsername.isBlank()) {
                writeError(response, 400, "Field 'username' is required.", "MISSING_FIELD");
                return;
            }
            if (newRole == null || (!newRole.equals("admin") && !newRole.equals("user"))) {
                writeError(response, 400,
                    "Field 'role' must be 'admin' or 'user'.", "INVALID_ROLE");
                return;
            }

            String requestingUser = UserContext.get();
            boolean updated = RoleDAO.setRole(targetUsername, newRole, requestingUser);

            if (!updated) {
                // Could be: self-demotion, last admin protection, or user not found
                writeError(response, 409,
                    "Could not update role. Check you are not demoting yourself or the last admin.",
                    "ROLE_UPDATE_BLOCKED");
                return;
            }

            LinkedHashMap<String, String> data = new LinkedHashMap<>();
            data.put("message", "Role updated for " + targetUsername + " → " + newRole);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            writeError(response, 500, "Failed to update role.", "INTERNAL_ERROR");
        }
    }

    // ── DELETE — delete user ──────────────────────────────────────────────

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            JsonObject body = parseJsonBody(request);
            String targetUsername = getString(body, "username");

            if (targetUsername == null || targetUsername.isBlank()) {
                writeError(response, 400, "Field 'username' is required.", "MISSING_FIELD");
                return;
            }

            String requestingUser = UserContext.get();
            boolean deleted = RoleDAO.deleteUser(targetUsername, requestingUser);

            if (!deleted) {
                writeError(response, 409,
                    "Cannot delete this user. You may not delete yourself or the last admin.",
                    "DELETE_BLOCKED");
                return;
            }

            LinkedHashMap<String, String> data = new LinkedHashMap<>();
            data.put("message", "User " + targetUsername + " deleted.");

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            writeError(response, 500, "Failed to delete user.", "INTERNAL_ERROR");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private JsonObject parseJsonBody(HttpServletRequest req) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(
                req.getInputStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private String getString(JsonObject obj, String key) {
        try {
            return obj.has(key) ? obj.get(key).getAsString() : null;
        } catch (Exception e) { return null; }
    }

    private void writeError(HttpServletResponse response,
                            int status, String message, String code)
            throws IOException {
        response.setStatus(status);
        response.getWriter().print(gson.toJson(ApiResponse.fail(message, code)));
    }
}
