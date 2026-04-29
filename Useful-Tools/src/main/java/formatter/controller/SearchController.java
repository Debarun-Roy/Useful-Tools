package formatter.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import common.ApiResponse;
import common.cache.ToolCache;
import common.dao.FormatterDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * SearchController — Provides tool search and discovery endpoints.
 *
 * ── Endpoints ────────────────────────────────────────────────────────────
 * GET /api/search/tools         — Search tools by query (fuzzy matching)
 * GET /api/search/recommendations — Get personalized tool recommendations
 * POST /api/search/record-usage  — Record tool usage for recommendations
 */
@WebServlet({"/api/search/tools", "/api/search/recommendations", "/api/search/record-usage"})
public class SearchController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String CACHE_SEARCH_PREFIX = "search:";
    private static final long   CACHE_SEARCH_TTL    = 120L; // 2 minutes

    private final Gson gson = new Gson();
    private final FormatterDAO formatterDAO = new FormatterDAO();

    // Static tool registry — would normally be in a database or config
    private static final List<ToolInfo> ALL_TOOLS = List.of(
            new ToolInfo("/calculator", "Calculator", "🧮",
                    "Arithmetic, boolean, trig, complex, matrix, statistics and more"),
            new ToolInfo("/analyser", "Number Analyser", "🔢",
                    "Classify numbers, explore base representations, and generate sequences"),
            new ToolInfo("/vault", "Password Vault", "🔐",
                    "Generate, save and retrieve passwords securely with RSA-2048 encryption"),
            new ToolInfo("/converter", "Unit Converter", "🔄",
                    "Convert between length, mass, temperature, time, data, speed and area"),
            new ToolInfo("/text-utils", "Text Utilities", "📝",
                    "Word counter, case converter, diff checker, regex tester, slug generator and more"),
            new ToolInfo("/encoding", "Encoding & Decoding", "🔧",
                    "Base64, URL encoding, hex conversion and more"),
            new ToolInfo("/code-utils", "Code Utilities", "💻",
                    "JSON, YAML, CSV formatting and Markdown rendering"),
            new ToolInfo("/web-dev", "Web Dev Helpers", "🛠️",
                    "HTML/CSS/JS utilities for web development"),
            new ToolInfo("/image-tools", "Image Tools", "🖼️",
                    "Resize, convert PNG/JPG/WebP, compress, crop, rotate, and filter"),
            new ToolInfo("/dev-utils", "Dev Utilities", "🧑‍💻",
                    "Hash identifier, API key generator, QR code generator, and cron expression builder"),
            new ToolInfo("/time-utils", "Time Utilities", "🕐",
                    "Timezone converter and Unix timestamp conversion"),
            new ToolInfo("/formatter", "API Formatter", "⚡",
                    "Format, validate, minify and analyse JSON, XML, and YAML — with JSON Schema support")
    );

    private static class RecordUsageRequest {
        String toolPath;
        String toolName;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String servletPath = request.getServletPath();
        PrintWriter out = response.getWriter();

        try {
            if (servletPath.equals("/api/search/tools")) {
                handleSearch(request, out, response);
            } else if (servletPath.equals("/api/search/recommendations")) {
                handleRecommendations(request, out, response);
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
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String servletPath = request.getServletPath();
        PrintWriter out = response.getWriter();

        try {
            if (servletPath.equals("/api/search/record-usage")) {
                handleRecordUsage(request, out, response);
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

    /**
     * Handler for GET /api/search/tools — Search tools by query.
     * Implements fuzzy matching on tool names and descriptions.
     */
    private void handleSearch(HttpServletRequest request, PrintWriter out,
                             HttpServletResponse response) {
        String query = request.getParameter("q");

        if (query == null || query.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Query parameter 'q' is required.",
                    "MISSING_QUERY")));
            return;
        }

        query = query.toLowerCase().trim();

        // Check cache first — search results are deterministic for a static tool list.
        ToolCache cache = ToolCache.getInstance();
        String cacheKey = CACHE_SEARCH_PREFIX + query;
        LinkedHashMap<String, Object> cached = cache.get(cacheKey);
        if (cached != null) {
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(cached)));
            return;
        }

        List<ToolSearchResult> results = new ArrayList<>();

        // Fuzzy match against tool names and descriptions
        for (ToolInfo tool : ALL_TOOLS) {
            int score = calculateMatchScore(query, tool);
            if (score > 0) {
                results.add(new ToolSearchResult(tool, score));
            }
        }

        // Sort by score descending
        results.sort((a, b) -> Integer.compare(b.relevanceScore, a.relevanceScore));

        // Limit to top 10 results
        if (results.size() > 10) {
            results = results.subList(0, 10);
        }

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        data.put("results", results.stream()
                .map(r -> Map.of(
                        "path", r.tool.path,
                        "name", r.tool.name,
                        "icon", r.tool.icon,
                        "description", r.tool.description,
                        "relevance", r.relevanceScore
                ))
                .toList());
        data.put("count", results.size());

        cache.put(cacheKey, data, CACHE_SEARCH_TTL);

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(data)));
    }

    /**
     * Handler for GET /api/search/recommendations — Get personalized recommendations.
     */
    private void handleRecommendations(HttpServletRequest request, PrintWriter out,
                                      HttpServletResponse response) throws Exception {
        // In a real implementation, this would check the user's session
        String username = request.getParameter("username");
        int limit = 5;

        try {
            String limitParam = request.getParameter("limit");
            if (limitParam != null) {
                limit = Math.min(Integer.parseInt(limitParam), 20); // Max 20
            }
        } catch (NumberFormatException e) {
            // Use default
        }

        List<Map<String, Object>> recommendations = new ArrayList<>();

        if (username != null && !username.isBlank()) {
            // Get user's recent tools from database
            var recentTools = formatterDAO.getToolRecommendations(username, limit);
            recommendations = recentTools;
        }

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("recommendations", recommendations);
        data.put("count", recommendations.size());

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(data)));
    }

    /**
     * Handler for POST /api/search/record-usage — Record tool usage.
     */
    private void handleRecordUsage(HttpServletRequest request, PrintWriter out,
                                  HttpServletResponse response) throws Exception {
        String username = (String) request.getAttribute("username");
        if (username == null) {
            // Try to get from query parameter (for testing)
            username = request.getParameter("username");
        }

        if (username == null || username.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(gson.toJson(ApiResponse.fail(
                    "Username is required.",
                    "MISSING_USERNAME")));
            return;
        }

        RecordUsageRequest body = gson.fromJson(request.getReader(), RecordUsageRequest.class);

        if (body == null || body.toolPath == null || body.toolPath.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(gson.toJson(ApiResponse.fail(
                    "Field 'toolPath' is required.",
                    "MISSING_TOOL_PATH")));
            return;
        }

        // Record the usage
        String toolName = body.toolName != null ? body.toolName : body.toolPath;
        formatterDAO.recordToolUsage(username, body.toolPath, toolName);

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("message", "Usage recorded");
        data.put("toolPath", body.toolPath);

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(data)));
    }

    /**
     * Calculates a relevance score for a tool based on query match.
     * Higher scores indicate better matches.
     */
    private int calculateMatchScore(String query, ToolInfo tool) {
        int score = 0;

        String toolNameLower = tool.name.toLowerCase();
        String descriptionLower = tool.description.toLowerCase();

        // Exact match on name
        if (toolNameLower.equals(query)) {
            score += 100;
        }
        // Name starts with query
        else if (toolNameLower.startsWith(query)) {
            score += 50;
        }
        // Name contains query word
        else if (toolNameLower.contains(" " + query) || toolNameLower.contains(query)) {
            score += 30;
        }

        // Description contains query
        if (descriptionLower.contains(query)) {
            score += 10;
        }

        // Partial word matching (character-by-character)
        if (isFuzzyMatch(query, toolNameLower)) {
            score += 5;
        }

        return score;
    }

    /**
     * Basic fuzzy string matching — checks if query characters appear in order.
     */
    private boolean isFuzzyMatch(String query, String text) {
        int queryIndex = 0;
        for (int i = 0; i < text.length() && queryIndex < query.length(); i++) {
            if (text.charAt(i) == query.charAt(queryIndex)) {
                queryIndex++;
            }
        }
        return queryIndex == query.length();
    }

    // ── Helper Classes ───────────────────────────────────────────────────────

    /**
     * Information about a tool.
     */
    private static class ToolInfo {
        String path;
        String name;
        String icon;
        String description;

        ToolInfo(String path, String name, String icon, String description) {
            this.path = path;
            this.name = name;
            this.icon = icon;
            this.description = description;
        }
    }

    /**
     * Search result with relevance score.
     */
    private static class ToolSearchResult {
        ToolInfo tool;
        int relevanceScore;

        ToolSearchResult(ToolInfo tool, int relevanceScore) {
            this.tool = tool;
            this.relevanceScore = relevanceScore;
        }
    }
}
