package common.startup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
 * - units (Sprint 26): Unit-conversion reference data for UnitConverterPage
 *
 * ── units is different from the other three ──────────────────────────────
 * regex_patterns/schema_templates/tool_recommendations are populated by user
 * actions over time (they have a `username` or `created_by` column). units
 * has no such column — every row is shared, system-curated reference data,
 * read by every session including guests. There's nothing for a user action
 * to insert, so it's seeded once, here, from a bundled SQL resource, the
 * first time the table is found empty. After that first seed, the live
 * table is the source of truth: adding or correcting a unit going forward
 * means editing the database directly (that's the whole point of this
 * migration — no more redeploying the frontend to change a conversion
 * factor). Re-bundling an updated units.sql resource and redeploying has NO
 * effect once the table is non-empty, by design — it would otherwise
 * silently overwrite live edits on every restart.
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
            createUnitsTable(conn);
            
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

    /**
     * Creates the units table (Sprint 26 — UnitConverterPage migration off
     * its static CATEGORIES object) and seeds it from the bundled
     * units.sql resource the first time it's empty.
     *
     * factor + offset together support a single generic conversion formula
     * for every unit_type, including Temperature (which needs an offset —
     * a plain factor can't represent Celsius/Fahrenheit/Kelvin/Rankine
     * having different zero points):
     *
     *     base   = value * factor + offset
     *     result = (base - offsetB) / factorB
     *
     * For every non-temperature row, offset is 0, so this reduces exactly
     * to the old value*factorA/factorB formula.
     */
    private void createUnitsTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS units (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  unit_id TEXT NOT NULL," +
                "  label TEXT NOT NULL," +
                "  category TEXT NOT NULL," +
                "  unit_type TEXT NOT NULL," +
                "  symbol TEXT NOT NULL," +
                "  factor REAL NOT NULL," +
                "  offset REAL NOT NULL DEFAULT 0," +
                "  UNIQUE(unit_id, unit_type)" +
                ")";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
            System.out.println("[DatabaseInitializer] ✓ units table initialized");
        }

        seedUnitsTableIfEmpty(conn);
    }

    /**
     * Seeds the units table from the bundled units.sql resource, but only
     * the first time the table is empty. This method runs on every
     * application startup, and re-running the INSERT against an
     * already-seeded table would violate UNIQUE(unit_id, unit_type).
     *
     * units.sql (see src/main/resources/units.sql) contains exactly two
     * statements: a CREATE TABLE (redundant with the one above — kept in
     * that file so it also works standalone if Debarun runs it directly
     * against a fresh database with a SQLite tool) and the INSERT. Only
     * the INSERT half is needed here, so the file is split at its one
     * "INSERT INTO" occurrence.
     */
    private void seedUnitsTableIfEmpty(Connection conn) throws SQLException {
        try (PreparedStatement count = conn.prepareStatement("SELECT COUNT(*) FROM units");
             ResultSet rs = count.executeQuery()) {
            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("[DatabaseInitializer] units table already seeded, skipping");
                return;
            }
        }

        String script = readUnitsResource();
        if (script == null) {
            System.out.println("[DatabaseInitializer] WARNING: units.sql resource not found, "
                    + "units table left empty");
            return;
        }

        int insertIdx = script.indexOf("INSERT INTO");
        if (insertIdx < 0) {
            System.out.println("[DatabaseInitializer] WARNING: units.sql has no INSERT "
                    + "statement, units table left empty");
            return;
        }

        String insertSql = script.substring(insertIdx).trim();
        if (insertSql.endsWith(";")) {
            insertSql = insertSql.substring(0, insertSql.length() - 1);
        }

        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            int rows = stmt.executeUpdate();
            System.out.println("[DatabaseInitializer] ✓ units table seeded with " + rows + " rows");
        }
    }

    /**
     * Reads src/main/resources/units.sql from the classpath.
     */
    private String readUnitsResource() {
        try (InputStream is = DatabaseInitializer.class.getClassLoader()
                .getResourceAsStream("units.sql")) {
            if (is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // No cleanup needed
    }
}
