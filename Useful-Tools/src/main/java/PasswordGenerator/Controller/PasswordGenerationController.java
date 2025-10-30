package PasswordGenerator.Controller;
/**
 * 
 */

/**
 * 
 */


import java.sql.Timestamp;
import java.time.Instant;
import java.util.logging.Logger;
import java.util.Collections;
import java.util.List;   
import java.util.stream.Collectors;  
import java.util.stream.Stream;

import PasswordGenerator.DAO.UserPasswordDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.PrintWriter;

import PasswordGenerator.Logging.UnifiedLogger;
import PasswordGenerator.Utilities.PasswordGeneratorUtils;
import PasswordGenerator.Models.PasswordModel;
import com.google.gson.Gson;

@WebServlet("/ProcessForm1")
public class PasswordGenerationController extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		try {
			Stream<Character> generatedPassword = null;
			String username = request.getParameter("username");
			String platform = request.getParameter("platform");
			PasswordModel pass = new PasswordModel();
			Gson gson = new Gson();
			pass.setUsername(username);
			pass.setPlatform(platform);
			int length = Integer.parseInt(request.getParameter("length"));
			UnifiedLogger ul = new UnifiedLogger();
			Logger logger = ul.writeLogs("controller");
			logger.info("User "+username+" wants a password of length "+length);

			String choice = request.getParameter("customize_password");
			if(choice.equalsIgnoreCase("custom")) {
				String var[] = request.getParameterValues("customization_checkboxes");
				int arr[] = new int[var.length];
				for(int i=0;i<var.length;i++) {
					int count = Integer.parseInt(request.getParameter(var[i]));
					arr[i] = count;
				}

				for(int i=0;i<var.length;i++) {
					if(var[i].equalsIgnoreCase("Numbers")) {
						pass.setNumberCount(arr[i]);
						Stream<Character> numberStream = PasswordGeneratorUtils.getRandomNumbers(arr[i]);
						generatedPassword = Stream.concat(generatedPassword, numberStream);
						logger.info("Generated number stream of "+arr[i]+" length : "+numberStream);
						continue;
					}
					else if(var[i].equalsIgnoreCase("Special Characters")) {
						pass.setSpecialCharactercount(arr[i]);
						Stream<Character> specialCharStream = PasswordGeneratorUtils.getRandomSpecialChars(arr[i]);
						generatedPassword = Stream.concat(generatedPassword, specialCharStream);
						logger.info("Generated special character stream of "+arr[i]+" length : "+specialCharStream);
						continue;
					}
					else if(var[i].equalsIgnoreCase("Uppercase Alphabets")) {
						pass.setUppercaseCount(arr[i]);
						Stream<Character> uppercaseStream = PasswordGeneratorUtils.getRandomAlphabets(arr[i], true);
						generatedPassword = Stream.concat(generatedPassword, uppercaseStream);
						logger.info("Generated uppercase stream of "+arr[i]+" length : "+uppercaseStream);
						continue;
					}
					else if(var[i].equalsIgnoreCase("Lowercase Alphabets")) {
						pass.setLowercaseCount(arr[i]);
						Stream<Character> lowercaseStream = PasswordGeneratorUtils.getRandomAlphabets(arr[i], false);
						generatedPassword = Stream.concat(generatedPassword, lowercaseStream);
						logger.info("Generated lowercase stream of "+arr[i]+" length : "+lowercaseStream);
						continue;
					}
					else {
						//should throw a custom Exception here
						logger.warning("Unexpected error occured.");
					}
				}
			}
			else {
				int result[] = PasswordGeneratorUtils.getRandomizedValues(length);
				generatedPassword = Stream.concat(PasswordGeneratorUtils.getRandomNumbers(result[0]), Stream.concat(PasswordGeneratorUtils.getRandomSpecialChars(result[1]), Stream.concat(PasswordGeneratorUtils.getRandomAlphabets(result[2], false), PasswordGeneratorUtils.getRandomAlphabets(result[3], true))));
				logger.info("Generate password of length "+length+" : "+generatedPassword);
			}

			List<Character> listOfChar = generatedPassword.collect(Collectors.toList());
			Collections.shuffle(listOfChar);
			String password = listOfChar.stream()
					.<StringBuilder>collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)  
					.toString(); 
			pass.setPassword(password);
			pass.setGeneratedTimestamp(Timestamp.from(Instant.now()));

			UserPasswordDAO.saveGeneratedPasswordDetails(pass);

			request.setAttribute("username", username);
			request.setAttribute("password", password);
			request.setAttribute("platform", platform);
			String jsonResponse = gson.toJson(password);
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			
			try (PrintWriter out = response.getWriter()) {
				out.print(jsonResponse);
				out.flush();
			}
		}
		catch(Exception e) {
			e.printStackTrace();
			request.getRequestDispatcher("/errorPageReloader.jsp").forward(request, response);
//			response.setContentType("application/json");
//			response.setCharacterEncoding("UTF-8");
//			
//			try (PrintWriter out = response.getWriter()) {
//				out.print(e.getMessage());
//				out.flush();
//			}
		}
	}
}
