package common.controller;

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
 * Diagnostic endpoint for testing cookie configuration.
 * 
 * GET /api/test/cookie-diagnostic
 * 
 * This endpoint:
 * 1. Sets a test cookie with SameSite=None
 * 2. Returns information about the current request's cookies
 * 3. Returns information about the current session
 * 
 * Use this to verify that:
 * - Cookies are being set with SameSite=None
 * - SessionID cookie exists and has correct attributes
 * - JSESSIONID is being transmitted in cross-origin requests
 */
@WebServlet("/api/test/cookie-diagnostic")
public class CookieDiagnosticController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            System.out.println("\n================== COOKIE DIAGNOSTIC ==================");
            
            // Set a test cookie
            Cookie testCookie = new Cookie("TEST-COOKIE", "test-value-" + System.currentTimeMillis());
            testCookie.setSecure(true);
            testCookie.setHttpOnly(false);
            testCookie.setPath("/");
            response.addCookie(testCookie);
            System.out.println("Test cookie added to response");

            // Get session info
            HttpSession session = request.getSession(false);
            String sessionId = session != null ? session.getId() : null;
            String username = session != null ? (String) session.getAttribute("username") : null;
            
            System.out.println("Request Cookies:");
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    System.out.println("  - " + cookie.getName() + " = " + cookie.getValue());
                }
            } else {
                System.out.println("  (No cookies in request)");
            }
            
            System.out.println("Session Info:");
            System.out.println("  - SessionID: " + (sessionId != null ? sessionId : "(No session)"));
            System.out.println("  - Username:  " + (username != null ? username : "(Not authenticated)"));
            System.out.println("  - Auth Status: " + (session != null && username != null ? "AUTHENTICATED" : "UNAUTHENTICATED"));
            
            System.out.println("Response Headers Being Set:");
            System.out.println("  - Set-Cookie: TEST-COOKIE=... (with SameSiteFilter applied)");
            System.out.println("======================================================\n");

            // Build response
            var data = new java.util.LinkedHashMap<>();
            data.put("message", "Cookie diagnostic endpoint - check server logs for details");
            data.put("sessionId", sessionId);
            data.put("authenticated", username != null);
            data.put("username", username != null ? username : "null");
            data.put("requestCookieCount", cookies != null ? cookies.length : 0);
            
            if (cookies != null) {
                var cookieList = new java.util.ArrayList<>();
                for (Cookie c : cookies) {
                    var info = new java.util.LinkedHashMap<>();
                    info.put("name", c.getName());
                    info.put("value", c.getValue().substring(0, Math.min(20, c.getValue().length())) + "...");
                    info.put("secure", c.getSecure());
                    info.put("httpOnly", c.isHttpOnly());
                    cookieList.add(info);
                }
                data.put("cookies", cookieList);
            }

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            System.out.println("ERROR in CookieDiagnosticController: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print(gson.toJson(ApiResponse.fail(
                    "Failed to generate diagnostic", "DIAGNOSTIC_ERROR")));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // POST not supported, redirect to GET
        doGet(request, response);
    }
}
