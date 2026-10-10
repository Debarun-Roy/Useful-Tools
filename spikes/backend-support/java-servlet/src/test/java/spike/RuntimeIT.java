package spike;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeIT {
    final HttpClient client = HttpClient.newHttpClient();
    HttpResponse<String> get(String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void packagedReferenceStartsAndServesHealth() throws Exception {
        try (var server = new LocalServer(Path.of("target/b0-servlet.war"), 0)) {
            var res = get(server.url()+"/health");
            assertEquals(200, res.statusCode());
            assertEquals("{\"status\":\"ok\",\"version\":\"0.1.0\",\"target\":\"java-servlet\"}", res.body());
            assertEquals(404, get(server.url()+"/missing").statusCode());
            var manager = server.context.getManager();
            var session = manager.createSession(null);
            assertNotNull(session.getId());
            session.getSession().setAttribute("principal", "synthetic");
            assertEquals("synthetic", session.getSession().getAttribute("principal"));
            String id = session.getIdInternal();
            session.getSession().invalidate();
            assertFalse(session.isValid());
            assertNull(manager.findSession(id));
            var expired = manager.createSession(null);
            expired.setMaxInactiveInterval(1);
            Thread.sleep(1100);
            assertFalse(expired.isValid());
        }
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="b0.applicationWar", matches=".+")
    void applicationWarStartsWithIsolatedDatabase() throws Exception {
        String war = System.getProperty("b0.applicationWar");
        String db = System.getenv("SQLITE_DB_PATH");
        assertNotNull(db, "Certification must supply disposable SQLite path");
        assertTrue(Path.of(db).toAbsolutePath().toString().contains(".b0"));
        try (var server = new LocalServer(Path.of(war), 0)) {
            var res = get(server.url()+"/api/auth/session-status");
            assertEquals(401, res.statusCode());
            assertTrue(res.headers().firstValue("Content-Type").orElse("").contains("application/json"));
            assertTrue(res.body().contains("\"success\":false"));
            assertTrue(res.body().contains("UNAUTHENTICATED"));
            assertTrue(Files.size(Path.of(db)) > 0, "Startup must initialize isolated SQLite");
        }
    }
}
