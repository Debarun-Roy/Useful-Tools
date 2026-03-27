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
 * POST /api/calculator/tax
 * Content-Type: application/json
 *
 * Request body:
 *   { "income": 1200000, "regime": "new", "deductions": 150000 }
 *
 * tenureUnit for regime: "new" | "old"
 * deductions is used only when regime = "old"
 *
 * Response 200:
 *   { "success": true, "data": {
 *       "grossIncome": 1200000, "standardDeduction": 75000, "otherDeductions": 0,
 *       "taxableIncome": 1125000, "taxBeforeCess": 82500, "cess": 3300,
 *       "totalTax": 85800, "netIncome": 1114200, "regime": "new"
 *   }}
 */
@WebServlet("/api/calculator/tax")
public class TaxCalculatorController extends AbstractFinancialController {

    private static final long serialVersionUID = 1L;
    private final FinancialCalculatorService service = new FinancialCalculatorService();

    @Override
    protected void calculate(String username, JsonObject body, PrintWriter out,
                             HttpServletResponse response) throws Exception {

        double income     = requireDouble(body, "income");
        String regime     = optString(body, "regime", "new");
        double deductions = optDouble(body, "deductions", 0);

        LinkedHashMap<String, Object> result = service.calculateTax(income, regime, deductions);

        FinancialDAO.saveTax(
                username,
                income,
                (String) result.get("regime"),
                (double) result.get("taxableIncome"),
                (double) result.get("totalTax"),
                (double) result.get("netIncome"));

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(result)));
    }
}