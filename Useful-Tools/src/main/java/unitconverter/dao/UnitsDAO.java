package unitconverter.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import common.DatabaseUtils;

/**
 * UnitsDAO — read access to the `units` reference table.
 *
 * This table is shared, system-curated reference data — not per-user
 * content like regex_patterns or schema_templates. Every row is readable
 * by every authenticated session, including guests. The table is created
 * and seeded once by DatabaseInitializer from the bundled units.sql
 * resource; this DAO only ever reads it.
 *
 * Sprint 26: added so UnitConverterPage no longer hardcodes its
 * conversion data in a static CATEGORIES object in the frontend — a unit
 * can now be added or corrected by editing this table directly, with no
 * frontend redeploy. The actual conversion arithmetic still happens
 * entirely client-side (value * factor + offset); this DAO only serves
 * the unit metadata list, matching the existing pattern of serving
 * reference data over a read endpoint for otherwise fully client-side
 * tools (see regex_patterns / schema_templates).
 */
public class UnitsDAO {

    private UnitsDAO() { }

    // ── Schema creation ─────────────────────────────────────────────────────

    public static void ensureSchema(Connection conn) throws SQLException {
        try(Statement st = conn.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS units("
                + "id        INTEGER PRIMARY KEY,"
                + "unit_id   VARCHAR NOT NULL,"
                + "label     VARCHAR NOT NULL,"
                + "category  VARCHAR NOT NULL,"
                + "unit_type VARCHAR NOT NULL,"
                + "symbol    VARCHAR NOT NULL,"
                + "factor    REAL NOT NULL,"
                + "offset    REAL NOT NULL DEFAULT 0,"
                + "UNIQUE(unit_id, unit_type)"
                + ");"
            );
        }
    }

    /**
     * Returns every row in the units table, ordered by unit_type then
     * category then label so the frontend can group/tab without an extra
     * client-side sort.
     *
     * factor and offset are returned as doubles (not strings) so Gson
     * serializes them as JSON numbers — the frontend does arithmetic
     * directly on them without a parse step.
     */
    public static List<Map<String, Object>> getAllUnits() {
        List<Map<String, Object>> result = new ArrayList<>();

        String sql = "SELECT unit_id, label, category, unit_type, symbol, factor, offset "
                   + "FROM units "
                   + "ORDER BY unit_type, category, label";

        try (Connection conn = DatabaseUtils.getSQLite3Connection()) {
            ensureSchema(conn);
            PreparedStatement pst = conn.prepareStatement(sql);
            ResultSet rs = pst.executeQuery();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("unitId",   rs.getString("unit_id"));
                row.put("label",    rs.getString("label"));
                row.put("category", rs.getString("category"));
                row.put("unitType", rs.getString("unit_type"));
                row.put("symbol",   rs.getString("symbol"));
                row.put("factor",   rs.getDouble("factor"));
                row.put("offset",   rs.getDouble("offset"));
                result.add(row);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }
}
