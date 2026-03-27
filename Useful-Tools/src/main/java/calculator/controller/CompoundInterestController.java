package calculator.controller;

import java.io.PrintWriter;
import java.util.LinkedHashMap;

import com.google.gson.JsonObject;
import calculator.dao.FinancialDAO;
import calculator.service.FinancialCalculatorService;
import common.ApiResponse;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletResponse;

/**
 * POST /api/calculator/compound-interest
 * Content-Type: application/json
 *
 * Request body:
 *   { "principal": 100000, "rate": 7.5, "time": 5, "frequency": "quarterly" }
 *
 * frequency: annually | semiannually | quarterly | monthly | daily
 *
 * Response 200:
 *   { "success": true, "data": {
 *       "principal": 100000, "finalAmount": 144994.06,
 *       "interestEarned": 44994.06, "effectiveRate": 7.7136,
 *       "frequency": "quarterly", "rate": 7.5, "time": 5.0
 *   }}
 */
@WebServlet("/api/calculator/compound-interest")
public class CompoundInterestController extends AbstractFinancialController {

    private static final long serialVersionUID = 1L;
    private final FinancialCalculatorService service = new FinancialCalculatorService();

    @Override
    protected void calculate(String username, JsonObject body, PrintWriter out,
                             HttpServletResponse response) throws Exception {

        double principal = requireDouble(body, "principal");
        double rate      = requireDouble(body, "rate");
        double time      = requireDouble(body, "time");
        String frequency = optString(body, "frequency", "annually");

        LinkedHashMap<String, Object> result = service.calculateCompoundInterest(
                principal, rate, time, frequency);

        FinancialDAO.saveCompoundInterest(
                username,
                principal,
                rate,
                time,
                frequency,
                (double) result.get("finalAmount"),
                (double) result.get("interestEarned"));

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(result)));
    }
}