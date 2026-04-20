package common.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;

import common.ApiResponse;
import common.dao.ToolToggleDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ToolStatusController — Sprint 17 RBAC.
 *
 * Read-only endpoint that returns the current enable/disable state of all
 * tools. Accessible by ALL authenticated users (not just admins).
 * The Dashboard fetches this on load to apply disabled-tool indicators.
 *
 * GET /api/tools/status
 *
 * Response 200:
 * {
 *   "success": true,
 *   "data": {
 *     "toggles": {
 *       "/calculator": true,
 *       "/vault":      false,
 *       ...
 *     }
 *   }
 * }
 *
 * Guests can also call this (guest tools are still toggleable globally).
 */
@WebServlet("/api/tools/status")
public class ToolStatusController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter out = response.getWriter()) {
            Map<String, Boolean> statuses = ToolToggleDAO.getAllStatuses();

            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("toggles", statuses);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print(gson.toJson(
                ApiResponse.fail("Failed to fetch tool status.", "INTERNAL_ERROR")));
        }
    }
}
