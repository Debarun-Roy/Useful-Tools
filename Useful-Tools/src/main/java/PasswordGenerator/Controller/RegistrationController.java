package PasswordGenerator.Controller;

import java.io.IOException;

import PasswordGenerator.DAO.UserDAO;
import PasswordGenerator.Utilities.HashingUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/Registration")
public class RegistrationController extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			String username = request.getParameter("username");
			String password = request.getParameter("password");
			
			String hashedPassword = HashingUtils.generateHashedPassword(password);
			UserDAO.registerUser(username, hashedPassword);
			request.getRequestDispatcher("/Login").forward(request, response);
		}
		catch(Exception e) {
			e.printStackTrace();
			request.getRequestDispatcher("/errorPageReloader.jsp").forward(request, response);
		}
	}
}