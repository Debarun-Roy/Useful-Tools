package backendsupport;

import com.google.gson.Gson;
import common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class Responses {
    private static final Gson GSON = new Gson();
    static void send(HttpServletResponse response, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 262144) {
            status = 500;
            bytes = GSON.toJson(ApiResponse.fail("Preview response unavailable.", "RESPONSE_LIMIT")).getBytes(StandardCharsets.UTF_8);
        }
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getOutputStream().write(bytes);
    }
    static void fail(HttpServletResponse response, int status, String code) throws IOException {
        send(response, status, ApiResponse.fail("Request rejected. Review access and specification requirements.", code));
    }
}
