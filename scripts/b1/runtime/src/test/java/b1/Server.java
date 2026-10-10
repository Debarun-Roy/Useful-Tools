package b1;
import java.nio.file.*;
import java.util.*;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.core.StandardContext;
import com.google.gson.Gson;

/** Verification-only container/session fixture. Never packaged into the application WAR. */
public final class Server {
    public static void main(String[] args) throws Exception {
        Path run=Path.of(args[0]).toAbsolutePath().normalize();
        if (!run.toString().contains(".b1") && !run.toString().contains(".b2") && !run.toString().contains(".b3") && !run.toString().contains(".b4") && !run.toString().contains(".b5") && !run.toString().contains(".b6")) throw new IllegalArgumentException("Isolated verification directory required");
        Path expanded=Files.createDirectories(run.resolve("tomcat/webapps/ROOT"));
        try(var zip=new java.util.zip.ZipInputStream(Files.newInputStream(Path.of(args[1])))) {
            java.util.zip.ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                Path dest=expanded.resolve(entry.getName()).normalize();
                if(!dest.startsWith(expanded)) throw new IllegalArgumentException("Unsafe WAR");
                if(entry.isDirectory()) Files.createDirectories(dest);
                else {Files.createDirectories(dest.getParent());Files.copy(zip,dest);}
            }
        }
        Path dist=Path.of(args[2]);
        try(var paths=Files.walk(dist)) {for(Path p:paths.toList()) {
            Path to=expanded.resolve(dist.relativize(p).toString());
            if(Files.isDirectory(p)) Files.createDirectories(to); else Files.copy(p,to,StandardCopyOption.REPLACE_EXISTING);
        }}
        Tomcat tomcat=new Tomcat();tomcat.setBaseDir(run.resolve("tomcat").toString());tomcat.setPort(0);
        tomcat.getConnector().setProperty("address","127.0.0.1");
        var context=(StandardContext)tomcat.addWebapp("",expanded.toString());context.setFailCtxIfServletStartFails(true);
        Tomcat.addServlet(context,"b1-spa",new jakarta.servlet.http.HttpServlet(){
            @Override protected void doGet(jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res) throws java.io.IOException {
                res.setContentType("text/html");res.getOutputStream().write(Files.readAllBytes(expanded.resolve("index.html")));
            }
        });
        for(String path:List.of("/backend-support","/dashboard","/admin","/login")) context.addServletMappingDecoded(path,"b1-spa");
        tomcat.start();if(!context.getState().isAvailable()) throw new IllegalStateException("WAR initialization failed");
        Map<String,Object> fixtures=new LinkedHashMap<>();fixtures.put("origin","http://127.0.0.1:"+tomcat.getConnector().getLocalPort());
        List<String> fixtureNames=new ArrayList<>(List.of("user","guest","admin","missingcsrf","rate","browseruser","browserguest","browseradmin","boundary","negative"));
        for(String module:List.of("migration","view","evaluator","etl","rest"))for(String role:List.of("user","guest","admin","negative","exports","browser"))fixtureNames.add(module+role);
        for(int i=0;i<20;i++)fixtureNames.add("restfixture"+i);
        for(int i=0;i<20;i++)fixtureNames.add("etlfixture"+i);
        for(String name:fixtureNames) {
            var session=context.getManager().createSession(null);
            String username=name.contains("guest")?"Guest User":"b1_"+name;
            String role=name.contains("admin")?"admin":"user";
            String csrf=UUID.randomUUID().toString();
            session.getSession().setAttribute("username",username);session.getSession().setAttribute("role",role);
            if(!name.equals("missingcsrf"))session.getSession().setAttribute("csrfToken",csrf);
            fixtures.put(name,Map.of("session",session.getId(),"csrf",csrf,"username",username,"role",role));
        }
        Files.writeString(run.resolve("sessions.json"),new Gson().toJson(fixtures));
        System.out.println("B1_READY");
        // Owned process stops gracefully when the verifier creates this file.
        while(!Files.exists(run.resolve("stop"))) Thread.sleep(100);
        tomcat.stop();tomcat.destroy();Files.deleteIfExists(run.resolve("sessions.json"));
    }
}
