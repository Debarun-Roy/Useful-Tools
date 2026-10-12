package backendsupport;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchemaControlsTest {
    @Test void multipleParentsAndNullableUniqueExecuteInSqlite() throws Exception {
        var s=GenerationTest.sample();
        var customer=GenerationTest.table(s);
        customer.getAsJsonArray("columns").get(1).getAsJsonObject().addProperty("nullable",true);
        var product=JsonParser.parseString("{\"id\":\"products\",\"name\":\"products\",\"columns\":[{\"id\":\"id\",\"name\":\"id\",\"type\":\"integer\",\"nullable\":false}],\"primaryKey\":[\"id\"],\"unique\":[],\"indexes\":[],\"checks\":[],\"foreignKeys\":[]}").getAsJsonObject();
        s.getAsJsonArray("tables").add(product);
        var orders=s.getAsJsonArray("tables").get(1).getAsJsonObject();
        orders.getAsJsonArray("columns").add(JsonParser.parseString("{\"id\":\"product_id\",\"name\":\"product_id\",\"type\":\"integer\",\"nullable\":false}"));
        orders.getAsJsonArray("foreignKeys").add(JsonParser.parseString("{\"columns\":[\"product_id\"],\"tableId\":\"products\",\"targetColumns\":[\"id\"],\"onDelete\":\"NO ACTION\",\"onUpdate\":\"NO ACTION\"}"));
        String sql=GenerationTest.bundle(s).files().get("schema.sql");
        try(var db=java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");var st=db.createStatement()) {
            st.execute("PRAGMA foreign_keys=ON");
            for(String statement:sql.split(";"))if(!statement.isBlank())st.execute(statement);
            st.executeUpdate("INSERT INTO customers(id) VALUES(1),(2)");
            st.executeUpdate("INSERT INTO products(id) VALUES(1)");
            st.executeUpdate("INSERT INTO orders(id,customer_id,product_id) VALUES(1,1,1)");
            assertThrows(java.sql.SQLException.class,()->st.executeUpdate("INSERT INTO orders(id,customer_id,product_id) VALUES(2,1,999)"));
            assertThrows(java.sql.SQLException.class,()->st.executeUpdate("INSERT INTO orders(id,customer_id,product_id) VALUES(2,999,1)"));
        }
        s.addProperty("target","postgresql");assertTrue(GenerationTest.bundle(s).files().get("schema.sql").contains("REFERENCES \"products\""));
    }
    @Test void generatedSqlEnforcesDefaultsUniqueAndChildReferences() throws Exception {
        var s=GenerationTest.sample();
        var name=GenerationTest.table(s).getAsJsonArray("columns").get(1).getAsJsonObject();
        name.add("default",JsonParser.parseString("{\"kind\":\"literal\",\"value\":\"O'Reilly\"}"));
        String sql=GenerationTest.bundle(s).files().get("schema.sql");
        assertTrue(sql.contains("DEFAULT 'O''Reilly'"));assertTrue(sql.contains("UNIQUE"));assertTrue(sql.contains("FOREIGN KEY"));
        try(var db=java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");var st=db.createStatement()) {
            st.execute("PRAGMA foreign_keys=ON");
            for(String statement:sql.split(";")) if(!statement.isBlank())st.execute(statement);
            st.executeUpdate("INSERT INTO customers(id) VALUES(1)");
            try(var rows=st.executeQuery("SELECT name,active FROM customers WHERE id=1")) {assertTrue(rows.next());assertEquals("O'Reilly",rows.getString(1));assertEquals(1,rows.getInt(2));}
            assertThrows(java.sql.SQLException.class,()->st.executeUpdate("INSERT INTO customers(id) VALUES(2)"));
            assertThrows(java.sql.SQLException.class,()->st.executeUpdate("INSERT INTO orders(id,customer_id,amount) VALUES(1,999,12)"));
            st.executeUpdate("INSERT INTO orders(id,customer_id,amount) VALUES(1,1,12)");
        }
    }
    @Test void evaluatorDiagnosesNullablePrimaryKeyAndInvalidDefault() {
        var s=B3Test.sample("evaluator");var c=B3Test.col(B3Test.table(s,"schema"),0);
        B3Test.table(s,"schema").add("primaryKey",JsonParser.parseString("[\"id\"]"));
        c.addProperty("nullable",true);c.add("default",JsonParser.parseString("{\"kind\":\"literal\",\"value\":\"wrong numeric type\"}"));
        String report=B3Test.valid(s).bundle().files().get("findings.json");
        assertTrue(report.contains("PRIMARY_KEY_NULLABLE"));assertTrue(report.contains("UNSAFE_DEFAULT_EXPRESSION"));
    }
    @Test void defaultsAndUniqueRenderInBothDialects() {
        for(String target:java.util.List.of("sqlite","postgresql")) {
            var s=GenerationTest.sample();s.addProperty("target",target);
            String sql=GenerationTest.bundle(s).files().get("schema.sql");
            assertTrue(sql.contains("DEFAULT"));assertTrue(sql.contains("UNIQUE"));assertTrue(sql.contains("REFERENCES"));
        }
    }
}
