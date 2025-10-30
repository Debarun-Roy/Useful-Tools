package PasswordGenerator.Controller;

import java.io.IOException;

import PasswordGenerator.DAO.UserDAO;
import PasswordGenerator.Utilities.HashingUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/UpdatePassword")
public class UpdatePasswordController extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		try {
			String username = request.getParameter("username");
			String password = request.getParameter("updated_password");
			
			String hashedPassword = HashingUtils.generateHashedPassword(password);
			
			UserDAO.updateUserDetails(username, hashedPassword);
			request.getRequestDispatcher("/success.jsp").forward(request, response);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

}
