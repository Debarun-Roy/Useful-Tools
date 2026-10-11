package backendsupport;

import common.AppConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.ByteArrayOutputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalOriginIsolationTest {
    @Test void hostedProfileStillRequiresOriginEvenWithLocalReferer() throws Exception {
        check(null, "valid-token", false, "ORIGIN_REJECTED");
    }
    @Test void hostedProfileStillRequiresCsrfWithTrustedOrigin() throws Exception {
        check("http://localhost:5173", "wrong-token", false, "CSRF_INVALID");
    }
    @Test void hostedProfileAcceptsMatchingOwnOriginAndCsrf() throws Exception {
        check("http://localhost:5173", "valid-token", true, null);
    }

    private void check(String origin, String token, boolean expected, String code) throws Exception {
        assertFalse(AppConfig.isLocal(), "Run Maven tests outside the local runtime profile");
        Map<String,Object> attributes = new HashMap<>();
        attributes.put("username", "synthetic-guest");
        attributes.put("csrfToken", "valid-token");
        HttpSession session = (HttpSession) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{HttpSession.class}, (p,m,a) -> switch(m.getName()) {
                case "getAttribute" -> attributes.get(a[0]);
                case "setAttribute" -> { attributes.put((String)a[0],a[1]); yield null; }
                default -> null;
            });
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{HttpServletRequest.class}, (p,m,a) -> switch(m.getName()) {
                case "getMethod" -> "POST";
                case "getSession" -> session;
                case "getScheme" -> "http";
                case "getServerName" -> "localhost";
                case "getServerPort" -> 5173;
                case "isSecure" -> false;
                case "getHeader" -> switch((String)a[0]) {
                    case "Origin" -> origin;
                    case "Referer" -> "http://localhost:5173/login";
                    case "X-XSRF-TOKEN" -> token;
                    default -> null;
                };
                default -> null;
            });
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        ServletOutputStream output = new ServletOutputStream() {
            @Override public void write(int value) { body.write(value); }
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(WriteListener listener) { }
        };
        AtomicInteger status = new AtomicInteger(200);
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{HttpServletResponse.class}, (p,m,a) -> switch(m.getName()) {
                case "getOutputStream" -> output;
                case "setStatus" -> { status.set((int)a[0]); yield null; }
                default -> null;
            });
        var filter = new BackendSupportSecurityFilter();
        filter.init(null);
        AtomicBoolean proceeded = new AtomicBoolean();
        filter.doFilter(request,response,(req,res) -> proceeded.set(true));
        assertEquals(expected,proceeded.get());
        if (!expected) { assertEquals(403,status.get()); assertTrue(body.toString().contains(code)); }
    }
}
