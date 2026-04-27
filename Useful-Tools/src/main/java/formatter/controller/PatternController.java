package formatter.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import common.ApiResponse;
import common.dao.FormatterDAO;
import formatter.service.PatternService;
import formatter.service.PatternService.MatchResult;
import formatter.service.PatternService.PatternDefinition;
import formatter.service.PatternService.SplitResult;
import formatter.service.PatternService.ValidationResult;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * PatternController — Provides regex pattern management and testing endpoints.
 *
 * ── Endpoints ────────────────────────────────────────────────────────────
 * POST /api/pattern/validate      — Validate regex pattern syntax
 * POST /api/pattern/test          — Test pattern against string
 * POST /api/pattern/split         — Split string by pattern
 * GET  /api/pattern/common        — Get common pattern library
 * POST /api/pattern/save          — Save user pattern (requires auth)
 * GET  /api/pattern/user          — Get user's saved patterns (requires auth)
 * DELETE /api/pattern/user/{id}   — Delete user pattern (requires auth)
 */
@WebServlet({"/api/pattern/validate", "/api/pattern/test", "/api/pattern/split",
        "/api/pattern/common", "/api/pattern/save", "/api/pattern/user", "/api/pattern/user/*"})
public class PatternController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private final PatternService patternService = new PatternService();
    private final FormatterDAO formatterDAO = new FormatterDAO();

    private static class PatternRequest {
        String pattern;
        String testString;
        List<String> testStrings;
        String description;
        String category;
        String exampleString;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String servletPath = request.getServletPath();
        PrintWriter out = response.getWriter();

        try {
            PatternRequest body = gson.fromJson(request.getReader(), PatternRequest.class);

            if (body == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body is required.",
                        "MISSING_BODY")));
                return;
            }

            // Route to appropriate handler
            if (servletPath.equals("/api/pattern/validate")) {
                handleValidate(body, out, response);
            } else if (servletPath.equals("/api/pattern/test")) {
                handleTest(body, out, response);
            } else if (servletPath.equals("/api/pattern/split")) {
                handleSplit(body, out, response);
            } else if (servletPath.equals("/api/pattern/save")) {
                handleSave(body, request, out, response);
            }

        } catch (JsonSyntaxException jse) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Request body must be valid JSON.",
                    "INVALID_REQUEST_JSON")));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(gson.toJson(ApiResponse.fail(
                    "Server error: " + e.getMessage(),
                    "INTERNAL_ERROR")));
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String servletPath = request.getServletPath();
        PrintWriter out = response.getWriter();

        try {
            if (servletPath.equals("/api/pattern/common")) {
                handleGetCommon(out, response);
            } else if (servletPath.equals("/api/pattern/user")) {
                handleGetUserPatterns(request, out, response);
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(gson.toJson(ApiResponse.fail(
                    "Server error: " + e.getMessage(),
                    "INTERNAL_ERROR")));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String servletPath = request.getServletPath();
        PrintWriter out = response.getWriter();

        try {
            if (servletPath.startsWith("/api/pattern/user/")) {
                handleDeletePattern(servletPath, request, out, response);
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(gson.toJson(ApiResponse.fail(
                    "Server error: " + e.getMessage(),
                    "INTERNAL_ERROR")));
        }
    }

    /**
     * Handler for /api/pattern/validate — Validate regex pattern.
     */
    private void handleValidate(PatternRequest body, PrintWriter out, HttpServletResponse response) {
        if (body.pattern == null || body.pattern.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Field 'pattern' is required.",
                    "MISSING_PATTERN")));
            return;
        }

        ValidationResult result = patternService.validatePattern(body.pattern);

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("isValid", result.isValid);
        if (!result.isValid) {
            data.put("error", result.error);
        }

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(data)));
    }

    /**
     * Handler for /api/pattern/test — Test pattern against string.
     */
    private void handleTest(PatternRequest body, PrintWriter out, HttpServletResponse response) {
        if (body.pattern == null || body.pattern.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Field 'pattern' is required.",
                    "MISSING_PATTERN")));
            return;
        }

        if (body.testString == null || body.testString.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Field 'testString' is required.",
                    "MISSING_TEST_STRING")));
            return;
        }

        MatchResult result = patternService.testPattern(body.pattern, body.testString);

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("patternValid", result.patternValid);
        data.put("isMatched", result.isMatched);
        data.put("matchCount", result.matchCount);
        if (result.patternValid) {
            data.put("matches", result.matches);
        } else {
            data.put("error", result.error);
        }

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(data)));
    }

    /**
     * Handler for /api/pattern/split — Split string by pattern.
     */
    private void handleSplit(PatternRequest body, PrintWriter out, HttpServletResponse response) {
        if (body.pattern == null || body.pattern.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Field 'pattern' is required.",
                    "MISSING_PATTERN")));
            return;
        }

        if (body.testString == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Field 'testString' is required.",
                    "MISSING_TEST_STRING")));
            return;
        }

        SplitResult result = patternService.splitByPattern(body.pattern, body.testString);

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("isValid", result.isValid);
        data.put("partCount", result.partCount);
        if (result.isValid) {
            data.put("parts", result.parts);
        } else {
            data.put("error", result.error);
        }

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(data)));
    }

    /**
     * Handler for GET /api/pattern/common — Get common patterns library.
     */
    private void handleGetCommon(PrintWriter out, HttpServletResponse response) {
        var commonPatterns = PatternService.getCommonPatterns();

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("patterns", commonPatterns);
        data.put("categoryCount", commonPatterns.size());

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(data)));
    }

    /**
     * Handler for POST /api/pattern/save — Save user pattern.
     */
    private void handleSave(PatternRequest body, HttpServletRequest request, PrintWriter out,
                           HttpServletResponse response) throws Exception {
        // Check authentication (would use actual auth from session/token in production)
        String username = (String) request.getAttribute("username");
        if (username == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(gson.toJson(ApiResponse.fail(
                    "Authentication required.",
                    "NOT_AUTHENTICATED")));
            return;
        }

        if (body.pattern == null || body.pattern.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Field 'pattern' is required.",
                    "MISSING_PATTERN")));
            return;
        }

        // Validate pattern syntax
        ValidationResult validationResult = patternService.validatePattern(body.pattern);
        if (!validationResult.isValid) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    validationResult.error,
                    "INVALID_PATTERN")));
            return;
        }

        formatterDAO.saveRegexPattern(username, body.pattern, body.description,
                body.category, body.exampleString);

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("message", "Pattern saved successfully");
        data.put("pattern", body.pattern);

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(data)));
    }

    /**
     * Handler for GET /api/pattern/user — Get user's saved patterns.
     */
    private void handleGetUserPatterns(HttpServletRequest request, PrintWriter out,
                                       HttpServletResponse response) throws Exception {
        String username = (String) request.getAttribute("username");
        if (username == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(gson.toJson(ApiResponse.fail(
                    "Authentication required.",
                    "NOT_AUTHENTICATED")));
            return;
        }

        var patterns = formatterDAO.getUserRegexPatterns(username);

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("patterns", patterns);
        data.put("count", patterns.size());

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(data)));
    }

    /**
     * Handler for DELETE /api/pattern/user/{id} — Delete pattern.
     */
    private void handleDeletePattern(String servletPath, HttpServletRequest request,
                                    PrintWriter out, HttpServletResponse response) throws Exception {
        String username = (String) request.getAttribute("username");
        if (username == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(gson.toJson(ApiResponse.fail(
                    "Authentication required.",
                    "NOT_AUTHENTICATED")));
            return;
        }

        String patternIdStr = servletPath.substring("/api/pattern/user/".length());

        try {
            int patternId = Integer.parseInt(patternIdStr);
            formatterDAO.deleteRegexPattern(patternId, username);

            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("message", "Pattern deleted successfully");

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Invalid pattern ID.",
                    "INVALID_ID")));
        }
    }
}
