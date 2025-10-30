package numberanalyzer.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import numberanalyzer.categories.BaseNRepresentation;

@WebServlet("/BaseRepresentation")
public class BaseRepresentationController extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			HttpSession session = request.getSession();
			long number = (long) session.getAttribute("number");
			BaseNRepresentation bnr = new BaseNRepresentation();
			if(request.getParameter("number")!=null) {
				number = Long.parseLong(request.getParameter("number"));
				session.setAttribute("number", number);
			}
			
			String choice = request.getParameter("choice");
			
			if(choice.equals("all")) {
				LinkedHashMap<Integer, String> responseJson = bnr.findAllBases(number);
				try (PrintWriter out = response.getWriter()){
					out.print(responseJson);
					out.flush();
				}
			}
			else if(choice.equals("binary")) {
				String responseJson = bnr.getBinaryRepresentation(number);
				try (PrintWriter out = response.getWriter()){
					out.print(responseJson);
					out.flush();
				}
			}
			else if(choice.equals("octal")) {
				String responseJson = bnr.getOctalRepresentation(number);
				try (PrintWriter out = response.getWriter()){
					out.print(responseJson);
					out.flush();
				}
			}
			else if(choice.equals("hex")) {
				String responseJson = bnr.getHexRepresentation(number);
				try (PrintWriter out = response.getWriter()){
					out.print(responseJson);
					out.flush();
				}
			}
			else if(choice.equals("all in range")) {
				LinkedHashMap<Integer, LinkedHashMap<Integer, String>> responseJson = new LinkedHashMap<>();
				if(number<0) {
					for(int i=0;i>=number;i--) {
						LinkedHashMap<Integer, String> allBases = bnr.findAllBases(i);
						responseJson.put(i, allBases);
					}
				}
				else {
					for(int i=0;i<=number;i++) {
						LinkedHashMap<Integer, String> allBases = bnr.findAllBases(i);
						responseJson.put(i, allBases);
					}
				}
				try (PrintWriter out = response.getWriter()){
					out.print(responseJson);
					out.flush();
				}
			}
			else {
				throw new Exception("Invalid choice");
			}
		}
		catch(Exception e) {
			e.printStackTrace();
			String responseJSON = "Exception occured : "+e.getMessage();
			try (PrintWriter out = response.getWriter()){
				out.print(responseJSON);
				out.flush();
			}
		}
	}
}
