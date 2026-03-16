package numberanalyzer.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import com.google.gson.Gson;
import common.ApiResponse;
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
 * CHANGE 5: All responses now wrapped in ApiResponse. HTTP status codes applied:
 *   400 for invalid input, 500 for server errors.
 * CHANGE 6: Path renamed /BaseRepresentation → /api/analyzer/base-representation
 *
 * All prior fixes from the final_changes batch are preserved:
 *   - Null-unboxing NPE on first request fixed (boxed Long)
 *   - Non-JSON Map.toString() responses replaced with Gson.toJson()
 */
@WebServlet("/api/analyzer/base-representation")
public class BaseRepresentationController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {
            HttpSession session = request.getSession();

            Long number = (Long) session.getAttribute("number");
            if (number == null) number = 0L;

            String numberParam = request.getParameter("number");
            if (numberParam != null && !numberParam.isBlank()) {
                try {
                    number = Long.parseLong(numberParam.trim());
                    session.setAttribute("number", number);
                } catch (NumberFormatException nfe) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print(gson.toJson(ApiResponse.fail(
                            "'" + numberParam + "' is not a valid integer.", "INVALID_NUMBER")));
                    return;
                }
            }

            String choice = request.getParameter("choice");
            if (choice == null || choice.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Parameter 'choice' is required. Valid values: all, binary, octal, hex, all in range.",
                        "MISSING_CHOICE")));
                return;
            }

            BaseNRepresentation bnr = new BaseNRepresentation();

            switch (choice.toLowerCase()) {
                case "all": {
                    LinkedHashMap<Integer, String> allBases = bnr.findAllBases(number);
                    response.setStatus(HttpServletResponse.SC_OK);
                    out.print(gson.toJson(ApiResponse.ok(allBases)));
                    break;
                }
                case "binary": {
                    String binary = bnr.getBinaryRepresentation(number);
                    response.setStatus(HttpServletResponse.SC_OK);
                    out.print(gson.toJson(ApiResponse.ok(binary)));
                    break;
                }
                case "octal": {
                    String octal = bnr.getOctalRepresentation(number);
                    response.setStatus(HttpServletResponse.SC_OK);
                    out.print(gson.toJson(ApiResponse.ok(octal)));
                    break;
                }
                case "hex": {
                    String hex = bnr.getHexRepresentation(number);
                    response.setStatus(HttpServletResponse.SC_OK);
                    out.print(gson.toJson(ApiResponse.ok(hex)));
                    break;
                }
                case "all in range": {
                    // Unique capability — returns base representations for every
                    // number in the range [0, N] (or [N, 0] for negative N).
                    LinkedHashMap<Long, LinkedHashMap<Integer, String>> rangeResult = new LinkedHashMap<>();
                    if (number < 0) {
                        for (long i = 0; i >= number; i--) {
                            rangeResult.put(i, bnr.findAllBases(i));
                        }
                    } else {
                        for (long i = 0; i <= number; i++) {
                            rangeResult.put(i, bnr.findAllBases(i));
                        }
                    }
                    response.setStatus(HttpServletResponse.SC_OK);
                    out.print(gson.toJson(ApiResponse.ok(rangeResult)));
                    break;
                }
                default: {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print(gson.toJson(ApiResponse.fail(
                            "Unknown choice: '" + choice + "'. Valid values: all, binary, octal, hex, all in range.",
                            "INVALID_CHOICE")));
                    break;
                }
            }
            out.flush();

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Base representation lookup failed. Please try again.", "INTERNAL_ERROR")));
            }
        }
    }
}