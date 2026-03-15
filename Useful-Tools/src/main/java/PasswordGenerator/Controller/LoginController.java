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
import passwordgenerator.dao.UserDAO;
import passwordgenerator.utilities.LoginUtils;

/**
 * Handles user authentication.
 *
 * CHANGE 3 — All request.getRequestDispatcher().forward() calls have been
 * replaced with structured JSON responses using ApiResponse.
 *
 * Previous behaviour:
 *   - User not found  → forward to /registration.jsp
 *   - Wrong password  → forward to /errorPage.jsp
 *   - Success         → forward to /indexPage.jsp
 *   - Catch block     → silent swallow (e.printStackTrace() only, no response)
 *
 * New behaviour:
 *   - Missing params  → 400  { success:false, errorCode:"MISSING_CREDENTIALS" }
 *   - User not found  → 404  { success:false, errorCode:"USER_NOT_FOUND" }
 *   - Wrong password  → 401  { success:false, errorCode:"INVALID_CREDENTIALS" }
 *   - Success         → 200  { success:true,  data:{ username, message } }
 *   - Any exception   → 500  { success:false, errorCode:"INTERNAL_ERROR" }
 *
 * On success the authenticated username is written into the HTTP session so
 * the AuthFilter (Change 7) can validate subsequent requests.
 *
 * NOTE: This controller still reads credentials from form-encoded request
 * parameters (request.getParameter). Migration to a JSON request body will
 * be handled in a later sprint when React's fetch calls are wired up.
 */
@WebServlet("/Login")
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

            // ── Input validation ────────────────────────────────────────────
            if (username == null || username.isBlank()
                    || password == null || password.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Username and password are required.",
                        "MISSING_CREDENTIALS")));
                return;
            }

            // ── User existence check ────────────────────────────────────────
            boolean userExists = UserDAO.checkIfUserExists(username);
            if (!userExists) {
                // Do not reveal whether the username or password was wrong —
                // that leaks account existence. However, since your registration
                // flow is separate, "user not found" is acceptable here.
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print(gson.toJson(ApiResponse.fail(
                        "No account found for this username. Please register.",
                        "USER_NOT_FOUND")));
                return;
            }

            // ── Password verification ───────────────────────────────────────
            String storedHash = UserDAO.getStoredHashPassword(username);
            boolean valid = LoginUtils.verifyUser(password, storedHash);
            if (!valid) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "Incorrect password.",
                        "INVALID_CREDENTIALS")));
                return;
            }

            // ── Success ─────────────────────────────────────────────────────
            // Store username in session so AuthFilter can authenticate
            // subsequent requests without re-querying the database.
            HttpSession session = request.getSession(true);
            session.setAttribute("username", username);

            LinkedHashMap<String, String> data = new LinkedHashMap<>();
            data.put("username", username);
            data.put("message", "Login successful.");

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