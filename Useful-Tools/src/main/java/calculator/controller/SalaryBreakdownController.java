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
 * POST /api/calculator/salary-breakdown
 * Content-Type: application/json
 *
 * Request body:
 *   {
 *     "basicSalary": 50000,
 *     "hra": 20000,
 *     "da": 5000,
 *     "allowances": 3000,
 *     "pfContribution": 6000,
 *     "professionalTax": 200,
 *     "otherDeductions": 500
 *   }
 *
 * All fields except basicSalary are optional and default to 0.
 *
 * Response 200:
 *   { "success": true, "data": {
 *       "basicSalary": 50000, "hra": 20000, "da": 5000, "allowances": 3000,
 *       "grossSalary": 78000, "pfContribution": 6000, "professionalTax": 200,
 *       "otherDeductions": 500, "totalDeductions": 6700,
 *       "netSalary": 71300, "annualGross": 936000, "annualNet": 855600
 *   }}
 */
@WebServlet("/api/calculator/salary-breakdown")
public class SalaryBreakdownController extends AbstractFinancialController {

    private static final long serialVersionUID = 1L;
    private final FinancialCalculatorService service = new FinancialCalculatorService();

    @Override
    protected void calculate(String username, JsonObject body, PrintWriter out,
                             HttpServletResponse response) throws Exception {

        double basicSalary      = requireDouble(body, "basicSalary");
        double hra              = optDouble(body, "hra", 0);
        double da               = optDouble(body, "da", 0);
        double allowances       = optDouble(body, "allowances", 0);
        double pfContribution   = optDouble(body, "pfContribution", 0);
        double professionalTax  = optDouble(body, "professionalTax", 0);
        double otherDeductions  = optDouble(body, "otherDeductions", 0);

        LinkedHashMap<String, Object> result = service.calculateSalaryBreakdown(
                basicSalary, hra, da, allowances, pfContribution, professionalTax, otherDeductions);

        FinancialDAO.saveSalaryBreakdown(
                username,
                basicSalary,
                (double) result.get("grossSalary"),
                (double) result.get("totalDeductions"),
                (double) result.get("netSalary"));

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(result)));
    }
}