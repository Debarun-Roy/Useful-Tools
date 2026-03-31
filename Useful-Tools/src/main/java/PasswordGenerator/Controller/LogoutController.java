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
 * Sprint 6 addition: clears the XSRF-TOKEN cookie on logout by setting its
 * max-age to 0. This is a defence-in-depth measure — the cookie becomes
 * invalid immediately when the server session is invalidated regardless, but
 * explicitly clearing it prevents the stale cookie from lingering in the
 * browser and causing confusing CSRF failures on the next login.
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

            // Clear the CSRF cookie (Sprint 6).
            Cookie csrfCookie = new Cookie("XSRF-TOKEN", "");
            csrfCookie.setPath("/");
            csrfCookie.setMaxAge(0);       // Instructs the browser to delete it.
            csrfCookie.setHttpOnly(false);
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