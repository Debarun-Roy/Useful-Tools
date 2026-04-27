package common.startup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import common.DatabaseUtils;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * DatabaseInitializer — Creates required tables on application startup.
 *
 * ── Purpose ──────────────────────────────────────────────────────────────
 * Ensures all necessary tables exist before the application processes requests.
 * Uses CREATE TABLE IF NOT EXISTS so it's safe to run multiple times.
 *
 * ── Tables created (Sprint 20+) ──────────────────────────────────────────
 * - regex_patterns: User-saved regex patterns with categories
 * - schema_templates: JSON schema templates for validation
 * - tool_recommendations: Tool recommendation data based on usage patterns
 */
@WebListener
public class DatabaseInitializer implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[DatabaseInitializer] Starting database table initialization...");
        
        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            if (conn == null) {
                System.out.println("[DatabaseInitializer] ERROR: Could not obtain database connection");
                return;
            }

            createRegexPatternsTable(conn);
            createSchemaTemplatesTable(conn);
            createToolRecommendationsTable(conn);
            
            System.out.println("[DatabaseInitializer] All required tables initialized successfully");
        } catch (SQLException e) {
            System.out.println("[DatabaseInitializer] ERROR during initialization:");
            e.printStackTrace();
        }
    }

    /**
     * Creates the regex_patterns table for storing user-saved regex patterns.
     */
    private void createRegexPatternsTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS regex_patterns (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  username TEXT NOT NULL," +
                "  pattern TEXT NOT NULL," +
                "  description TEXT," +
                "  category TEXT," +
                "  example_string TEXT," +
                "  created_at TEXT NOT NULL," +
                "  updated_at TEXT NOT NULL," +
                "  UNIQUE(username, pattern)" +
                ")";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
            System.out.println("[DatabaseInitializer] ✓ regex_patterns table initialized");
        }
    }

    /**
     * Creates the schema_templates table for storing JSON schema templates.
     */
    private void createSchemaTemplatesTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS schema_templates (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  name TEXT NOT NULL UNIQUE," +
                "  description TEXT," +
                "  schema_json TEXT NOT NULL," +
                "  category TEXT," +
                "  is_public INTEGER DEFAULT 1," +
                "  created_by TEXT," +
                "  created_at TEXT NOT NULL" +
                ")";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
            System.out.println("[DatabaseInitializer] ✓ schema_templates table initialized");
        }
    }

    /**
     * Creates the tool_recommendations table for tracking tool usage and recommendations.
     */
    private void createToolRecommendationsTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS tool_recommendations (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  username TEXT NOT NULL," +
                "  tool_path TEXT NOT NULL," +
                "  tool_name TEXT NOT NULL," +
                "  usage_count INTEGER DEFAULT 1," +
                "  last_used_at TEXT NOT NULL," +
                "  UNIQUE(username, tool_path)" +
                ")";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
            System.out.println("[DatabaseInitializer] ✓ tool_recommendations table initialized");
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // No cleanup needed
    }
}
