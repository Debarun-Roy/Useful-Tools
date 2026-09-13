package user.controller;

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
import passwordgenerator.dao.UserDAO;
import user.utilities.UserUtils;

@WebServlet()
public class DeleteUserDataController extends HttpServlet {
    
    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {
            
            String username = request.getParameter("username");
            if (username == null || username.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Username is required.",
                        "USERNAME_REQUIRED")));
                return;
            }

            // ── 1. Check if user exists ───────────────────────────────────
            if (!UserDAO.checkIfUserExists(username)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print(gson.toJson(ApiResponse.fail(
                        "No account found for this username.",
                        "USER_NOT_FOUND")));
                return;
            }

            // ── 2. Delete all user records from all tables ────────────────
            boolean result = UserUtils.deleteUserData(username);

            // ── 3. If deletion does not succeed, show error message and let user try again
            if (!result){
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print(gson.toJson(ApiResponse.fail(
                        "Could not delete user records. Try again.",
                        "SC_INTERNAL_SERVER_ERROR")));
            }

            // ── 4. If deletion succeeds, show success message ─────────────
            else{
                LinkedHashMap<String, String> data = new LinkedHashMap<>();
                data.put("message", "Successfully deleted user records.");
                response.setStatus(HttpServletResponse.SC_OK);
                out.print(gson.toJson(ApiResponse.ok(data)));
            }
        }
    }
}
