package common.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import common.DatabaseUtils;

/**
 * FormatterDAO — Database operations for Sprint 20 infrastructure.
 *
 * ── Responsibilities ─────────────────────────────────────────────────────
 * - Manage regex patterns (save, fetch, delete)
 * - Manage schema templates (fetch, list)
 * - Track tool recommendations based on usage
 *
 * ── Tables ───────────────────────────────────────────────────────────────
 * - regex_patterns: User-saved regex patterns
 * - schema_templates: JSON schema templates for validation
 * - tool_recommendations: Tool usage tracking for recommendations
 */
public class FormatterDAO {

    // ── REGEX PATTERNS ───────────────────────────────────────────────────────

    /**
     * Saves a regex pattern for a user.
     *
     * @param username Username of the pattern owner
     * @param pattern The regex pattern string
     * @param description Optional human-readable description
     * @param category Pattern category (e.g., "email", "url", "custom")
     * @param exampleString Example string that matches the pattern
     * @throws SQLException if the database operation fails
     */
    public void saveRegexPattern(String username, String pattern, String description,
                                  String category, String exampleString) throws SQLException {
        String sql = "INSERT OR REPLACE INTO regex_patterns " +
                "(username, pattern, description, category, example_string, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DatabaseUtils.getSQLite3Connection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            String now = Instant.now().toString();
            stmt.setString(1, username);
            stmt.setString(2, pattern);
            stmt.setString(3, description);
            stmt.setString(4, category);
            stmt.setString(5, exampleString);
            stmt.setString(6, now);
            stmt.setString(7, now);
            stmt.executeUpdate();
        }
    }

    /**
     * Retrieves all saved regex patterns for a user.
     *
     * @param username Username to fetch patterns for
     * @return List of pattern maps (id, pattern, description, category, example_string)
     * @throws SQLException if the database operation fails
     */
    public List<Map<String, Object>> getUserRegexPatterns(String username) throws SQLException {
        String sql = "SELECT id, pattern, description, category, example_string, created_at " +
                "FROM regex_patterns WHERE username = ? ORDER BY created_at DESC";
        
        List<Map<String, Object>> patterns = new ArrayList<>();
        
        try (Connection conn = DatabaseUtils.getSQLite3Connection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> pattern = new LinkedHashMap<>();
                    pattern.put("id", rs.getInt("id"));
                    pattern.put("pattern", rs.getString("pattern"));
                    pattern.put("description", rs.getString("description"));
                    pattern.put("category", rs.getString("category"));
                    pattern.put("exampleString", rs.getString("example_string"));
                    pattern.put("createdAt", rs.getString("created_at"));
                    patterns.add(pattern);
                }
            }
        }
        
        return patterns;
    }

    /**
     * Deletes a regex pattern by ID.
     *
     * @param patternId ID of the pattern to delete
     * @param username Username (for authorization check)
     * @throws SQLException if the database operation fails
     */
    public void deleteRegexPattern(int patternId, String username) throws SQLException {
        String sql = "DELETE FROM regex_patterns WHERE id = ? AND username = ?";
        
        try (Connection conn = DatabaseUtils.getSQLite3Connection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, patternId);
            stmt.setString(2, username);
            stmt.executeUpdate();
        }
    }

    // ── SCHEMA TEMPLATES ─────────────────────────────────────────────────────

    /**
     * Retrieves a schema template by name.
     *
     * @param templateName Name of the schema template
     * @return Map containing schema_json and metadata, or empty map if not found
     * @throws SQLException if the database operation fails
     */
    public Map<String, Object> getSchemaTemplate(String templateName) throws SQLException {
        String sql = "SELECT id, name, description, schema_json, category " +
                "FROM schema_templates WHERE name = ? AND is_public = 1";
        
        try (Connection conn = DatabaseUtils.getSQLite3Connection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, templateName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> template = new LinkedHashMap<>();
                    template.put("id", rs.getInt("id"));
                    template.put("name", rs.getString("name"));
                    template.put("description", rs.getString("description"));
                    template.put("schemaJson", rs.getString("schema_json"));
                    template.put("category", rs.getString("category"));
                    return template;
                }
            }
        }
        
        return new LinkedHashMap<>();
    }

    /**
     * Lists all public schema templates, optionally filtered by category.
     *
     * @param category Optional category filter (null for all)
     * @return List of schema template maps
     * @throws SQLException if the database operation fails
     */
    public List<Map<String, Object>> listSchemaTemplates(String category) throws SQLException {
        String sql = "SELECT id, name, description, category FROM schema_templates " +
                "WHERE is_public = 1";
        if (category != null && !category.isBlank()) {
            sql += " AND category = ?";
        }
        sql += " ORDER BY name ASC";
        
        List<Map<String, Object>> templates = new ArrayList<>();
        
        try (Connection conn = DatabaseUtils.getSQLite3Connection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (category != null && !category.isBlank()) {
                stmt.setString(1, category);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> template = new LinkedHashMap<>();
                    template.put("id", rs.getInt("id"));
                    template.put("name", rs.getString("name"));
                    template.put("description", rs.getString("description"));
                    template.put("category", rs.getString("category"));
                    templates.add(template);
                }
            }
        }
        
        return templates;
    }

    // ── TOOL RECOMMENDATIONS ─────────────────────────────────────────────────

    /**
     * Records or updates a tool usage for recommendation tracking.
     *
     * @param username Username
     * @param toolPath Tool path (e.g., "/calculator", "/text-utils")
     * @param toolName Tool display name (e.g., "Calculator", "Text Utilities")
     * @throws SQLException if the database operation fails
     */
    public void recordToolUsage(String username, String toolPath, String toolName)
            throws SQLException {
        String sql = "INSERT INTO tool_recommendations " +
                "(username, tool_path, tool_name, usage_count, last_used_at) " +
                "VALUES (?, ?, ?, 1, ?) " +
                "ON CONFLICT(username, tool_path) DO UPDATE SET " +
                "usage_count = usage_count + 1, last_used_at = ?";
        
        try (Connection conn = DatabaseUtils.getSQLite3Connection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            String now = Instant.now().toString();
            stmt.setString(1, username);
            stmt.setString(2, toolPath);
            stmt.setString(3, toolName);
            stmt.setString(4, now);
            stmt.setString(5, now);
            stmt.executeUpdate();
        }
    }

    /**
     * Retrieves tool recommendations for a user based on usage patterns.
     *
     * @param username Username
     * @param limit Maximum number of recommendations to return
     * @return List of tool recommendation maps (tool_path, tool_name, usage_count, last_used_at)
     * @throws SQLException if the database operation fails
     */
    public List<Map<String, Object>> getToolRecommendations(String username, int limit)
            throws SQLException {
        String sql = "SELECT tool_path, tool_name, usage_count, last_used_at " +
                "FROM tool_recommendations WHERE username = ? " +
                "ORDER BY last_used_at DESC LIMIT ?";
        
        List<Map<String, Object>> recommendations = new ArrayList<>();
        
        try (Connection conn = DatabaseUtils.getSQLite3Connection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> rec = new LinkedHashMap<>();
                    rec.put("toolPath", rs.getString("tool_path"));
                    rec.put("toolName", rs.getString("tool_name"));
                    rec.put("usageCount", rs.getInt("usage_count"));
                    rec.put("lastUsedAt", rs.getString("last_used_at"));
                    recommendations.add(rec);
                }
            }
        }
        
        return recommendations;
    }
}
