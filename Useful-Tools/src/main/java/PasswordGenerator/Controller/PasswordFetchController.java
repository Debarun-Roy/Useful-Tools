package passwordgenerator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import passwordgenerator.dao.UserPasswordDAO;

/**
 * FIX (package): Renamed from PasswordGenerator.Controller to passwordgenerator.controller.
 */
@WebServlet("/FetchPasswords")
public class PasswordFetchController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            String username = request.getParameter("username");
            Gson gson = new Gson();
            String jsonResponse;

            String choice = request.getParameter("choice");

            if (choice.equalsIgnoreCase("All Passwords")) {
                LinkedHashMap<Integer, LinkedHashMap<String, String>> userPasswords =
                        UserPasswordDAO.fetchUserPasswords(username);
                jsonResponse = gson.toJson(userPasswords);
            } else {
                String platform = request.getParameter("platform");
                LinkedHashMap<String, String> password =
                        UserPasswordDAO.fetchUserPlatformPassword(username, platform);
                jsonResponse = gson.toJson(password);
            }

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            try (PrintWriter out = response.getWriter()) {
                out.print(jsonResponse);
                out.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
            request.getRequestDispatcher("/errorPageReloader.jsp").forward(request, response);
        }
    }
}
