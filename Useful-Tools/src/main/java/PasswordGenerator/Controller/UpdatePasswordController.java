package passwordgenerator.controller;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import passwordgenerator.dao.UserDAO;
import passwordgenerator.utilities.HashingUtils;

/**
 * FIX (package): Renamed from PasswordGenerator.Controller to passwordgenerator.controller.
 */
@WebServlet("/UpdatePassword")
public class UpdatePasswordController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            String username = request.getParameter("username");
            String password = request.getParameter("updated_password");

            String hashedPassword = HashingUtils.generateHashedPassword(password);
            UserDAO.updateUserDetails(username, hashedPassword);
            request.getRequestDispatcher("/success.jsp").forward(request, response);
        } catch (Exception e) {
            e.printStackTrace();
            request.getRequestDispatcher("/errorPageReloader.jsp").forward(request, response);
        }
    }
}
