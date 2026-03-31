package passwordgenerator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

import com.google.gson.Gson;
import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import passwordgenerator.dao.UserDAO;
import passwordgenerator.dao.UserDAO.LockStatus;
import passwordgenerator.utilities.LoginUtils;

/**
 * Sprint 6 security additions:
 *
 * CHANGE 1 — Session fixation prevention:
 *   After BCrypt verification succeeds, request.changeSessionId() is called
 *   to swap the session ID before writing the authenticated username into the
 *   session. This ensures an attacker who knows the pre-login session ID
 *   cannot inherit the authenticated session.
 *
 * CHANGE 2 — Account lockout:
 *   Before verifying the password, the account's lock status is checked.
 *   If the account is locked (>= 5 consecutive failures within 15 minutes),
 *   HTTP 403 is returned immediately without performing a BCrypt check.
 *   On a wrong password, recordFailedLogin() increments the counter.
 *   On a correct password, resetFailedAttempts() clears the counter.
 *
 * CHANGE 3 — CSRF token generation:
 *   After successful login, a UUID CSRF token is generated, stored in the
 *   session as "csrfToken", and sent as the "XSRF-TOKEN" cookie.
 *   The cookie is NOT HttpOnly so JavaScript can read it (required for the
 *   double-submit cookie pattern used by CsrfFilter).
 *   SameSite=Strict prevents the cookie from being sent in cross-site requests.
 */
@WebServlet("/api/auth/login")
public class LoginController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            String username = request.getParameter("username");
            String password = request.getParameter("password");

            // ── 1. Validate input ──────────────────────────────────────────
            if (username == null || username.isBlank()
                    || password == null || password.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Username and password are required.", "MISSING_CREDENTIALS")));
                return;
            }

            // ── 2. Check user exists ───────────────────────────────────────
            if (!UserDAO.checkIfUserExists(username)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print(gson.toJson(ApiResponse.fail(
                        "No account found for this username. Please register.",
                        "USER_NOT_FOUND")));
                return;
            }

            // ── 3. Account lockout check (Sprint 6) ───────────────────────
            LockStatus lockStatus = UserDAO.getLockStatus(username);
            if (lockStatus.locked) {
                long remainingSec = Math.max(1L,
                        (Instant.parse(lockStatus.lockedUntil).toEpochMilli()
                         - Instant.now().toEpochMilli()) / 1000L);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                out.print(gson.toJson(ApiResponse.fail(
                        "Account temporarily locked due to too many failed attempts. "
                        + "Try again in " + remainingSec + " seconds.",
                        "ACCOUNT_LOCKED")));
                return;
            }

            // ── 4. Verify password ─────────────────────────────────────────
            String storedHash = UserDAO.getStoredHashPassword(username);
            if (!LoginUtils.verifyUser(password, storedHash)) {
                // Record failure — this may trigger a lock on the 5th attempt.
                UserDAO.recordFailedLogin(username);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "Incorrect password.", "INVALID_CREDENTIALS")));
                return;
            }

            // ── 5. Successful auth — reset lockout counter (Sprint 6) ──────
            UserDAO.resetFailedAttempts(username);

            // ── 6. Session fixation prevention (Sprint 6) ─────────────────
            // changeSessionId() swaps the session ID while preserving data.
            // Must be called before writing authenticated state.
            HttpSession session = request.getSession(true);
            request.changeSessionId();
            session.setAttribute("username", username);

            // ── 7. CSRF token (Sprint 6) ───────────────────────────────────
            String csrfToken = UUID.randomUUID().toString();
            session.setAttribute("csrfToken", csrfToken);

            // Send XSRF-TOKEN cookie — HttpOnly=false so JS can read it.
            // SameSite=Strict prevents cross-site cookie submission.
            // NOTE: setSecure(true) should be enabled when serving over HTTPS.
            Cookie csrfCookie = new Cookie("XSRF-TOKEN", csrfToken);
            csrfCookie.setPath("/");
            csrfCookie.setHttpOnly(false);
            csrfCookie.setAttribute("SameSite", "Strict");
            response.addCookie(csrfCookie);

            // ── 8. Return success ──────────────────────────────────────────
            LinkedHashMap<String, String> data = new LinkedHashMap<>();
            data.put("username", username);
            data.put("message",  "Login successful.");

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