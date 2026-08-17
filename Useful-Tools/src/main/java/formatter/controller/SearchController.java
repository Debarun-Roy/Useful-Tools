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
@WebServlet({"/api/search/tools", "/api/search/recommendations", "/api/search/record-usage", "/api/search/trending"})
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
            , new ToolInfo("/regex-builder", "Regex Builder", ".*",
                    "Build, test, explain, save, and reuse regular expressions"),
            new ToolInfo("/data-viz", "Data Visualisation", "📊",
                    "Build bar, line, pie, area and scatter charts; import CSV/JSON; analyse trends and export PNG/SVG"),
            new ToolInfo("/markdown", "Markdown Converter", "M↓",
                    "Live Markdown editor with preview, 5 themes, custom CSS, table builder, and HTML/PDF export"),
            new ToolInfo("/color-tools", "Color Tools", "🎨",
                    "Convert HEX/RGB/HSL/HSV/CMYK, generate palettes, build CSS gradients, and check WCAG contrast"),
            new ToolInfo("/placeholder", "Placeholder Generator", "¶",
                    "Generate Lorem Ipsum, fake data records, SVG image placeholders, and JSON sample data")
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
            } else if (servletPath.equals("/api/search/trending")) {
                handleTrending(request, out, response);
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
     * Handler for GET /api/search/trending — Returns site-wide trending tools
     * for the "Most Popular This Week" widget.
     */
    private void handleTrending(HttpServletRequest request, PrintWriter out,
                                HttpServletResponse response) throws Exception {
        int limit = 6;
        try {
            String limitParam = request.getParameter("limit");
            if (limitParam != null) {
                limit = Math.min(Integer.parseInt(limitParam), 20);
            }
        } catch (NumberFormatException e) {
            // Use default
        }

        List<Map<String, Object>> dbTrending = formatterDAO.getTrendingTools(limit);

        // Enrich DB results with icon + description from ALL_TOOLS registry
        Map<String, ToolInfo> toolByPath = new java.util.HashMap<>();
        for (ToolInfo t : ALL_TOOLS) toolByPath.put(t.path, t);

        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> row : dbTrending) {
            String path = (String) row.get("toolPath");
            ToolInfo info = toolByPath.get(path);
            Map<String, Object> entry = new LinkedHashMap<>(row);
            entry.put("icon",        info != null ? info.icon        : "🔧");
            entry.put("description", info != null ? info.description : "");
            entry.put("name",        info != null ? info.name        : row.get("toolName"));
            enriched.add(entry);
        }

        // If DB is empty (cold start), return the top static tools as default trending
        if (enriched.isEmpty()) {
            String[] defaultPaths = {"/calculator", "/text-utils", "/vault",
                                     "/formatter", "/regex-builder", "/data-viz"};
            for (String p : defaultPaths) {
                ToolInfo info = toolByPath.get(p);
                if (info != null) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("toolPath",    p);
                    entry.put("toolName",    info.name);
                    entry.put("name",        info.name);
                    entry.put("icon",        info.icon);
                    entry.put("description", info.description);
                    entry.put("trend",       0L);
                    enriched.add(entry);
                }
            }
        }

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("trending", enriched);
        data.put("count",    enriched.size());

        response.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(ApiResponse.ok(data)));
    }

    /**
     * Calculates a relevance score for a tool based on query match.
     * Higher scores indicate better matches.
     *
     * Sprint 21 enhancement: natural language intent matching.
     * Phrases like "convert colours", "check contrast", "validate JSON" are
     * mapped to tool keywords so the score reflects user intent, not just
     * literal string overlap.
     */
    // ── Natural language intent → keyword mapping ─────────────────────────────
    //
    // Maps common natural-language phrases to tool-relevant keywords.
    // When the search query contains a phrase on the left, the keywords on the
    // right are added to the effective query before scoring. This lets a query
    // like "validate json" surface API Formatter even if the user types
    // "check if my json is valid".
    private static final Map<String, String[]> NL_INTENT_MAP;
    static {
        NL_INTENT_MAP = new java.util.LinkedHashMap<>();
        NL_INTENT_MAP.put("validate",     new String[]{"formatter", "validator"});
        NL_INTENT_MAP.put("format",       new String[]{"formatter", "code"});
        NL_INTENT_MAP.put("json",         new String[]{"formatter", "code"});
        NL_INTENT_MAP.put("xml",          new String[]{"formatter"});
        NL_INTENT_MAP.put("yaml",         new String[]{"formatter", "code"});
        NL_INTENT_MAP.put("regex",        new String[]{"regex", "pattern", "text"});
        NL_INTENT_MAP.put("pattern",      new String[]{"regex"});
        NL_INTENT_MAP.put("chart",        new String[]{"data", "visualisation"});
        NL_INTENT_MAP.put("graph",        new String[]{"data", "visualisation"});
        NL_INTENT_MAP.put("visualise",    new String[]{"data", "visualisation"});
        NL_INTENT_MAP.put("visualize",    new String[]{"data", "visualisation"});
        NL_INTENT_MAP.put("markdown",     new String[]{"markdown", "converter"});
        NL_INTENT_MAP.put("document",     new String[]{"markdown", "text"});
        NL_INTENT_MAP.put("pdf",          new String[]{"markdown", "converter"});
        NL_INTENT_MAP.put("convert",      new String[]{"converter", "encoding"});
        NL_INTENT_MAP.put("encode",       new String[]{"encoding"});
        NL_INTENT_MAP.put("decode",       new String[]{"encoding"});
        NL_INTENT_MAP.put("base64",       new String[]{"encoding"});
        NL_INTENT_MAP.put("hash",         new String[]{"dev", "hash"});
        NL_INTENT_MAP.put("password",     new String[]{"vault", "password"});
        NL_INTENT_MAP.put("generate",     new String[]{"key", "password", "dev"});
        NL_INTENT_MAP.put("calculate",    new String[]{"calculator"});
        NL_INTENT_MAP.put("math",         new String[]{"calculator"});
        NL_INTENT_MAP.put("time",         new String[]{"time", "timezone"});
        NL_INTENT_MAP.put("timezone",     new String[]{"time"});
        NL_INTENT_MAP.put("image",        new String[]{"image"});
        NL_INTENT_MAP.put("resize",       new String[]{"image"});
        NL_INTENT_MAP.put("css",          new String[]{"web", "dev"});
        NL_INTENT_MAP.put("html",         new String[]{"web", "encoding", "markdown"});
        NL_INTENT_MAP.put("colour",       new String[]{"web", "dev"});
        NL_INTENT_MAP.put("color",        new String[]{"web", "dev"});
        NL_INTENT_MAP.put("number",       new String[]{"analyser", "calculator"});
        NL_INTENT_MAP.put("prime",        new String[]{"analyser"});
        NL_INTENT_MAP.put("text",         new String[]{"text"});
        NL_INTENT_MAP.put("string",       new String[]{"text", "encoding"});
        NL_INTENT_MAP.put("qr",           new String[]{"dev", "qr"});
        NL_INTENT_MAP.put("cron",         new String[]{"dev", "cron"});
        NL_INTENT_MAP.put("table",        new String[]{"markdown", "text"});
        NL_INTENT_MAP.put("scatter",      new String[]{"data", "visualisation"});
        NL_INTENT_MAP.put("statistics",   new String[]{"calculator", "data"});
        NL_INTENT_MAP.put("stat",         new String[]{"calculator", "data"});
        NL_INTENT_MAP.put("matrix",       new String[]{"calculator"});
        NL_INTENT_MAP.put("unit",         new String[]{"converter"});
    }

    private int calculateMatchScore(String query, ToolInfo tool) {
        int score = 0;

        String toolNameLower   = tool.name.toLowerCase();
        String descriptionLower = tool.description.toLowerCase();
        String pathLower       = tool.path.toLowerCase();

        // ── Direct scoring ────────────────────────────────────────────────────
        if (toolNameLower.equals(query)) {
            score += 100;
        } else if (toolNameLower.startsWith(query)) {
            score += 60;
        } else if (toolNameLower.contains(query)) {
            score += 40;
        }

        // Path segment match (e.g. "vault" matches "/vault")
        if (pathLower.contains(query)) {
            score += 20;
        }

        if (descriptionLower.contains(query)) {
            score += 15;
        }

        // ── Token-level scoring ───────────────────────────────────────────────
        // Split query into words and score each word independently.
        // A multi-word query like "json format" scores both "json" and "format".
        String[] queryTokens = query.split("\\s+");
        if (queryTokens.length > 1) {
            for (String token : queryTokens) {
                if (token.length() < 2) continue;
                if (toolNameLower.contains(token))   score += 15;
                if (descriptionLower.contains(token)) score += 8;
                if (pathLower.contains(token))       score += 10;
            }
        }

        // ── Natural language intent expansion ─────────────────────────────────
        for (Map.Entry<String, String[]> entry : NL_INTENT_MAP.entrySet()) {
            if (query.contains(entry.getKey())) {
                for (String keyword : entry.getValue()) {
                    if (toolNameLower.contains(keyword))    score += 12;
                    if (descriptionLower.contains(keyword)) score += 6;
                    if (pathLower.contains(keyword))        score += 8;
                }
            }
        }

        // ── Fuzzy character-sequence matching (fallback) ──────────────────────
        if (score == 0 && isFuzzyMatch(query, toolNameLower)) {
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
