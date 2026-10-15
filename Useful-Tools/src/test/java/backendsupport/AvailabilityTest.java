package backendsupport;
import org.junit.jupiter.api.Test;
import common.dao.ToolToggleDAO;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class AvailabilityTest {
    @Test void initializationDisablesOnlyNewToolAndPreservesBothAdminChoices() throws Exception {
        try (var connection=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ToolToggleDAO.ensureSchema(connection);
            try(var statement=connection.createStatement()) {
                try(var rows=statement.executeQuery("select enabled from tool_toggles where tool_path='/backend-support'")) {assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
                statement.executeUpdate("update tool_toggles set enabled=1 where tool_path='/backend-support'");
                ToolToggleDAO.ensureSchema(connection);
                try(var rows=statement.executeQuery("select enabled from tool_toggles where tool_path='/backend-support'")) {assertTrue(rows.next());assertEquals(1,rows.getInt(1));}
                statement.executeUpdate("update tool_toggles set enabled=0 where tool_path='/backend-support'");
                ToolToggleDAO.ensureSchema(connection);
                try(var rows=statement.executeQuery("select tool_path from tool_toggles where enabled=0 order by tool_path")) {
                    assertTrue(rows.next());assertEquals("/backend-support",rows.getString(1));
                    assertTrue(rows.next());assertEquals("/emote-utils",rows.getString(1));assertFalse(rows.next());
                }
            }
        }
    }
}
