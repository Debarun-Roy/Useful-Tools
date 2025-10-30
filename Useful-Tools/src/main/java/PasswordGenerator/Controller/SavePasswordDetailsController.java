package PasswordGenerator.Controller;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

import PasswordGenerator.DAO.UserPasswordDAO;
import PasswordGenerator.Logging.UnifiedLogger;
import PasswordGenerator.Models.PasswordModel;
import PasswordGenerator.Utilities.EncryptionUtils;
import PasswordGenerator.Utilities.HashingUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/SavePassword")
public class SavePasswordDetailsController extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		try {
			UnifiedLogger ul = new UnifiedLogger();
			Logger logger = ul.writeLogs("controller");
			String username = request.getParameter("username");
			String password = request.getParameter("password");
			String platform = request.getParameter("platform");

			PasswordModel pass = new PasswordModel();
			pass.setUsername(username);
			pass.setPlatform(platform);
			LinkedHashMap<String, String> encryption = new LinkedHashMap<>();
			encryption = EncryptionUtils.generateEncryptedPassword(password);
			pass.setEncryptedPassword(encryption.get("encrypted_password"));
			pass.setPrivateKey(encryption.get("private_key"));

			String hashedPassword = HashingUtils.generateHashedPassword(password);
			pass.setHashedPassword(hashedPassword);
			pass.setCreatedDate(Instant.now());

			UserPasswordDAO.saveUserPasswordDetails(pass);
			UserPasswordDAO.saveEncryptionDetails(pass);
			logger.info("User password details stored in table successfully");

			request.setAttribute("username", username);
			request.setAttribute("platform", platform);

			request.getRequestDispatcher("/success.jsp").forward(request, response);
		}
		catch(Exception e) {
			e.printStackTrace();
			request.getRequestDispatcher("/errorPageReloader.jsp").forward(request, response);
		}
	}

}
