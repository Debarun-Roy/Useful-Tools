package numberanalyzer.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import numberanalyzer.categories.BaseNRepresentation;

/**
 * Returns base-N representations for a single number or a range 0..N.
 *
 * This controller is kept specifically for its "all in range" capability
 * (choice = "all in range"), which iterates from 0 to N and returns all
 * base representations for every number in the range. This is not available
 * through any other endpoint.
 *
 * For single-number base lookups (binary/octal/hex), prefer using the
 * NumberAnalysisDisplayController response which includes base representations
 * as part of the full classification result.
 *
 * FIX 1 — NullPointerException on first request:
 *   The original did:
 *     long number = (long) session.getAttribute("number");
 *   On the first request, getAttribute() returns null. Unboxing null to a
 *   primitive long throws NullPointerException before the parameter check
 *   even runs. Fixed: use boxed Long with a null-guard, defaulting to 0.
 *
 * FIX 2 — Non-JSON responses:
 *   All branches called out.print(responseJson) on Map and String objects,
 *   which calls Map.toString() / String.toString() producing Java format
 *   (e.g. "{1=0b101}") rather than valid JSON. Fixed: Gson.toJson() applied
 *   to every response.
 *
 * FIX 3 — Error response was a plain string "Exception occured : ...".
 *   Now returns proper JSON so the client can parse it consistently.
 */
@WebServlet("/BaseRepresentation")
public class BaseRepresentationController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            HttpSession session = request.getSession();

            // FIX: use boxed Long — unboxing null throws NPE.
            Long number = (Long) session.getAttribute("number");
            if (number == null) number = 0L;

            String numberParam = request.getParameter("number");
            if (numberParam != null && !numberParam.isBlank()) {
                number = Long.parseLong(numberParam.trim());
                session.setAttribute("number", number);
            }

            BaseNRepresentation bnr = new BaseNRepresentation();
            String choice = request.getParameter("choice");

            if (choice == null) {
                throw new IllegalArgumentException("Parameter 'choice' is required.");
            }

            try (PrintWriter out = response.getWriter()) {
                switch (choice.toLowerCase()) {

                    case "all": {
                        // FIX: was out.print(responseJson) — produced "{1=0b...}" not JSON
                        LinkedHashMap<Integer, String> allBases = bnr.findAllBases(number);
                        out.print(gson.toJson(allBases));
                        break;
                    }

                    case "binary": {
                        String binary = bnr.getBinaryRepresentation(number);
                        out.print(gson.toJson(binary));
                        break;
                    }

                    case "octal": {
                        String octal = bnr.getOctalRepresentation(number);
                        out.print(gson.toJson(octal));
                        break;
                    }

                    case "hex": {
                        String hex = bnr.getHexRepresentation(number);
                        out.print(gson.toJson(hex));
                        break;
                    }

                    case "all in range": {
                        // The unique capability of this controller.
                        // Returns every base representation for every number 0..N
                        // (or N..0 for negative N).
                        LinkedHashMap<Long, LinkedHashMap<Integer, String>> rangeResult
                                = new LinkedHashMap<>();
                        if (number < 0) {
                            for (long i = 0; i >= number; i--) {
                                rangeResult.put(i, bnr.findAllBases(i));
                            }
                        } else {
                            for (long i = 0; i <= number; i++) {
                                rangeResult.put(i, bnr.findAllBases(i));
                            }
                        }
                        // FIX: was out.print(responseJson) — produced Java Map format, not JSON
                        out.print(gson.toJson(rangeResult));
                        break;
                    }

                    default:
                        throw new IllegalArgumentException(
                                "Unknown choice: '" + choice + "'. Valid values: all, binary, octal, hex, all in range.");
                }
                out.flush();
            }

        } catch (NumberFormatException nfe) {
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson("Error: '" + request.getParameter("number")
                        + "' is not a valid integer."));
            }
        } catch (Exception e) {
            e.printStackTrace();
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson("Error: " + e.getMessage()));
            }
        }
    }
}