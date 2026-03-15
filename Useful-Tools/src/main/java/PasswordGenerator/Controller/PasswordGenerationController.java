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
import passwordgenerator.dao.UserPasswordDAO;
import passwordgenerator.models.PasswordModel;
import passwordgenerator.utilities.PasswordGeneratorUtils;

/**
 * Generates a secure random password, persists the generation record,
 * and returns the password to the client.
 *
 * CHANGE 3 — The forward() call in the catch block has been replaced with a
 * structured JSON error response. The success path already returned JSON but
 * was not wrapped in ApiResponse — it now is.
 *
 * Previous behaviour:
 *   - Success      → raw gson.toJson(password) — a bare JSON string, no envelope
 *   - Catch block  → forward to /errorPageReloader.jsp
 *
 * New behaviour:
 *   - Missing/invalid params  → 400  { success:false, errorCode:"INVALID_PARAMETERS" }
 *   - Success                 → 200  { success:true,  data:{ password, length, ... } }
 *   - Any exception           → 500  { success:false, errorCode:"INTERNAL_ERROR" }
 *
 * BUG FIXES carried from prior review batches:
 *
 *   1. NullPointerException — generatedPassword was initialised to null.
 *      Stream.concat(null, ...) throws NPE on the first iteration of the
 *      "custom" path. Fixed: initialised to Stream.empty().
 *
 *   2. Uppercase/Lowercase swap — getRandomAlphabets(n, true) means lowercase.
 *      The original code called getRandomAlphabets(n, true) for Uppercase
 *      and getRandomAlphabets(n, false) for Lowercase — exactly backwards.
 *      Fixed: Uppercase passes false, Lowercase passes true.
 *
 *   3. setSpecialCharactercount → setSpecialCharacterCount (PasswordModel fix).
 *
 *   4. Removed stale request.setAttribute() calls that set attributes for
 *      JSP forwarding — they served no purpose in a JSON API.
 *
 *   5. Removed the large block of commented-out dead code.
 *
 * BOUNDS VALIDATION:
 *   Password length is capped at 8–128 characters. Lengths outside this range
 *   are rejected with a 400 response rather than silently producing an empty
 *   password or causing a server-side error.
 *
 * NOTE: This controller still reads parameters from form-encoded request data.
 * Migration to a JSON request body is handled in a later sprint.
 */
@WebServlet("/ProcessForm1")
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

            String username = request.getParameter("username");
            String platform = request.getParameter("platform");
            String lengthParam = request.getParameter("length");

            // ── Input validation ────────────────────────────────────────────
            if (username == null || username.isBlank()
                    || platform == null || platform.isBlank()
                    || lengthParam == null || lengthParam.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "username, platform, and length are required.",
                        "INVALID_PARAMETERS")));
                return;
            }

            int length;
            try {
                length = Integer.parseInt(lengthParam.trim());
            } catch (NumberFormatException nfe) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "length must be a valid integer.",
                        "INVALID_PARAMETERS")));
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

            // ── Build model ─────────────────────────────────────────────────
            PasswordModel pass = new PasswordModel();
            pass.setUsername(username.trim());
            pass.setPlatform(platform.trim());

            logger.info("User " + username + " requesting password of length " + length);

            // ── Generate password ───────────────────────────────────────────
            // FIX 1: initialise to Stream.empty() — was null, caused NPE on
            //         first Stream.concat() call in the custom path.
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
                            pass.setSpecialCharacterCount(count); // FIX 3: was setSpecialCharactercount
                            generatedPassword = Stream.concat(generatedPassword,
                                    PasswordGeneratorUtils.getRandomSpecialChars(count));
                            break;

                        case "Uppercase Alphabets":
                            pass.setUppercaseCount(count);
                            // FIX 2: false = uppercase. Original had true here (lowercase).
                            generatedPassword = Stream.concat(generatedPassword,
                                    PasswordGeneratorUtils.getRandomAlphabets(count, false));
                            break;

                        case "Lowercase Alphabets":
                            pass.setLowercaseCount(count);
                            // FIX 2: true = lowercase. Original had false here (uppercase).
                            generatedPassword = Stream.concat(generatedPassword,
                                    PasswordGeneratorUtils.getRandomAlphabets(count, true));
                            break;

                        default:
                            logger.warning("Unknown customization category: " + category);
                            break;
                    }
                }

            } else {
                // Auto-generate: split the requested length randomly across
                // the four character classes.
                int[] split = PasswordGeneratorUtils.getRandomizedValues(length);
                generatedPassword = Stream.concat(
                        PasswordGeneratorUtils.getRandomNumbers(split[0]),
                        Stream.concat(
                                PasswordGeneratorUtils.getRandomSpecialChars(split[1]),
                                Stream.concat(
                                        PasswordGeneratorUtils.getRandomAlphabets(split[2], true),   // lowercase
                                        PasswordGeneratorUtils.getRandomAlphabets(split[3], false))  // uppercase
                        ));
            }

            // ── Shuffle and assemble ────────────────────────────────────────
            List<Character> chars = generatedPassword.collect(Collectors.toList());
            Collections.shuffle(chars);
            String password = chars.stream()
                    .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                    .toString();

            pass.setPassword(password);
            pass.setGeneratedTimestamp(Timestamp.from(Instant.now()));

            // ── Persist generation record ───────────────────────────────────
            UserPasswordDAO.saveGeneratedPasswordDetails(pass);
            logger.info("Password generated for user=" + username + " platform=" + platform);

            // ── Success response ────────────────────────────────────────────
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
            // FIX: previously forwarded to /errorPageReloader.jsp — meaningless
            // to a JSON client and would return HTML instead of JSON.
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Password generation failed. Please try again.",
                        "INTERNAL_ERROR")));
            }
        }
    }
}