package passwordgenerator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;
import common.UnifiedLogger;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import passwordgenerator.dao.UserPasswordDAO;
import passwordgenerator.models.PasswordModel;
import passwordgenerator.utilities.PasswordGeneratorUtils;

/**
 * FIX (NullPointerException): generatedPassword was initialised to null and
 *   Stream.concat(null, ...) was called on the first iteration, immediately
 *   throwing NPE. Fixed by building an empty Stream<Character> as the initial
 *   accumulator and concatenating onto it.
 *
 * FIX (wrong boolean flag for uppercase/lowercase): getRandomAlphabets(n, true)
 *   means lowercase — the original controller called getRandomAlphabets(n, true)
 *   for "Uppercase Alphabets" and getRandomAlphabets(n, false) for "Lowercase
 *   Alphabets", swapping upper and lower case output. Corrected.
 *
 * FIX (model): Updated setSpecialCharactercount → setSpecialCharacterCount.
 *
 * FIX (package + imports): Renamed packages throughout.
 */
@WebServlet("/ProcessForm1")
public class PasswordGenerationController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = new UnifiedLogger().writeLogs("controller");

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            String username = request.getParameter("username");
            String platform = request.getParameter("platform");

            PasswordModel pass = new PasswordModel();
            pass.setUsername(username);
            pass.setPlatform(platform);

            int length = Integer.parseInt(request.getParameter("length"));
            logger.info("User " + username + " wants a password of length " + length);

            // FIX: Start with an empty stream — never null — so the first concat is safe.
            Stream<Character> generatedPassword = Stream.empty();

            String choice = request.getParameter("customize_password");
            if (choice.equalsIgnoreCase("custom")) {
                String[] var = request.getParameterValues("customization_checkboxes");
                for (String category : var) {
                    int count = Integer.parseInt(request.getParameter(category));
                    if (category.equalsIgnoreCase("Numbers")) {
                        pass.setNumberCount(count);
                        generatedPassword = Stream.concat(generatedPassword,
                                PasswordGeneratorUtils.getRandomNumbers(count));
                    } else if (category.equalsIgnoreCase("Special Characters")) {
                        pass.setSpecialCharacterCount(count);
                        generatedPassword = Stream.concat(generatedPassword,
                                PasswordGeneratorUtils.getRandomSpecialChars(count));
                    } else if (category.equalsIgnoreCase("Uppercase Alphabets")) {
                        pass.setUppercaseCount(count);
                        // FIX: false = uppercase (original had true here, producing lowercase)
                        generatedPassword = Stream.concat(generatedPassword,
                                PasswordGeneratorUtils.getRandomAlphabets(count, false));
                    } else if (category.equalsIgnoreCase("Lowercase Alphabets")) {
                        pass.setLowercaseCount(count);
                        // FIX: true = lowercase (original had false here, producing uppercase)
                        generatedPassword = Stream.concat(generatedPassword,
                                PasswordGeneratorUtils.getRandomAlphabets(count, true));
                    } else {
                        logger.warning("Unknown password category: " + category);
                    }
                }
            } else {
                int[] result = PasswordGeneratorUtils.getRandomizedValues(length);
                generatedPassword = Stream.concat(
                        PasswordGeneratorUtils.getRandomNumbers(result[0]),
                        Stream.concat(
                                PasswordGeneratorUtils.getRandomSpecialChars(result[1]),
                                Stream.concat(
                                        PasswordGeneratorUtils.getRandomAlphabets(result[2], true),
                                        PasswordGeneratorUtils.getRandomAlphabets(result[3], false))));
            }

            List<Character> listOfChar = generatedPassword.collect(Collectors.toList());
            Collections.shuffle(listOfChar);
            String password = listOfChar.stream()
                    .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                    .toString();

            pass.setPassword(password);
            pass.setGeneratedTimestamp(Timestamp.from(Instant.now()));
            UserPasswordDAO.saveGeneratedPasswordDetails(pass);

            request.setAttribute("username", username);
            request.setAttribute("password", password);
            request.setAttribute("platform", platform);

            String jsonResponse = new Gson().toJson(password);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            try (PrintWriter out = response.getWriter()) {
                out.print(jsonResponse);
                out.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
            request.getRequestDispatcher("/errorPageReloader.jsp").forward(request, response);
        }
    }
}
