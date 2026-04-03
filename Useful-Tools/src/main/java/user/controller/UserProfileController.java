package user.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import passwordgenerator.dao.UserDAO;

/**
 * Returns the profile summary for the currently logged-in user.
 *
 * GET /api/user/profile
 *
 * Response 200:
 *   { "success": true, "data": {
 *       "username": "alice",
 *       "totalStandardCalculations": 145,
 *       "totalEMICalculations": 3,
 *       "totalTaxCalculations": 2,
 *       "totalCICalculations": 1,
 *       "totalSalaryCalculations": 5,
 *       "totalPasswordsStored": 4,
 *       "totalCalculations": 156
 *   }}
 *
 * All counts gracefully return 0 if the table does not yet exist
 * (pre-migration), so the endpoint is safe to call at any schema version.
 */
@WebServlet("/api/user/profile")
public class UserProfileController extends HttpServlet {

    private static final long serialVersionUID = 1L;
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

            LinkedHashMap<String, Object> data = buildProfile(username);
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(gson.toJson(ApiResponse.fail(
                    "Failed to load profile.", "INTERNAL_ERROR")));
        }
    }

    private LinkedHashMap<String, Object> buildProfile(String username) {
        long std    = countFrom("calc_history",        username);
        long emi    = countFrom("emi_calculations",    username);
        long tax    = countFrom("tax_calculations",    username);
        long ci     = countFrom("ci_calculations",     username);
        long salary = countFrom("salary_calculations", username);
        long vault  = countFrom("password_table",      username);

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("username",                    username);
        data.put("accountCreatedDate",          UserDAO.getAccountCreatedDate(username));
        data.put("totalStandardCalculations",   std);
        data.put("totalEMICalculations",        emi);
        data.put("totalTaxCalculations",        tax);
        data.put("totalCICalculations",         ci);
        data.put("totalSalaryCalculations",     salary);
        data.put("totalPasswordsStored",        vault);
        data.put("totalCalculations",           std + emi + tax + ci + salary);
        return data;
    }

    /**
     * Returns COUNT(*) from the given table for the user.
     * Returns 0 silently if the table does not exist or any SQL error occurs.
     * Table names are hardcoded — no injection risk.
     */
    private long countFrom(String table, String username) {
        try (Connection conn = DatabaseUtils.getSQLite3Connection();
             PreparedStatement pst = conn.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE username = ?")) {
            pst.setString(1, username);
            try (ResultSet rs = pst.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            // Table may not exist before schema migration; return 0 gracefully.
            return 0L;
        }
    }
}
