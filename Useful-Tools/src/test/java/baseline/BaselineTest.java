package baseline;

import calculator.service.CalculatorService;
import common.ApiResponse;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BaselineTest {
    @Test void bundledUnitSeedInitializesFreshDatabaseAndPreservesExistingValues() throws Exception {
        try (var connection=java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")) {
            var initializer=new common.startup.DatabaseInitializer();
            var initialize=common.startup.DatabaseInitializer.class.getDeclaredMethod("createUnitsTable",java.sql.Connection.class);
            initialize.setAccessible(true);initialize.invoke(initializer,connection);
            try(var statement=connection.createStatement()) {
                int count;
                try(var rows=statement.executeQuery("SELECT COUNT(*) FROM units")){assertTrue(rows.next());count=rows.getInt(1);assertTrue(count>100);}
                try(var rows=statement.executeQuery("SELECT factor FROM units WHERE unit_id='m' AND unit_type='Length'")){assertTrue(rows.next());assertEquals(1.0,rows.getDouble(1));}
                statement.executeUpdate("UPDATE units SET factor=2 WHERE unit_id='m' AND unit_type='Length'");
                initialize.invoke(initializer,connection);
                try(var rows=statement.executeQuery("SELECT COUNT(*) FROM units")){assertTrue(rows.next());assertEquals(count,rows.getInt(1));}
                try(var rows=statement.executeQuery("SELECT factor FROM units WHERE unit_id='m' AND unit_type='Length'")){assertTrue(rows.next());assertEquals(2.0,rows.getDouble(1));}
            }
        }
    }
    @Test void arithmeticAndPrecedence() throws Exception {
        assertEquals(14.0, new CalculatorService().evaluate("2+3*4"));
    }
    @Test void invalidInputIsRejected() {
        var service = new CalculatorService();
        assertThrows(IllegalArgumentException.class, () -> service.evaluate(" "));
        assertFalse(service.validateForMode("2+(", "simple"));
        assertFalse(service.validateForMode(null, "simple"));
    }
    @Test void actualGsonEnvelopeOmitsNullFields() {
        var gson = new Gson();
        var ok = JsonParser.parseString(gson.toJson(ApiResponse.ok(14))).getAsJsonObject();
        assertTrue(ok.get("success").getAsBoolean());
        assertEquals(14, ok.get("data").getAsInt());
        assertFalse(ok.has("error"));
        var fail = JsonParser.parseString(gson.toJson(ApiResponse.fail("Invalid", "INVALID_INPUT"))).getAsJsonObject();
        assertFalse(fail.get("success").getAsBoolean());
        assertEquals("INVALID_INPUT", fail.get("errorCode").getAsString());
        assertFalse(fail.has("data"));
    }
}
