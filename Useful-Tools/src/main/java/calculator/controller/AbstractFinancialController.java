package calculator.controller;

import java.io.IOException;
import java.io.PrintWriter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Base class for all financial calculator controllers.
 *
 * Handles:
 *   1. JSON body parsing into a JsonObject (avoids per-controller boilerplate).
 *   2. Session-based username extraction (username is NEVER read from the
 *      request body for financial endpoints — only the session is trusted).
 *   3. Uniform error serialisation.
 *
 * Subclasses implement calculate(username, body, out, response).
 */
public abstract class AbstractFinancialController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    protected final Gson gson = new Gson();

    /**
     * Core calculation logic. Called after session validation and JSON parsing.
     *
     * @param username  Logged-in username (from session — cannot be forged).
     * @param body      Parsed JSON request body.
     * @param out       Response writer (already opened).
     * @param response  HttpServletResponse (to set status codes).
     */
    protected abstract void calculate(
            String username,
            JsonObject body,
            PrintWriter out,
            HttpServletResponse response) throws Exception;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        try {
            // ── 1. Resolve the authenticated username from session ─────────
            HttpSession session = request.getSession(false);
            String username = (session != null)
                    ? (String) session.getAttribute("username")
                    : null;

            // AuthFilter should have already rejected unauthenticated requests,
            // but we guard here anyway.
            if (username == null || username.isBlank()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "You must be logged in to use the financial calculator.",
                        "UNAUTHENTICATED")));
                return;
            }

            // ── 2. Parse JSON body ─────────────────────────────────────────
            JsonObject body;
            try {
                body = gson.fromJson(request.getReader(), JsonObject.class);
            } catch (JsonSyntaxException jse) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body must be valid JSON.",
                        "INVALID_JSON")));
                return;
            }

            if (body == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body is required.",
                        "MISSING_BODY")));
                return;
            }

            // ── 3. Delegate to subclass ────────────────────────────────────
            calculate(username, body, out, response);

        } catch (IllegalArgumentException iae) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(iae.getMessage(), "INVALID_PARAMETERS")));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(gson.toJson(ApiResponse.fail(
                    "Calculation failed. Please check your inputs.",
                    "INTERNAL_ERROR")));
        }
    }

    // ── Helper: safely extract a double from JSON (throws IAE on missing/bad) ──

    protected double requireDouble(JsonObject body, String field) {
        if (!body.has(field) || body.get(field).isJsonNull()) {
            throw new IllegalArgumentException("Field '" + field + "' is required.");
        }
        try {
            return body.get(field).getAsDouble();
        } catch (Exception e) {
            throw new IllegalArgumentException("Field '" + field + "' must be a number.");
        }
    }

    protected double optDouble(JsonObject body, String field, double defaultValue) {
        if (!body.has(field) || body.get(field).isJsonNull()) return defaultValue;
        try {
            return body.get(field).getAsDouble();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    protected String optString(JsonObject body, String field, String defaultValue) {
        if (!body.has(field) || body.get(field).isJsonNull()) return defaultValue;
        return body.get(field).getAsString();
    }
}