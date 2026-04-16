package common.startup;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.annotation.WebListener;

/**
 * CookieConfigListener — Configures session cookie attributes at startup.
 *
 * TOMCAT 11 COOKIE ISSUE:
 * In Tomcat 11.0.21, the context.xml attribute "sameSiteCookies" is ignored.
 * We configure the SessionCookieConfig programmatically to ensure JSESSIONID
 * cookie has SameSite=None attribute for cross-origin requests.
 *
 * This listener runs early (before first request) to configure both:
 * 1. SessionCookieConfig - controls JSESSIONID cookie attributes
 * 2. Default cookie attributes - via Cookie wrapper in SameSiteFilter
 *
 * NOTE: This works in conjunction with SameSiteFilter, which also ensures
 * ALL cookies have SameSite=None at the filter level (defense in depth).
 */
@WebListener
public class CookieConfigListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        
        System.out.println("[CookieConfigListener] Initializing session cookie configuration");
        
        try {
            // Configure SessionCookieConfig - this controls JSESSIONID cookie
            SessionCookieConfig sessionCookieConfig = context.getSessionCookieConfig();
            
            System.out.println("[CookieConfigListener] Current SessionCookieConfig:");
            System.out.println("  ├─ Secure: " + sessionCookieConfig.isSecure());
            System.out.println("  ├─ HttpOnly: " + sessionCookieConfig.isHttpOnly());
            
            // Enable Secure flag for HTTPS (should already be true from web.xml, but ensure it)
            if (!sessionCookieConfig.isSecure()) {
                sessionCookieConfig.setSecure(true);
                System.out.println("[CookieConfigListener] Set SessionCookieConfig.Secure = true");
            }
            
            // Enable HttpOnly flag (should already be true from web.xml, but ensure it)
            if (!sessionCookieConfig.isHttpOnly()) {
                sessionCookieConfig.setHttpOnly(true);
                System.out.println("[CookieConfigListener] Set SessionCookieConfig.HttpOnly = true");
            }
            
            // Set SameSite=None via reflection (Jakarta 6.0 doesn't have SameSite yet)
            try {
                var setSameSiteMethod = SessionCookieConfig.class.getMethod("setSameSite", String.class);
                setSameSiteMethod.invoke(sessionCookieConfig, "None");
                System.out.println("[CookieConfigListener] Set SessionCookieConfig.SameSite = None (via reflection)");
            } catch (NoSuchMethodException e) {
                System.out.println("[CookieConfigListener] setSameSite(String) method not available - relying on SameSiteFilter");
            } catch (Exception e) {
                System.out.println("[CookieConfigListener] Warning setting SameSite: " + e.getMessage());
            }
            
            System.out.println("[CookieConfigListener] Updated SessionCookieConfig:");
            System.out.println("  ├─ Secure: " + sessionCookieConfig.isSecure());
            System.out.println("  └─ HttpOnly: " + sessionCookieConfig.isHttpOnly());
            
        } catch (Exception e) {
            System.out.println("[CookieConfigListener] ERROR: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("[CookieConfigListener] Initialization complete - SameSiteFilter will catch remaining cookies");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // No cleanup needed
    }
}
