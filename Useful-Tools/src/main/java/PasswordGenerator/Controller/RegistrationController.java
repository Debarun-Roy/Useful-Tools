package passwordgenerator.controller;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import passwordgenerator.dao.UserDAO;

/**
 * FIX (double-hashing): The original called HashingUtils.generateHashedPassword()
 *   here, then passed the already-hashed string into UserDAO.registerUser(),
 *   which called HashingUtils.generateHashedPassword() again internally — so the
 *   password was hashed twice. The hash of a hash is what got stored, making
 *   login verification permanently fail. The hashing now happens exactly once,
 *   inside UserDAO.registerUser(), so this controller simply passes the plain
 *   password. This is safe because UserDAO is a server-side class.
 *
 * FIX (package): Renamed from PasswordGenerator.Controller to passwordgenerator.controller.
 */
@WebServlet("/Registration")
public class RegistrationController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            String username = request.getParameter("username");
            String password = request.getParameter("password");

            // FIX: Pass plain password — UserDAO handles hashing internally.
            UserDAO.registerUser(username, password);
            request.getRequestDispatcher("/Login").forward(request, response);
        } catch (Exception e) {
            e.printStackTrace();
            request.getRequestDispatcher("/errorPageReloader.jsp").forward(request, response);
        }
    }
}
