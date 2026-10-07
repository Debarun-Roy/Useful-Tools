package unitconverter.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import unitconverter.dao.UnitsDAO;

/**
 * UnitsListController — GET /api/units/list
 *
 * Returns every row of the units reference table as a flat JSON array.
 * UnitConverterPage.jsx fetches this once on mount and does all grouping,
 * filtering, and conversion math client-side — this endpoint only serves
 * data, the same role regex_patterns plays for RegexBuilderPage.
 *
 * No CSRF token required (GET, read-only, no state change). Reachable by
 * guest sessions — unit conversion is stateless and holds no personal
 * data, the same class of tool as the calculators and analyzers guests
 * already have full access to, so this path is intentionally NOT added to
 * GuestRestrictionFilter's restricted-paths list.
 */
@WebServlet("/api/units/list")
public class UnitsListController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {
            List<Map<String, Object>> units = UnitsDAO.getAllUnits();
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(units)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(gson.toJson(ApiResponse.fail(
                        "Could not load unit data. Please try again.",
                        "INTERNAL_ERROR")));
            }
        }
    }
}
