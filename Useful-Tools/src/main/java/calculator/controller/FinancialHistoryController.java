package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import com.google.gson.Gson;
import common.ApiResponse;
import common.DatabaseUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Returns paginated financial calculation history for the logged-in user.
 *
 * GET /api/calculator/financial-history?type=emi&page=0&size=10
 *
 * Valid type values: emi | tax | ci | salary
 *
 * All four financial tables were created in Sprint 4 with username and
 * calculated_at columns — no schema changes needed.
 */
@WebServlet("/api/calculator/financial-history")
public class FinancialHistoryController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE     = 50;
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        try {
            HttpSession session = request.getSession(false);
            String username = (session != null)
                    ? (String) session.getAttribute("username") : null;
            if (username == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "Not authenticated.", "UNAUTHENTICATED")));
                return;
            }

            String type = request.getParameter("type");
            if (type == null || type.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Parameter 'type' is required. Valid values: emi, tax, ci, salary.",
                        "MISSING_TYPE")));
                return;
            }

            int page = Math.max(0, parseIntParam(request.getParameter("page"), 0));
            int size = Math.min(Math.max(1,
                    parseIntParam(request.getParameter("size"), DEFAULT_SIZE)), MAX_SIZE);

            LinkedHashMap<String, Object> data =
                    switch (type.toLowerCase()) {
                        case "emi"    -> fetchEMI(username, page, size);
                        case "tax"    -> fetchTax(username, page, size);
                        case "ci"     -> fetchCI(username, page, size);
                        case "salary" -> fetchSalary(username, page, size);
                        default       -> null;
                    };

            if (data == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Unknown type '" + type + "'. Valid: emi, tax, ci, salary.",
                        "INVALID_TYPE")));
                return;
            }

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(gson.toJson(ApiResponse.fail(
                    "Failed to fetch financial history.", "INTERNAL_ERROR")));
        }
    }

    // ── Fetchers ──────────────────────────────────────────────────────────────

    private LinkedHashMap<String, Object> fetchEMI(
            String username, int page, int size) {
        int offset = page * size;
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            result.put("total", count(conn, "emi_calculations", username));
            ArrayList<LinkedHashMap<String, Object>> entries = new ArrayList<>();
            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT principal, annual_rate, tenure_months, emi, "
                    + "total_amount, total_interest, calculated_at "
                    + "FROM emi_calculations WHERE username = ? "
                    + "ORDER BY id DESC LIMIT ? OFFSET ?")) {
                pst.setString(1, username);
                pst.setInt(2, size);
                pst.setInt(3, offset);
                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) {
                        LinkedHashMap<String, Object> e = new LinkedHashMap<>();
                        e.put("principal",     rs.getDouble("principal"));
                        e.put("annualRate",    rs.getDouble("annual_rate"));
                        e.put("tenureMonths",  rs.getInt("tenure_months"));
                        e.put("emi",           rs.getDouble("emi"));
                        e.put("totalAmount",   rs.getDouble("total_amount"));
                        e.put("totalInterest", rs.getDouble("total_interest"));
                        e.put("calculatedAt",  rs.getString("calculated_at"));
                        entries.add(e);
                    }
                }
            }
            result.put("entries", entries);
        } catch (SQLException ex) {
            ex.printStackTrace();
            result.put("total", 0L);
            result.put("entries", new ArrayList<>());
        }
        result.put("page", page);
        result.put("size", size);
        result.put("type", "emi");
        return result;
    }

    private LinkedHashMap<String, Object> fetchTax(
            String username, int page, int size) {
        int offset = page * size;
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            result.put("total", count(conn, "tax_calculations", username));
            ArrayList<LinkedHashMap<String, Object>> entries = new ArrayList<>();
            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT gross_income, regime, taxable_income, total_tax, "
                    + "net_income, calculated_at "
                    + "FROM tax_calculations WHERE username = ? "
                    + "ORDER BY id DESC LIMIT ? OFFSET ?")) {
                pst.setString(1, username);
                pst.setInt(2, size);
                pst.setInt(3, offset);
                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) {
                        LinkedHashMap<String, Object> e = new LinkedHashMap<>();
                        e.put("grossIncome",   rs.getDouble("gross_income"));
                        e.put("regime",        rs.getString("regime"));
                        e.put("taxableIncome", rs.getDouble("taxable_income"));
                        e.put("totalTax",      rs.getDouble("total_tax"));
                        e.put("netIncome",     rs.getDouble("net_income"));
                        e.put("calculatedAt",  rs.getString("calculated_at"));
                        entries.add(e);
                    }
                }
            }
            result.put("entries", entries);
        } catch (SQLException ex) {
            ex.printStackTrace();
            result.put("total", 0L);
            result.put("entries", new ArrayList<>());
        }
        result.put("page", page);
        result.put("size", size);
        result.put("type", "tax");
        return result;
    }

    private LinkedHashMap<String, Object> fetchCI(
            String username, int page, int size) {
        int offset = page * size;
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            result.put("total", count(conn, "ci_calculations", username));
            ArrayList<LinkedHashMap<String, Object>> entries = new ArrayList<>();
            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT principal, annual_rate, time_years, frequency, "
                    + "final_amount, interest_earned, calculated_at "
                    + "FROM ci_calculations WHERE username = ? "
                    + "ORDER BY id DESC LIMIT ? OFFSET ?")) {
                pst.setString(1, username);
                pst.setInt(2, size);
                pst.setInt(3, offset);
                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) {
                        LinkedHashMap<String, Object> e = new LinkedHashMap<>();
                        e.put("principal",     rs.getDouble("principal"));
                        e.put("annualRate",    rs.getDouble("annual_rate"));
                        e.put("timeYears",     rs.getDouble("time_years"));
                        e.put("frequency",     rs.getString("frequency"));
                        e.put("finalAmount",   rs.getDouble("final_amount"));
                        e.put("interestEarned",rs.getDouble("interest_earned"));
                        e.put("calculatedAt",  rs.getString("calculated_at"));
                        entries.add(e);
                    }
                }
            }
            result.put("entries", entries);
        } catch (SQLException ex) {
            ex.printStackTrace();
            result.put("total", 0L);
            result.put("entries", new ArrayList<>());
        }
        result.put("page", page);
        result.put("size", size);
        result.put("type", "ci");
        return result;
    }

    private LinkedHashMap<String, Object> fetchSalary(
            String username, int page, int size) {
        int offset = page * size;
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            result.put("total", count(conn, "salary_calculations", username));
            ArrayList<LinkedHashMap<String, Object>> entries = new ArrayList<>();
            try (PreparedStatement pst = conn.prepareStatement(
                    "SELECT basic_salary, gross_salary, total_deductions, "
                    + "net_salary, calculated_at "
                    + "FROM salary_calculations WHERE username = ? "
                    + "ORDER BY id DESC LIMIT ? OFFSET ?")) {
                pst.setString(1, username);
                pst.setInt(2, size);
                pst.setInt(3, offset);
                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) {
                        LinkedHashMap<String, Object> e = new LinkedHashMap<>();
                        e.put("basicSalary",     rs.getDouble("basic_salary"));
                        e.put("grossSalary",     rs.getDouble("gross_salary"));
                        e.put("totalDeductions", rs.getDouble("total_deductions"));
                        e.put("netSalary",       rs.getDouble("net_salary"));
                        e.put("calculatedAt",    rs.getString("calculated_at"));
                        entries.add(e);
                    }
                }
            }
            result.put("entries", entries);
        } catch (SQLException ex) {
            ex.printStackTrace();
            result.put("total", 0L);
            result.put("entries", new ArrayList<>());
        }
        result.put("page", page);
        result.put("size", size);
        result.put("type", "salary");
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long count(Connection conn, String table, String username) throws SQLException {
        try (PreparedStatement pst = conn.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " WHERE username = ?")) {
            pst.setString(1, username);
            try (ResultSet rs = pst.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private int parseIntParam(String val, int defaultVal) {
        if (val == null || val.isBlank()) return defaultVal;
        try { return Integer.parseInt(val.trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }
}