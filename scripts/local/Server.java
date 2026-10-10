package local;

import java.nio.file.*;
import java.sql.Connection;
import java.util.Map;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.core.StandardContext;
import com.google.gson.Gson;

/** Development container only. Never compiled into the application WAR. */
public final class Server {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toRealPath();
        Path run = Path.of(args[1]).toRealPath();
        Path local = root.resolve(".local").toRealPath();
        if (!run.startsWith(local.resolve("runs")) || !"local".equals(System.getenv("UT_PROFILE"))
                || !local.equals(Path.of(System.getenv("UT_LOCAL_DIR")).toRealPath())
                || System.getenv("RAILWAY_ENVIRONMENT_ID") != null || System.getenv("VERCEL") != null) {
            throw new IllegalStateException("Dedicated localhost launcher required");
        }
        System.setProperty("usefultools.local.launcher", "true");
        Path expanded = Files.createDirectories(run.resolve("webapp"));
        try (var zip = new java.util.zip.ZipInputStream(Files.newInputStream(root.resolve("Useful-Tools/target/UsefulTools.war")))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = expanded.resolve(entry.getName()).normalize();
                if (!target.startsWith(expanded)) throw new IllegalArgumentException("Invalid WAR path");
                if (entry.isDirectory()) Files.createDirectories(target);
                else { Files.createDirectories(target.getParent()); Files.copy(zip, target); }
            }
        }
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(run.resolve("tomcat").toString());
        tomcat.setPort(8080);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        var context = (StandardContext) tomcat.addWebapp("", expanded.toString());
        context.setFailCtxIfServletStartFails(true);
        try {
            tomcat.start();
            if (!context.getState().isAvailable() || !tomcat.getConnector().getState().isAvailable()) throw new IllegalStateException("Application startup failed");
            var loader = context.getLoader().getClassLoader();
            try (Connection db = (Connection) Class.forName("common.DatabaseUtils", true, loader)
                    .getMethod("getSQLite3Connection").invoke(null)) {
                if (db == null) throw new IllegalStateException("Local database unavailable");
                db.setAutoCommit(false);
                try (var st = db.createStatement()) {
                    st.execute("CREATE TABLE IF NOT EXISTS local_seed(version INTEGER PRIMARY KEY)");
                    int inserted = st.executeUpdate("INSERT OR IGNORE INTO local_seed VALUES(1)");
                    if (inserted == 1) st.executeUpdate("UPDATE tool_toggles SET enabled=1 WHERE tool_path='/backend-support'");
                    // No user accounts or production rows are seeded.
                }
                db.commit();
            }
            Files.writeString(run.resolve("backend-ready.json"), new Gson().toJson(Map.of(
                    "origin", "http://localhost:8080", "context", "/", "profile", "local")));
            while (!Files.exists(run.resolve("stop"))) Thread.sleep(200);
        } finally {
            tomcat.stop();
            tomcat.destroy();
            Files.deleteIfExists(run.resolve("backend-ready.json"));
        }
    }
}
