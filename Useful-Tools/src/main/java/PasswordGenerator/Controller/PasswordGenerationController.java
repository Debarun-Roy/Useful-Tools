package passwordgenerator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;
import common.ApiResponse;
import common.UnifiedLogger;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import passwordgenerator.dao.UserPasswordDAO;
import passwordgenerator.models.PasswordModel;
import passwordgenerator.utilities.PasswordGeneratorUtils;

/**
 * CHANGE 6: Path renamed /ProcessForm1 → /api/passwords/generate
 *
 * Sprint 9 hardening:
 *   The authenticated username is now read from the HTTP session instead of
 *   trusting a request parameter. This prevents one logged-in user from
 *   writing generated-password history rows into another user's account.
 */
@WebServlet("/api/passwords/generate")
public class PasswordGenerationController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int MIN_LENGTH =   8;
    private static final int MAX_LENGTH = 128;
    private static final Logger logger = new UnifiedLogger().writeLogs("controller");
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {

            HttpSession session = request.getSession(false);
            String username = (session != null)
                    ? (String) session.getAttribute("username")
                    : null;
            String platform    = request.getParameter("platform");
            String lengthParam = request.getParameter("length");

            if (username == null || username.isBlank()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "You must be logged in to generate passwords.", "UNAUTHENTICATED")));
                return;
            }

            if (platform == null || platform.isBlank()
                    || lengthParam == null || lengthParam.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "platform and length are required.", "INVALID_PARAMETERS")));
                return;
            }

            int length;
            try {
                length = Integer.parseInt(lengthParam.trim());
            } catch (NumberFormatException nfe) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "length must be a valid integer.", "INVALID_PARAMETERS")));
                return;
            }

            if (length < MIN_LENGTH || length > MAX_LENGTH) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Password length must be between " + MIN_LENGTH
                                + " and " + MAX_LENGTH + " characters.",
                        "INVALID_PARAMETERS")));
                return;
            }

            PasswordModel pass = new PasswordModel();
            pass.setUsername(username.trim());
            pass.setPlatform(platform.trim());

            logger.info("User " + username + " requesting password of length " + length);

            Stream<Character> generatedPassword = Stream.empty();
            String choice = request.getParameter("customize_password");

            if ("custom".equalsIgnoreCase(choice)) {
                String[] categories = request.getParameterValues("customization_checkboxes");
                if (categories == null || categories.length == 0) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print(gson.toJson(ApiResponse.fail(
                            "At least one customization category must be selected.",
                            "INVALID_PARAMETERS")));
                    return;
                }
                for (String category : categories) {
                    String countParam = request.getParameter(category);
                    if (countParam == null) continue;
                    int count = Integer.parseInt(countParam.trim());
                    switch (category) {
                        case "Numbers":
                            pass.setNumberCount(count);
                            generatedPassword = Stream.concat(generatedPassword,
                                    PasswordGeneratorUtils.getRandomNumbers(count));
                            break;
                        case "Special Characters":
                            pass.setSpecialCharacterCount(count);
                            generatedPassword = Stream.concat(generatedPassword,
                                    PasswordGeneratorUtils.getRandomSpecialChars(count));
                            break;
                        case "Uppercase Alphabets":
                            pass.setUppercaseCount(count);
                            generatedPassword = Stream.concat(generatedPassword,
                                    PasswordGeneratorUtils.getRandomAlphabets(count, false));
                            break;
                        case "Lowercase Alphabets":
                            pass.setLowercaseCount(count);
                            generatedPassword = Stream.concat(generatedPassword,
                                    PasswordGeneratorUtils.getRandomAlphabets(count, true));
                            break;
                        default:
                            logger.warning("Unknown customization category: " + category);
                            break;
                    }
                }
            } else {
                int[] split = PasswordGeneratorUtils.getRandomizedValues(length);
                generatedPassword = Stream.concat(
                        PasswordGeneratorUtils.getRandomNumbers(split[0]),
                        Stream.concat(
                                PasswordGeneratorUtils.getRandomSpecialChars(split[1]),
                                Stream.concat(
                                        PasswordGeneratorUtils.getRandomAlphabets(split[2], true),
                                        PasswordGeneratorUtils.getRandomAlphabets(split[3], false))));
            }

            List<Character> chars = generatedPassword.collect(Collectors.toList());
            Collections.shuffle(chars);
            String password = chars.stream()
                    .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                    .toString();

            pass.setPassword(password);
            pass.setGeneratedTimestamp(Timestamp.from(Instant.now()));
            UserPasswordDAO.saveGeneratedPasswordDetails(pass);
            logger.info("Password generated for user=" + username + " platform=" + platform);

            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("password", password);
            data.put("length",   password.length());
            data.put("username", username.trim());
            data.put("platform", platform.trim());

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            logger.severe("Password generation failed: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Password generation failed. Please try again.", "INTERNAL_ERROR")));
            }
        }
    }
}
