package passwordgenerator.controller;

import java.io.IOException;
import java.io.PrintWriter;
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

/**
 * Guest Mode Login Controller
 *
 * Allows users to access tools without creating an account. Guest sessions:
 * - Generate a unique guest ID: "guest_<UUID>"
 * - Have access to tools (calculator, encoders, etc)
 * - DO NOT have access to personalized features (vault, profile)
 * - Persist across browser restarts (sessionStorage/localStorage)
 * - Create a valid session with CSRF token (like regular login)
 *
 * Endpoint: POST /api/auth/login-guest
 * Public: YES (no authentication required)
 * Parameters: None required
 *
 * Response 200 OK:
 * {
 *   "success": true,
 *   "data": {
 *     "username": "guest_550e8400-e29b-41d4-a716-446655440000",
 *     "csrfToken": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
 *     "isGuest": true
 *   }
 * }
 */
@WebServlet("/api/auth/login-guest")
public class GuestLoginController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            // ── 1. Generate unique guest username ──────────────────────────
            // Format: guest_<UUID> to ensure uniqueness per session
            String guestId = "guest_" + UUID.randomUUID().toString().replace("-", "");
            System.out.println("[GuestLoginController] Generated guest ID: " + guestId);

            // ── 2. Create/get session ──────────────────────────────────────
            HttpSession session = request.getSession(true);
            String sessionId = session.getId();
            session.setAttribute("username", guestId);
            System.out.println("[GuestLoginController] Created session with ID: " + sessionId);

            // ── 3. Manually add JSESSIONID with SameSite=None ────────────────
            // (Same fix as regular login — ensures cross-origin cookie works)
            Cookie jsessionidCookie = new Cookie("JSESSIONID", sessionId);
            jsessionidCookie.setPath("/");
            jsessionidCookie.setSecure(true);
            jsessionidCookie.setHttpOnly(true);
            response.addCookie(jsessionidCookie);
            System.out.println("[GuestLoginController] Added JSESSIONID via addCookie()");

            // ── 4. Generate CSRF token ────────────────────────────────────
            String csrfToken = UUID.randomUUID().toString();
            session.setAttribute("csrfToken", csrfToken);

            // ── 5. Set XSRF-TOKEN cookie ──────────────────────────────────
            Cookie csrfCookie = new Cookie("XSRF-TOKEN", csrfToken);
            csrfCookie.setSecure(true);
            csrfCookie.setPath("/");
            csrfCookie.setHttpOnly(false);
            csrfCookie.setAttribute("SameSite", "None");
            response.addCookie(csrfCookie);

            // ── 6. Return success response ────────────────────────────────
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("username", guestId);
            data.put("csrfToken", csrfToken);
            data.put("isGuest", true);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

            System.out.println("[GuestLoginController] Guest login successful: " + guestId);

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print(gson.toJson(ApiResponse.fail(
                    "Failed to create guest session.",
                    "INTERNAL_ERROR")));
        }
    }
}
