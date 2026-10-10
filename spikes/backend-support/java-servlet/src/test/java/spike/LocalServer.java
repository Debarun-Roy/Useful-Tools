package spike;
import java.nio.file.*;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.Context;

/** Disposable loopback-only container; can run either reference or application WAR. */
public final class LocalServer implements AutoCloseable {
    final Tomcat tomcat = new Tomcat();
    public final Context context;
    private final Path base;
    public LocalServer(Path war, int port) throws Exception {
        base = Files.createTempDirectory("b0-tomcat-");
        Path expanded = Files.createDirectories(base.resolve("webapps/ROOT"));
        // Extract the actual WAR into a disposable docBase; avoid Windows URL-encoded path ambiguity.
        try (var zip = new java.util.zip.ZipInputStream(Files.newInputStream(war.toAbsolutePath().normalize()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path destination = expanded.resolve(entry.getName()).normalize();
                if (!destination.startsWith(expanded)) throw new java.io.IOException("Unsafe WAR entry");
                if (entry.isDirectory()) Files.createDirectories(destination);
                else { Files.createDirectories(destination.getParent()); Files.copy(zip, destination); }
            }
        }
        tomcat.setBaseDir(base.toString());
        tomcat.setPort(port);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        context = tomcat.addWebapp("", expanded.toString());
        ((org.apache.catalina.core.StandardContext) context).setFailCtxIfServletStartFails(true);
        tomcat.start();
        if (!context.getState().isAvailable()) throw new IllegalStateException("WAR initialization failed");
    }
    public String url() { return "http://127.0.0.1:" + tomcat.getConnector().getLocalPort(); }
    public void close() throws Exception {
        try { tomcat.stop(); } finally { tomcat.destroy(); }
        try (var paths = Files.walk(base)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
    public static void main(String[] args) throws Exception {
        Path war = Path.of(System.getProperty("b0.war", "target/b0-servlet.war"));
        var server = new LocalServer(war, Integer.getInteger("b0.port", 8085));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { server.close(); } catch(Exception e) { e.printStackTrace(); } }));
        System.out.println("B0_READY " + server.url());
        server.tomcat.getServer().await();
    }
}
