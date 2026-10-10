package b5;

import com.google.gson.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.naming.NamingException;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.SimpleInstanceManager;
import org.apache.tomcat.util.net.*;

/** External verifier only. Actual WAR classes, routes, filters, hashing and DB adapters remain intact. */
public final class StarterServer {
    static final Gson JSON=new Gson();
    static final class Time extends Clock {
        final AtomicLong millis=new AtomicLong(System.currentTimeMillis());
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return Instant.ofEpochMilli(millis.get());}
    }
    public static void main(String[] args)throws Exception {
        Path run=Path.of(args[0]).toAbsolutePath().normalize();if(!run.toString().contains(".b5")&&!run.toString().contains(".b6"))throw new IllegalArgumentException("Owned B5/B6 path required");Files.createDirectories(run);
        Path expanded=Files.createDirectories(run.resolve("tomcat/webapps/ROOT"));
        try(var zip=new java.util.zip.ZipInputStream(Files.newInputStream(Path.of(args[1])))) {
            java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null) {
                Path destination=expanded.resolve(entry.getName()).normalize();if(!destination.startsWith(expanded))throw new IOException("Unsafe WAR");
                if(entry.isDirectory())Files.createDirectories(destination);else{Files.createDirectories(destination.getParent());Files.copy(zip,destination);}
            }
        }
        JsonObject spec=JsonParser.parseString(Files.readString(expanded.resolve("WEB-INF/classes/auth-spec.json"))).getAsJsonObject();
        String packageName=spec.get("packageName").getAsString();int port=URI.create(spec.getAsJsonArray("origins").get(0).getAsString()).getPort();
        Time time=new Time();Set<String> used=Collections.synchronizedSet(new HashSet<>());AtomicLong providerCalls=new AtomicLong();
        Map<String,String> versions=new LinkedHashMap<>();
        var provider=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var providerThreads=java.util.concurrent.Executors.newFixedThreadPool(2);provider.setExecutor(providerThreads);
        provider.createContext("/verify",exchange->{
            providerCalls.incrementAndGet();String form=new String(exchange.getRequestBody().readNBytes(16384),java.nio.charset.StandardCharsets.UTF_8);
            Map<String,String> fields=new HashMap<>();for(String pair:form.split("&")){String[] pieces=pair.split("=",2);fields.put(pieces[0],URLDecoder.decode(pieces[1],java.nio.charset.StandardCharsets.UTF_8));}
            String token=fields.get("response");if(!"synthetic-provider-secret".equals(fields.get("secret")))throw new IOException("SYNTHETIC_FORM_MISMATCH");
            if(token.equals("unavailable")){exchange.sendResponseHeaders(503,-1);exchange.close();return;}
            if(token.equals("redirect")){exchange.getResponseHeaders().add("Location","http://127.0.0.1:1/not-followed");exchange.sendResponseHeaders(302,-1);exchange.close();return;}
            if(token.equals("timeout")){try{Thread.sleep(3500);}catch(InterruptedException ignored){}exchange.close();return;}
            String action=token.startsWith("register:")?"register":"login";
            JsonObject result=new JsonObject();result.addProperty("success",used.add(token)&&!token.contains("rejected"));
            result.addProperty("action",token.contains("wrong-action")?"other":action);result.addProperty("hostname",token.contains("wrong-host")?"other.invalid":"localhost");
            result.addProperty("score",token.contains("low-score")?0.1:0.9);result.addProperty("challenge_ts",time.instant().minusSeconds(token.contains("expired")?121:token.contains("future")?-11:0).toString());
            if(token.contains("wrong-type"))result.addProperty("score","0.9");
            byte[] body=(token.equals("oversize")?"x".repeat(8193):token.contains("malformed")?"{":JSON.toJson(result)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);try(var output=exchange.getResponseBody()){output.write(body);}
        });provider.start();
        URI providerUri=URI.create("http://127.0.0.1:"+provider.getAddress().getPort()+"/verify");
        Tomcat tomcat=new Tomcat();tomcat.setBaseDir(run.resolve("tomcat").toString());tomcat.setPort(port);
        var connector=tomcat.getConnector();connector.setProperty("address","127.0.0.1");connector.setScheme("https");connector.setSecure(true);connector.setProperty("SSLEnabled","true");
        SSLHostConfig ssl=new SSLHostConfig();SSLHostConfigCertificate certificate=new SSLHostConfigCertificate(ssl,SSLHostConfigCertificate.Type.RSA);
        certificate.setCertificateKeystoreFile(args[2]);certificate.setCertificateKeystorePassword("b5-synthetic-only");certificate.setCertificateKeystoreType("PKCS12");ssl.addCertificate(certificate);connector.addSslHostConfig(ssl);
        StandardContext context=(StandardContext)tomcat.addWebapp("",expanded.toString());context.setFailCtxIfServletStartFails(true);
        // Tomcat still reads the exported web.xml. Only listener construction is substituted in this external process.
        context.setInstanceManager(new SimpleInstanceManager() {
            @Override public Object newInstance(String name)throws IllegalAccessException,InvocationTargetException,NamingException,InstantiationException,ClassNotFoundException,NoSuchMethodException {
                if(!name.equals(packageName+".Bootstrap"))return super.newInstance(name);
                return new ServletContextListener() {
                    public void contextInitialized(ServletContextEvent event) {
                        try {
                            ClassLoader loader=context.getLoader().getClassLoader();Class<?> bootstrap=loader.loadClass(name),config=loader.loadClass(packageName+".Config"),transport=loader.loadClass(packageName+".Captcha$Transport");
                            Object configuration=config.getMethod("load").invoke(null);
                            Class<?> factory=loader.loadClass(packageName+".Captcha$ConnectionFactory");
                            Object connections=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{factory},(proxy,method,values)->{
                                if(!method.getName().equals("open"))throw new UnsupportedOperationException();return (HttpURLConnection)providerUri.toURL().openConnection();
                            });
                            Object stub=loader.loadClass(packageName+".Captcha$Google").getConstructor(factory).newInstance(connections);
                            bootstrap.getMethod("initialize",ServletContext.class,config,Clock.class,transport).invoke(null,event.getServletContext(),configuration,time,stub);
                            Object application=bootstrap.getMethod("application",ServletContext.class).invoke(null,event.getServletContext());
                            Object users=application.getClass().getMethod("users").invoke(application);Method open=users.getClass().getDeclaredMethod("open");open.setAccessible(true);
                            try(var connection=(java.sql.Connection)open.invoke(users)) {versions.put("database",connection.getMetaData().getDatabaseProductVersion());versions.put("jdbc",connection.getMetaData().getDriverVersion());}
                        }catch(Exception failure){throw new IllegalStateException("EXPORTED_STARTER_INITIALIZATION_FAILED",failure);}
                    }
                };
            }
        });
        Path client=Files.createDirectories(run.resolve("client"));StandardContext browser=(StandardContext)tomcat.addContext("/client",client.toString());
        Tomcat.addServlet(browser,"client",new HttpServlet(){protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException{response.setContentType("text/html; charset=UTF-8");response.getWriter().write("<!doctype html><html><head><title>B5 external browser verification</title></head><body><h1>Exported auth application browser client</h1><p id='status'>Ready</p></body></html>");}});browser.addServletMappingDecoded("/","client");
        try {
            tomcat.start();if(!context.getState().isAvailable())throw new IllegalStateException("EXPORTED_WAR_STARTUP_FAILED");
            versions.put("origin","https://127.0.0.1:"+port);versions.put("jdk",System.getProperty("java.version"));versions.put("container",org.apache.catalina.util.ServerInfo.getServerInfo());
            versions.put("sessionCapacity",Integer.toString(((org.apache.catalina.session.ManagerBase)context.getManager()).getMaxActiveSessions()));
            Files.writeString(run.resolve("ready.tmp"),JSON.toJson(versions));Files.move(run.resolve("ready.tmp"),run.resolve("ready.json"),StandardCopyOption.ATOMIC_MOVE);
            while(!Files.exists(run.resolve("stop"))) {
                Path command=run.resolve("control.json");if(Files.exists(command)) {
                    JsonObject action=JsonParser.parseString(Files.readString(command)).getAsJsonObject();
                    if(action.has("advance"))time.millis.addAndGet(action.get("advance").getAsLong());
                    if(action.has("clearCsrf"))for(var session:context.getManager().findSessions())session.getSession().removeAttribute("csrf");
                    Object application=context.getLoader().getClassLoader().loadClass(packageName+".Bootstrap").getMethod("application",ServletContext.class).invoke(null,context.getServletContext());
                    if(action.has("holdHash")) {
                        Object auth=application.getClass().getMethod("auth").invoke(application);
                        Field passwords=auth.getClass().getDeclaredField("passwords");passwords.setAccessible(true);Object worker=passwords.get(auth);
                        Field slots=worker.getClass().getDeclaredField("slots");slots.setAccessible(true);
                        var semaphore=(java.util.concurrent.Semaphore)slots.get(worker);if(!semaphore.tryAcquire(2))throw new IllegalStateException("Hash slots not idle");
                        Thread release=new Thread(()->{try{Thread.sleep(2000);}catch(InterruptedException ignored){}finally{semaphore.release(2);}},"b7-owned-hash-release");release.setDaemon(true);release.start();
                    }
                    if(action.has("fillLimiter")) {
                        Object limiter=application.getClass().getMethod("limiter").invoke(application);Method take=limiter.getClass().getMethod("take",String.class,int.class);
                        for(int n=0;n<4096;n++)take.invoke(limiter,"b7-capacity-"+n,1);
                    }
                    if(action.has("fillSessions")) {
                        var manager=(org.apache.catalina.session.ManagerBase)context.getManager();
                        while(manager.getActiveSessions()<manager.getMaxActiveSessions())manager.createSession(null).getSession().setAttribute("b7-owned-capacity",true);
                    }
                    if(action.has("clearCapacitySessions"))for(var session:context.getManager().findSessions())if(Boolean.TRUE.equals(session.getSession().getAttribute("b7-owned-capacity")))session.expire();
                    Files.delete(command);
                    Path temporary=run.resolve("control-result.tmp"),result=run.resolve("control-result-"+action.get("id").getAsInt()+".json");
                    Files.writeString(temporary,JSON.toJson(Map.of("id",action.get("id").getAsInt(),"providerCalls",providerCalls.get())));
                    Files.move(temporary,result,StandardCopyOption.ATOMIC_MOVE);
                }Thread.sleep(25);
            }
        }finally{try{tomcat.stop();tomcat.destroy();}finally{provider.stop(0);providerThreads.shutdownNow();Files.deleteIfExists(run.resolve("ready.json"));}}
    }
}
