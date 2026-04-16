package common.startup;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * CookieConfigListener — Configures Tomcat's cookie processor at startup.
 *
 * TOMCAT 11 COOKIE ISSUE:
 * In Tomcat 11.0.21, the context.xml attribute "sameSiteCookies" is ignored.
 * We need to programmatically configure cookie attributes at runtime.
 *
 * This listener runs when the web application starts and configures
 * Tomcat's default cookie processor to add SameSite=None to all cookies.
 *
 * NOTE: This works in conjunction with SameSiteFilter, which also ensures
 * cookies have SameSite=None at the filter level (belt and suspenders approach).
 */
@WebListener
public class CookieConfigListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        
        System.out.println("CookieConfigListener: Initializing Tomcat cookie configuration");
        
        try {
            // Get Tomcat StandardContext through ServletContext
            // This is a bit of a hack, but necessary for Tomcat 11 cookie configuration
            Object contextImpl = context.getAttribute("org.apache.catalina.core.StandardContext");
            
            if (contextImpl != null) {
                // Try to configure the cookie processor
                Class<?> standardCtxClass = Class.forName("org.apache.catalina.core.StandardContext");
                if (standardCtxClass.isInstance(contextImpl)) {
                    System.out.println("  └─ StandardContext found, attempting to configure cookies");
                    
                    // Get the cookie processor
                    var getCookieProcessor = standardCtxClass.getMethod("getCookieProcessor");
                    Object cookieProcessor = getCookieProcessor.invoke(contextImpl);
                    
                    if (cookieProcessor != null) {
                        Class<?> cookieProcessorClass = cookieProcessor.getClass();
                        
                        // Set SameSite attribute using reflection
                        try {
                            var setSameSite = cookieProcessorClass.getMethod("setSameSiteCookies", String.class);
                            setSameSite.invoke(cookieProcessor, "None");
                            System.out.println("  ├─ Cookie Processor: setSameSiteCookies = None");
                        } catch (NoSuchMethodException e) {
                            System.out.println("  └─ setSameSiteCookies method not available in Tomcat 11");
                        }
                        
                        // Ensure Secure flag is set
                        try {
                            var setSecure = cookieProcessorClass.getMethod("setSecure", boolean.class);
                            setSecure.invoke(cookieProcessor, true);
                            System.out.println("  └─ Cookie Processor: setSecure = true");
                        } catch (NoSuchMethodException e) {
                            System.out.println("  └─ setSecure method not available");
                        }
                    }
                }
            } else {
                System.out.println("  └─ Could not access StandardContext, relying on SameSiteFilter");
            }
        } catch (Exception e) {
            System.out.println("ERROR in CookieConfigListener: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("CookieConfigListener: Initialization complete");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // No cleanup needed
    }
}
