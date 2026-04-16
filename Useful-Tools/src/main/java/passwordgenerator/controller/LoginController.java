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
 * Sprint 6 security: session fixation prevention, account lockout, CSRF token.
 *
 * CHANGE — Cross-origin deployment fix (Vercel frontend / Railway backend):
 *
 *   1. CSRF cookie SameSite changed from Strict → None.
 *      SameSite=Strict would prevent the browser from sending the cookie on
 *      any cross-origin request, making the double-submit CSRF pattern
 *      impossible to use from a different domain.  SameSite=None allows the
 *      cookie to travel cross-origin while still requiring Secure (HTTPS).
 *
 *   2. csrfToken is now included in the JSON response body as well as the
 *      cookie.  In a cross-origin deployment the frontend JavaScript (running
 *      on Vercel) cannot read cookies set by the Railway backend via
 *      document.cookie because of the browser's same-origin cookie access
 *      policy.  Returning the token in the body lets AuthContext store it in
 *      sessionStorage so it can be attached to subsequent requests as the
 *      X-XSRF-TOKEN header without reading the unreachable cookie.
 *
 *      The cookie is still set because it remains the correct mechanism in
 *      same-origin (local development) deployments.
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
                UserDAO.recordFailedLogin(username);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "Incorrect password.", "INVALID_CREDENTIALS")));
                return;
            }

            // ── 5. Successful auth — reset lockout counter ─────────────────
            UserDAO.resetFailedAttempts(username);

            // ── 6. Session fixation prevention ────────────────────────────
            HttpSession session = request.getSession(true);
            request.changeSessionId();
            String sessionId = session.getId();
            session.setAttribute("username", username);
            
            System.out.println("[LoginController] Created session with ID: " + sessionId);

            // ── 6b. Manually add JSESSIONID with SameSite=None ──────────────
            // CRITICAL: Tomcat's session manager adds JSESSIONID at connector level,
            // which bypasses our wrapper. To ensure it has SameSite=None, we manually
            // create it via response.addCookie() which DOES go through our wrapper.
            //
            // Our wrapper will:
            // 1. Intercept this addCookie() call
            // 2. Apply SameSite=None attribute
            // 3. Skip any duplicate JSESSIONID that Tomcat adds later
            Cookie jsessionidCookie = new Cookie("JSESSIONID", sessionId);
            jsessionidCookie.setPath("/");
            jsessionidCookie.setSecure(true);
            jsessionidCookie.setHttpOnly(true);
            // Don't set SameSite here - let wrapper do it via setAttribute()
            response.addCookie(jsessionidCookie);
            System.out.println("[LoginController] Added JSESSIONID via addCookie() - wrapper will add SameSite=None");

            // ── 7. CSRF token ──────────────────────────────────────────────
            String csrfToken = UUID.randomUUID().toString();
            session.setAttribute("csrfToken", csrfToken);

            // Set XSRF-TOKEN cookie.
            // SameSite=None  — required for cross-origin (Vercel → Railway) so
            //                   the browser will include it on Railway-domain
            //                   requests from the Vercel page.
            // Secure=true    — mandatory when SameSite=None; Railway serves HTTPS.
            // HttpOnly=false — JS must be able to read it (double-submit pattern).
            //
            // NOTE: Even with SameSite=None, the cookie still belongs to the
            // Railway domain and cannot be read by JS on the Vercel domain via
            // document.cookie.  The csrfToken is therefore also returned in the
            // response body so AuthContext can store it in sessionStorage.
            Cookie csrfCookie = new Cookie("XSRF-TOKEN", csrfToken);
            csrfCookie.setSecure(true);
            csrfCookie.setPath("/");
            csrfCookie.setHttpOnly(false);
            csrfCookie.setAttribute("SameSite", "None"); // FIX: was "Strict"
            response.addCookie(csrfCookie);

            // ── 8. Return success — include csrfToken in body ──────────────
            // The frontend stores this in sessionStorage so it can attach it
            // as the X-XSRF-TOKEN header even when document.cookie cannot
            // read the Railway-domain cookie from Vercel JavaScript.
            LinkedHashMap<String, String> data = new LinkedHashMap<>();
            data.put("username",  username);
            data.put("csrfToken", csrfToken); // FIX: added for cross-origin support
            data.put("message",   "Login successful.");

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
