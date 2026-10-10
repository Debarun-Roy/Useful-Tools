package local;

import common.AppConfig;
import common.LocalCookiePolicy;
import jakarta.servlet.http.*;
import java.lang.reflect.*;
import java.util.*;

/** Isolated-process configuration/cookie probes; not an application endpoint or WAR class. */
public final class ConfigProbe {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("database")) {
            var method = Class.forName("common.DatabaseUtils").getDeclaredMethod("resolveJdbcUrl");
            method.setAccessible(true);
            String url = (String) method.invoke(null);
            if (AppConfig.isLocal() && !url.equals("jdbc:sqlite:" + AppConfig.localDirectory().resolve("data/UsefulTools.db")))
                throw new AssertionError("Unexpected local DB");
        } else if (args[0].equals("cookies")) {
            String source = "XSRF-TOKEN=synthetic; Secure; HttpOnly; SameSite=None; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT;";
            String expected = "XSRF-TOKEN=synthetic; HttpOnly; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; SameSite=Lax";
            if (!LocalCookiePolicy.rewrite(source).equals(expected)) throw new AssertionError("Cookie attributes lost");
            if (!LocalCookiePolicy.rewrite("x=y; sEcUrE; SameSite=None; SameSite=Strict").equals("x=y; SameSite=Lax"))
                throw new AssertionError("Duplicate cookie attributes");
            List<Object> captured = new ArrayList<>();
            HttpServletResponse sink = (HttpServletResponse) Proxy.newProxyInstance(ConfigProbe.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class}, (proxy, method, values) -> {
                        if (method.getName().equals("addCookie")) captured.add(values[0]);
                        if (method.getName().equals("addHeader") || method.getName().equals("setHeader")) captured.add(values[1]);
                        return null;
                    });
            var constructor = Class.forName("common.filter.SameSiteFilter$SameSiteCookieWrapper")
                    .getDeclaredConstructor(HttpServletResponse.class);
            constructor.setAccessible(true);
            HttpServletResponse wrapper = (HttpServletResponse) constructor.newInstance(sink);
            Cookie cookie = new Cookie("XSRF-TOKEN", "synthetic");
            cookie.setHttpOnly(false); cookie.setPath("/"); cookie.setMaxAge(0);
            wrapper.addCookie(cookie);
            wrapper.addHeader("Set-Cookie", source);
            wrapper.setHeader("Set-Cookie", "other=value; Path=/");
            if (captured.size() != 3 || cookie.getSecure() == AppConfig.isLocal()
                    || !cookie.getAttribute("SameSite").equals(AppConfig.isLocal() ? "Lax" : "None")
                    || cookie.isHttpOnly() || cookie.getMaxAge() != 0) throw new AssertionError("Cookie policy mismatch");
            if (AppConfig.isLocal() && !captured.get(1).equals(expected)) throw new AssertionError("Raw cookie policy mismatch");
            if (!AppConfig.isLocal() && !captured.get(1).equals(source)) throw new AssertionError("Hosted cookie changed");
        } else {
            AppConfig.getRequired("cors_allowed_origins");
        }
        System.out.println("PASS " + args[0]);
    }
}
