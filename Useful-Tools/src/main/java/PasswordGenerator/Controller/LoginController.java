package passwordgenerator.controller;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import passwordgenerator.dao.UserDAO;
import passwordgenerator.utilities.LoginUtils;

/**
 * FIX (package): Renamed from PasswordGenerator.Controller to passwordgenerator.controller.
 * FIX (style): Replaced "== false" comparisons with the idiomatic "!" operator.
 */
@WebServlet("/Login")
public class LoginController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            String username = request.getParameter("username");
            String password = request.getParameter("password");

            if (!UserDAO.checkIfUserExists(username)) {
                request.getRequestDispatcher("/registration.jsp").forward(request, response);
            } else {
                String storedHashPassword = UserDAO.getStoredHashPassword(username);
                if (!LoginUtils.verifyUser(password, storedHashPassword)) {
                    request.getRequestDispatcher("/errorPage.jsp").forward(request, response);
                } else {
                    request.getRequestDispatcher("/indexPage.jsp").forward(request, response);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
