package user.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

import com.google.gson.Gson;

import common.ApiResponse;
import common.UnifiedLogger;
import common.UserContext;
import common.dao.RoleDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import passwordgenerator.dao.UserDAO;
import user.utilities.UserUtils;

/**
 * RemoveAccountController — "Remove My Account" self-service action.
 *
 * DELETE /api/user/remove-account
 *
 * Deletes every row tied to the authenticated user, including the
 * user_table row itself (see UserUtils.deleteAccountAndData), then
 * invalidates the session and clears the CSRF cookie exactly like
 * LogoutController, so the browser ends the request fully signed out.
 *
 * Security: the account to delete is taken from UserContext (populated by
 * AuthFilter from the session) — never from a request parameter. One
 * authenticated user must never be able to remove another user's account
 * through this endpoint.
 *
 * Guardrail: refuses if the caller is the sole remaining admin, so a lone
 * admin can't accidentally lock the team out of the admin panel — the same
 * "last admin" protection RoleDAO already applies to admin-initiated role
 * changes and deletions. (The hardcoded bootstrap admin, Deba_exe, would
 * regain admin automatically on next registration via RoleDAO.ensureSchema,
 * but this guardrail still matters for any OTHER admin who is the last one
 * standing.)
 *
 * Guest sessions are blocked from this path by GuestRestrictionFilter.
 */
@WebServlet("/api/user/remove-account")
public class RemoveAccountController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = new UnifiedLogger().writeLogs("dao");
    private final Gson gson = new Gson();

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            // ── 1. Authenticate via UserContext (session-derived) ──────────
            String username = UserContext.get();
            if (username == null || username.isBlank()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "You must be logged in to remove your account.",
                        "UNAUTHENTICATED")));
                return;
            }

            // ── 2. Check if user exists ─────────────────────────────────────
            if (!UserDAO.checkIfUserExists(username)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print(gson.toJson(ApiResponse.fail(
                        "No account found for this username.", "USER_NOT_FOUND")));
                return;
            }

            // ── 3. Last-admin guardrail ──────────────────────────────────────
            if (RoleDAO.isSoleAdmin(username)) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                out.print(gson.toJson(ApiResponse.fail(
                        "You are the only admin. Promote another user to admin "
                        + "before removing your own account.",
                        "LAST_ADMIN")));
                return;
            }

            // ── 4. Delete the account and all its data ──────────────────────
            boolean deleted = UserUtils.deleteAccountAndData(username);
            if (!deleted) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print(gson.toJson(ApiResponse.fail(
                        "Could not remove your account. Please try again.",
                        "INTERNAL_ERROR")));
                return;
            }

            logger.info("Account removed (self-service): " + username);

            // ── 5. Sign the browser out — mirrors LogoutController ──────────
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }

            Cookie csrfCookie = new Cookie("XSRF-TOKEN", "");
            csrfCookie.setPath("/");
            csrfCookie.setMaxAge(0);
            csrfCookie.setHttpOnly(false);
            csrfCookie.setSecure(true);
            csrfCookie.setAttribute("SameSite", "None");
            response.addCookie(csrfCookie);

            LinkedHashMap<String, String> data = new LinkedHashMap<>();
            data.put("message", "Your account and all associated data have been deleted.");

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
