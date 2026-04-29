package formatter.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import common.ApiResponse;
import formatter.service.FormatterService;
import formatter.service.FormatterService.FormattingStats;
import formatter.service.FormatterService.ValidationResult;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * FormatterController — Provides formatting and minification endpoints for JSON and XML.
 *
 * ── Endpoints ────────────────────────────────────────────────────────────
 * POST /api/formatter/format   — Pretty-print JSON or XML
 * POST /api/formatter/minify   — Minify JSON or XML
 * POST /api/formatter/validate — Validate JSON or XML syntax
 * POST /api/formatter/stats    — Get formatting statistics
 *
 * ── Request body ─────────────────────────────────────────────────────────
 * { "content": "...", "format": "json" | "xml" }
 *
 * Legacy form still accepted:
 * { "json": "..." }  → treated as format=json
 */
@WebServlet({"/api/formatter/format", "/api/formatter/minify",
             "/api/formatter/validate", "/api/formatter/stats"})
public class FormatterController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private final FormatterService formatterService = new FormatterService();

    private static class FormatRequest {
        String json;      // legacy field — kept for backward compatibility
        String content;   // preferred field (Sprint 21+)
        String format;    // "json" | "xml" (default: "json")
    }

    private String resolveContent(FormatRequest body) {
        return body.content != null ? body.content : body.json;
    }

    private String resolveFormat(FormatRequest body) {
        return body.format != null ? body.format.toLowerCase() : "json";
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String servletPath = request.getServletPath();
        PrintWriter out = response.getWriter();

        try {
            FormatRequest body = gson.fromJson(request.getReader(), FormatRequest.class);

            if (body == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body is required.",
                        "MISSING_BODY")));
                return;
            }

            String content = resolveContent(body);
            if (content == null || content.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body must contain a non-empty 'content' (or 'json') field.",
                        "MISSING_CONTENT")));
                return;
            }

            String trimmedContent = content.trim();
            String format = resolveFormat(body);

            if (servletPath.equals("/api/formatter/format")) {
                handleFormat(trimmedContent, format, out, response);
            } else if (servletPath.equals("/api/formatter/minify")) {
                handleMinify(trimmedContent, format, out, response);
            } else if (servletPath.equals("/api/formatter/validate")) {
                handleValidate(trimmedContent, format, out, response);
            } else if (servletPath.equals("/api/formatter/stats")) {
                handleStats(trimmedContent, format, out, response);
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

    private void handleFormat(String content, String format, PrintWriter out,
                              HttpServletResponse response) {
        try {
            String formatted = "xml".equals(format)
                    ? formatterService.formatXml(content)
                    : formatterService.formatJson(content);

            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("output", formatted);
            data.put("format", format);
            data.put("characterCount", formatted.length());

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Invalid " + format.toUpperCase() + ": " + e.getMessage(),
                    "INVALID_CONTENT")));
        }
    }

    private void handleMinify(String content, String format, PrintWriter out,
                              HttpServletResponse response) {
        try {
            String minified = "xml".equals(format)
                    ? formatterService.minifyXml(content)
                    : formatterService.minifyJson(content);

            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("output", minified);
            data.put("format", format);
            data.put("originalSize", content.length());
            data.put("minifiedSize", minified.length());

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Invalid " + format.toUpperCase() + ": " + e.getMessage(),
                    "INVALID_CONTENT")));
        }
    }

    private void handleValidate(String content, String format, PrintWriter out,
                                HttpServletResponse response) {
        ValidationResult result = "xml".equals(format)
                ? formatterService.validateXmlDetailed(content)
                : formatterService.validateJsonDetailed(content);

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("isValid", result.isValid);
        data.put("format", format);
        if (!result.isValid) {
            data.put("error", result.errorMessage);
        }

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(data)));
    }

    private void handleStats(String content, String format, PrintWriter out,
                             HttpServletResponse response) {
        try {
            FormattingStats stats = "xml".equals(format)
                    ? formatterService.getXmlFormattingStats(content)
                    : formatterService.getFormattingStats(content);

            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("format", format);
            data.put("originalSize", stats.originalSize);
            data.put("minifiedSize", stats.minifiedSize);
            data.put("compressionRatio", Math.round(stats.compressionRatio * 100.0) / 100.0);
            data.put("charactersSaved", stats.originalSize - stats.minifiedSize);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Invalid " + format.toUpperCase() + ": " + e.getMessage(),
                    "INVALID_CONTENT")));
        }
    }
}
