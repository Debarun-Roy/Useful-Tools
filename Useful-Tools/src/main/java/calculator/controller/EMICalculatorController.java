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
 * POST /api/calculator/emi
 * Content-Type: application/json
 *
 * Request body:
 *   { "principal": 500000, "annualRate": 8.5, "tenure": 5, "tenureUnit": "years" }
 *
 * Response 200:
 *   { "success": true, "data": {
 *       "emi": 10254.36, "totalAmount": 615261.6, "totalInterest": 115261.6,
 *       "tenureMonths": 60, "principal": 500000, "annualRate": 8.5
 *   }}
 *
 * Response 400: { "success": false, "errorCode": "INVALID_PARAMETERS", "error": "..." }
 */
@WebServlet("/api/calculator/emi")
public class EMICalculatorController extends AbstractFinancialController {

    private static final long serialVersionUID = 1L;
    private final FinancialCalculatorService service = new FinancialCalculatorService();

    @Override
    protected void calculate(String username, JsonObject body, PrintWriter out,
                             HttpServletResponse response) throws Exception {

        double principal  = requireDouble(body, "principal");
        double annualRate = requireDouble(body, "annualRate");
        double tenure     = requireDouble(body, "tenure");
        String tenureUnit = optString(body, "tenureUnit", "years");

        LinkedHashMap<String, Object> result = service.calculateEMI(
                principal, annualRate, tenure, tenureUnit);

        // Persist to emi_calculations table
        FinancialDAO.saveEMI(
                username,
                principal,
                annualRate,
                (int) result.get("tenureMonths"),
                (double) result.get("emi"),
                (double) result.get("totalAmount"),
                (double) result.get("totalInterest"));

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(result)));
    }
}