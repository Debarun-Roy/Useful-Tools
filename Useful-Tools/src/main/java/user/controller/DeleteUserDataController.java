package user.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

import com.google.gson.Gson;

import common.ApiResponse;
import common.UnifiedLogger;
import common.UserContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import passwordgenerator.dao.UserDAO;
import user.utilities.UserUtils;

/**
 * DeleteUserDataController — "Delete My Data" self-service action.
 *
 * DELETE /api/user/delete-data
 *
 * Wipes every row of usage data tied to the authenticated user — calculation
 * history, vault entries + encryption keys, password history, activity log,
 * favorites, metrics, saved regex patterns, tool recommendations, and
 * feedback — but leaves the user_table row (login credentials) intact, so
 * the account keeps working afterwards with a clean slate.
 *
 * FIX (unreachable endpoint): previously declared as {@code @WebServlet()}
 * with no path at all, so this controller could never actually be hit.
 *
 * FIX (authorization): previously read "username" from a request parameter,
 * meaning any authenticated user could pass a different username and wipe
 * that user's data. The username is now taken from UserContext (populated
 * by AuthFilter from the session) — the same pattern already used by
 * UpdatePasswordController and every other self-service endpoint.
 *
 * Guest sessions are blocked from this path by GuestRestrictionFilter.
 */
@WebServlet("/api/user/delete-data")
public class DeleteUserDataController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = new UnifiedLogger().writeLogs("dao");
    private final Gson gson = new Gson();

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            String username = UserContext.get();
            if (username == null || username.isBlank()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "You must be logged in to delete your data.",
                        "UNAUTHENTICATED")));
                return;
            }

            // ── 1. Check if user exists ───────────────────────────────────
            if (!UserDAO.checkIfUserExists(username)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print(gson.toJson(ApiResponse.fail(
                        "No account found for this username.",
                        "USER_NOT_FOUND")));
                return;
            }

            // ── 2. Delete all usage data (account itself is untouched) ─────
            boolean result = UserUtils.deleteUserData(username);

            // ── 3. If deletion does not succeed, show error message and let user try again
            if (!result) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print(gson.toJson(ApiResponse.fail(
                        "Could not delete your data. Try again.",
                        "INTERNAL_ERROR")));
            }

            // ── 4. If deletion succeeds, show success message ─────────────
            else {
                logger.info("Usage data deleted (self-service) for " + username);
                LinkedHashMap<String, String> data = new LinkedHashMap<>();
                data.put("message", "Successfully deleted your platform data.");
                response.setStatus(HttpServletResponse.SC_OK);
                out.print(gson.toJson(ApiResponse.ok(data)));
            }

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
