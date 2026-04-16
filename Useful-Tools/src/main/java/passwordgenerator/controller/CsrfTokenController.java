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

/**
 * Returns the CSRF token stored in the current session.
 *
 * WHY THIS EXISTS — cross-origin deployment problem:
 * In production the frontend (Vercel) and backend (Railway) are on different
 * domains.  The XSRF-TOKEN cookie is set by the Railway domain; JavaScript
 * running on Vercel cannot read it via document.cookie because browsers
 * enforce the same-origin policy on cookie access.
 *
 * The fix is a two-step strategy in the frontend:
 *   1. On login  — the csrfToken is included in the JSON response body and
 *                  stored in sessionStorage by AuthContext.
 *   2. On reload — sessionStorage survives page refreshes.  If for any
 *                  reason it is empty (first load after login elsewhere,
 *                  cleared storage, etc.) AuthContext calls this endpoint to
 *                  re-obtain the token from the still-valid server session.
 *
 * GET is a safe HTTP method; CsrfFilter skips it.
 * AuthFilter still enforces authentication (session must be valid).
 *
 * GET /api/auth/csrf-token
 *
 * Response 200:  { "success": true,  "data": { "csrfToken": "..." } }
 * Response 401:  { "success": false, "errorCode": "UNAUTHENTICATED" }
 * Response 404:  { "success": false, "errorCode": "NO_CSRF_TOKEN" }
 *   (404 means the session exists but pre-dates Sprint 6 and has no token —
 *    unlikely in production; just log in again to obtain one.)
 */
@WebServlet("/api/auth/csrf-token")
public class CsrfTokenController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        HttpSession session = request.getSession(false);
        String username  = (session != null) ? (String) session.getAttribute("username")  : null;
        String csrfToken = (session != null) ? (String) session.getAttribute("csrfToken") : null;

        if (username == null || username.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(gson.toJson(ApiResponse.fail(
                    "You must be logged in to retrieve a CSRF token.",
                    "UNAUTHENTICATED")));
            return;
        }

        if (csrfToken == null || csrfToken.isBlank()) {
            // Session predates Sprint 6 — ask the client to log in again.
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            out.print(gson.toJson(ApiResponse.fail(
                    "No CSRF token found in session. Please log in again.",
                    "NO_CSRF_TOKEN")));
            return;
        }

        LinkedHashMap<String, String> data = new LinkedHashMap<>();
        data.put("csrfToken", csrfToken);

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(data)));
    }
}
