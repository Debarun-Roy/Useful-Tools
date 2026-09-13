package passwordgenerator.controller;

import java.io.IOException;
import java.io.PrintWriter;

import com.google.gson.Gson;

import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import passwordgenerator.dao.UserDAO;

@WebServlet()
public class AdminRecoveryCodeController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

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
        }
    }
}
