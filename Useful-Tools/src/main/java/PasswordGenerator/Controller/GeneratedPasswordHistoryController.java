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
import passwordgenerator.dao.UserPasswordDAO;

/**
 * Returns paginated generated-password history for the logged-in user.
 *
 * GET /api/passwords/generated-history?page=0&size=12
 */
@WebServlet("/api/passwords/generated-history")
public class GeneratedPasswordHistoryController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_SIZE = 12;
    private static final int MAX_SIZE = 50;
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        try {
            HttpSession session = request.getSession(false);
            String username = (session != null)
                    ? (String) session.getAttribute("username")
                    : null;

            if (username == null || username.isBlank()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "You must be logged in to view generated-password history.",
                        "UNAUTHENTICATED")));
                return;
            }

            int page = Math.max(0, parseIntParam(request.getParameter("page"), 0));
            int size = Math.min(
                    Math.max(1, parseIntParam(request.getParameter("size"), DEFAULT_SIZE)),
                    MAX_SIZE);

            LinkedHashMap<String, Object> data =
                    UserPasswordDAO.fetchGeneratedPasswordHistory(username.trim(), page, size);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(gson.toJson(ApiResponse.fail(
                    "Failed to fetch generated-password history.",
                    "INTERNAL_ERROR")));
        }
    }

    private int parseIntParam(String val, int defaultVal) {
        if (val == null || val.isBlank()) return defaultVal;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
