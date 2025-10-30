package numberanalyzer.utilities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import jakarta.servlet.http.HttpServletRequest;

public class JsonBodyParser {

	public String getRequestBodyAsString(HttpServletRequest request) throws IOException{
		StringBuilder req = new StringBuilder();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(request.getInputStream()));
			String line;
			while((line = reader.readLine()) != null) {
				req.append(line);
			}
		}
		finally {
			if(reader != null) {
				try {
					reader.close();
				}
				catch(IOException ioe) {
					ioe.printStackTrace();
				}
			}
		}
		return req.toString();
	}
}
