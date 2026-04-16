package passwordgenerator.controller;

import java.io.IOException;
import java.io.PrintWriter;

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
 * Clears the server session and the XSRF-TOKEN cookie on logout.
 *
 * CHANGE — Cross-origin deployment fix:
 *   To delete a cookie the browser already has, the clearing Set-Cookie header
 *   must match all attributes of the original cookie (Path, Secure, SameSite,
 *   HttpOnly).  LoginController now sets the XSRF-TOKEN cookie with
 *   SameSite=None and Secure=true; the clearing cookie here is updated to
 *   match, otherwise browsers on some platforms silently ignore the deletion.
 */
@WebServlet("/api/auth/logout")
public class LogoutController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }

            // Clear the CSRF cookie.
            // Attributes must match the original Set-Cookie exactly so the
            // browser actually removes it (RFC 6265 §5.3 step 11).
            Cookie csrfCookie = new Cookie("XSRF-TOKEN", "");
            csrfCookie.setPath("/");
            csrfCookie.setMaxAge(0);           // Instructs the browser to delete it.
            csrfCookie.setHttpOnly(false);
            csrfCookie.setSecure(true);        // FIX: match login cookie
            csrfCookie.setAttribute("SameSite", "None"); // FIX: was missing, must match login cookie
            response.addCookie(csrfCookie);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok("Logged out successfully.")));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Logout failed. Please try again.", "INTERNAL_ERROR")));
            }
        }
    }
}
