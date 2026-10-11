package common.filter;

import java.lang.reflect.Proxy;
import java.util.stream.Stream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class LocalRequestPolicyTest {
    static Stream<Arguments> requests() {
        return Stream.of(
            Arguments.of("POST", "127.0.0.1", "localhost", "http://localhost:5173", null, true),
            Arguments.of("POST", "::1", "localhost", "http://localhost:8080", null, true),
            Arguments.of("POST", "127.0.0.1", "localhost", null, "http://localhost:5173/login", true),
            Arguments.of("POST", "127.0.0.1", "localhost", null, "http://localhost:8080/login?next=/", true),
            Arguments.of("DELETE", "127.0.0.1", "localhost", null, "http://localhost:5173/", true),
            Arguments.of("GET", "127.0.0.1", "localhost", null, null, true),
            Arguments.of("HEAD", "127.0.0.1", "localhost", null, null, true),
            Arguments.of("OPTIONS", "127.0.0.1", "localhost", "http://localhost:5173", null, true),
            Arguments.of("POST", "127.0.0.1", "localhost", null, null, false),
            Arguments.of("POST", "127.0.0.1", "localhost", "null", "http://localhost:5173/login", false),
            Arguments.of("POST", "127.0.0.1", "localhost", "", "http://localhost:5173/login", false),
            Arguments.of("POST", "127.0.0.1", "localhost", "https://evil.invalid", "http://localhost:5173/login", false),
            Arguments.of("GET", "127.0.0.1", "localhost", "https://evil.invalid", null, false),
            Arguments.of("POST", "192.0.2.1", "localhost", "http://localhost:5173", null, false),
            Arguments.of("POST", "127.0.0.1", "127.0.0.1", "http://localhost:5173", null, false),
            Arguments.of("POST", "127.0.0.1", "evil.invalid", "http://localhost:5173", null, false),
            Arguments.of("POST", "127.0.0.1", "localhost", null, "http://localhost.evil.invalid:5173/login", false),
            Arguments.of("POST", "127.0.0.1", "localhost", null, "http://localhost:5173@evil.invalid/login", false),
            Arguments.of("POST", "127.0.0.1", "localhost", null, "http://evil@localhost:5173/login", false),
            Arguments.of("POST", "127.0.0.1", "localhost", null, "https://localhost:5173/login", false),
            Arguments.of("POST", "127.0.0.1", "localhost", null, "http://localhost:5174/login", false),
            Arguments.of("POST", "127.0.0.1", "localhost", null, "http://127.0.0.1:5173/login", false),
            Arguments.of("POST", "127.0.0.1", "localhost", null, "/login", false),
            Arguments.of("POST", "127.0.0.1", "localhost", null, "not a URI", false),
            Arguments.of("POST", "127.0.0.1", "localhost", null, "http://localhost:5173/#fragment", false)
        );
    }

    @ParameterizedTest @MethodSource("requests")
    void validatesSourceWithoutWeakeningOriginOrHostChecks(String method, String remote, String host,
            String origin, String referer, boolean allowed) throws Exception {
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class}, (proxy, called, args) ->
                switch (called.getName()) {
                    case "getMethod" -> method;
                    case "getRemoteAddr" -> remote;
                    case "getServerName" -> host;
                    case "getHeader" -> "Origin".equals(args[0]) ? origin : "Referer".equals(args[0]) ? referer : null;
                    default -> throw new UnsupportedOperationException(called.getName());
                });
        assertEquals(allowed, LocalRequestPolicy.rejection(request) == null);
    }
}
