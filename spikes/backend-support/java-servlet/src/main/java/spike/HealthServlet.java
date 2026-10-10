package spike;
import jakarta.servlet.http.*;
import java.io.IOException;

/** Harmless reference endpoint only. Not an authentication starter. */
public class HealthServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"status\":\"ok\",\"version\":\"0.1.0\",\"target\":\"java-servlet\"}");
    }
}
