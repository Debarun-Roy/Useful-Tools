package PasswordGenerator.Controller;

import java.io.IOException;

import PasswordGenerator.DAO.UserDAO;
import PasswordGenerator.Utilities.LoginUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/Login")
public class LoginController extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		try {
			String username = request.getParameter("username");
			String password = request.getParameter("password");
			
			boolean verifyExistence = UserDAO.checkIfUserExists(username);
			if(verifyExistence == false) {
				request.getRequestDispatcher("/registration.jsp").forward(request, response);
			}
			else {
				String storedHashPassword = UserDAO.getStoredHashPassword(username);
				boolean isUserValid = LoginUtils.verifyUser(password, storedHashPassword);
				if(isUserValid == false) {
					request.getRequestDispatcher("/errorPage.jsp").forward(request, response);
				}
				else {
					request.getRequestDispatcher("/indexPage.jsp").forward(request, response);
				}
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
