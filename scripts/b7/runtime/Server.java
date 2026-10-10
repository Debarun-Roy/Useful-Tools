package b7;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.sql.*;
import com.google.gson.Gson;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.core.StandardContext;
/** External-only real login fixture: only provider URL/config and synthetic database are replaced. */
public final class Server {
 public static void main(String[] args) throws Exception {
  Path run=Path.of(args[0]).toAbsolutePath().normalize();
  if(!run.startsWith(Path.of(".b7").toAbsolutePath().normalize()))throw new IllegalArgumentException("Owned B7 path required");
  Path expanded=Files.createDirectories(run.resolve("tomcat/webapps/ROOT"));
  try(var zip=new java.util.zip.ZipInputStream(Files.newInputStream(Path.of(args[1])))){
   java.util.zip.ZipEntry e;while((e=zip.getNextEntry())!=null){Path p=expanded.resolve(e.getName()).normalize();if(!p.startsWith(expanded))throw new IllegalArgumentException();if(e.isDirectory())Files.createDirectories(p);else{Files.createDirectories(p.getParent());Files.copy(zip,p);}}
  }
  var provider=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),8);
  provider.createContext("/verify",exchange->{
   byte[] body=exchange.getRequestBody().readNBytes(8193);if(body.length>8192){exchange.sendResponseHeaders(413,-1);exchange.close();return;}
   boolean accepted=new String(body,java.nio.charset.StandardCharsets.UTF_8).contains("response=accepted-synthetic-token");
   byte[] reply=("{\"success\":"+accepted+",\"score\":0.9,\"action\":\"login\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
   exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,reply.length);exchange.getResponseBody().write(reply);exchange.close();
  });provider.start();
  var config=expanded.resolve("WEB-INF/classes/PasswordGenerator/Properties/config.properties");
  if(!Files.exists(config))config=expanded.resolve("WEB-INF/classes/passwordgenerator/properties/config.properties");
  String source=Files.readString(config);source=source.replaceAll("(?m)^recaptcha_verify_url=.*$","recaptcha_verify_url=http://127.0.0.1:"+provider.getAddress().getPort()+"/verify");Files.writeString(config,source);
  Tomcat tomcat=new Tomcat();tomcat.setBaseDir(run.resolve("tomcat").toString());tomcat.setPort(0);tomcat.getConnector().setProperty("address","127.0.0.1");
  var context=(StandardContext)tomcat.addWebapp("",expanded.toString());context.setFailCtxIfServletStartFails(true);
  try{
   tomcat.start();if(!context.getState().isAvailable())throw new IllegalStateException("WAR unavailable");
   var loader=context.getLoader().getClassLoader();var bcrypt=Class.forName("org.mindrot.jbcrypt.BCrypt",true,loader);String salt=(String)bcrypt.getMethod("gensalt",int.class).invoke(null,10);String hash=(String)bcrypt.getMethod("hashpw",String.class,String.class).invoke(null,"synthetic-password-b7",salt);
   try(Connection db=(Connection)Class.forName("common.DatabaseUtils",true,loader).getMethod("getSQLite3Connection").invoke(null)){
    db.createStatement().execute("CREATE TABLE IF NOT EXISTS user_table(username TEXT PRIMARY KEY, hashed_password TEXT NOT NULL, failed_attempts INTEGER DEFAULT 0, locked_until TEXT, created_date TEXT, recovery_code_hash TEXT, role TEXT DEFAULT 'user')");
    try(var stmt=db.prepareStatement("INSERT INTO user_table(username,hashed_password,created_date) VALUES(?,?,?)")){stmt.setString(1,"b7_login");stmt.setString(2,hash);stmt.setString(3,"2026-10-05");stmt.executeUpdate();}
   }
   Files.writeString(run.resolve("ready.json"),new Gson().toJson(Map.of("origin","http://127.0.0.1:"+tomcat.getConnector().getLocalPort(),"provider","owned loopback HTTP")));
   while(!Files.exists(run.resolve("stop")))Thread.sleep(100);
  }finally{tomcat.stop();tomcat.destroy();provider.stop(0);Files.deleteIfExists(run.resolve("ready.json"));}
 }
}
