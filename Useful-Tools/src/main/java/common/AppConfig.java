package common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Shared application-properties loader.
 *
 * Several classes previously opened config.properties independently and relied
 * on a lowercase resource path. That is fragile on case-sensitive classpaths,
 * so this class centralises the lookup and supports both the canonical and
 * legacy resource names.
 */
public final class AppConfig {

    private static final String[] RESOURCE_CANDIDATES = {
            "PasswordGenerator/Properties/config.properties",
            "passwordgenerator/properties/config.properties"
    };

    private static final Properties PROPERTIES = loadProperties();

    public static boolean isLocal() {
        return "local".equals(System.getenv("UT_PROFILE"));
    }

    public static java.nio.file.Path localDirectory() {
        String directory = System.getenv("UT_LOCAL_DIR");
        if (!isLocal() || !"true".equals(System.getProperty("usefultools.local.launcher"))
                || System.getenv("RAILWAY_ENVIRONMENT_ID") != null || System.getenv("VERCEL") != null
                || directory == null || directory.isBlank()) {
            throw new IllegalStateException("Local profile requires the loopback development launcher; hosted use is forbidden");
        }
        java.nio.file.Path path = java.nio.file.Path.of(directory).toAbsolutePath().normalize();
        if (!path.getFileName().toString().equals(".local") || !java.nio.file.Files.isDirectory(path)) {
            throw new IllegalStateException("UT_LOCAL_DIR must be the dedicated .local runtime directory");
        }
        return path;
    }

    private AppConfig() { }

    public static String getRequired(String key) {
        String value = PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required config key missing: " + key);
        }
        return value.trim();
    }

    public static String getOrDefault(String key, String defaultValue) {
        String value = PROPERTIES.getProperty(key);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    public static Set<String> getCsvSet(String key, Set<String> defaultValue) {
        String value = PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) {
            return new LinkedHashSet<>(defaultValue);
        }

        LinkedHashSet<String> result = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        return result.isEmpty() ? new LinkedHashSet<>(defaultValue) : result;
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        String profile = System.getenv("UT_PROFILE");
        if (profile != null && !Set.of("local", "staging", "production").contains(profile)) {
            throw new IllegalStateException("UT_PROFILE must be local, staging or production");
        }
        if ("staging".equals(profile)) {
            String origins = System.getenv("CORS_ALLOWED_ORIGINS");
            String path = System.getenv("SQLITE_DB_PATH");
            String url = System.getenv("SQLITE_DB_URL");
            if (origins == null || origins.isBlank()
                    || (path == null || path.isBlank()) && (url == null || url.isBlank())) {
                throw new IllegalStateException("Staging requires explicit CORS origins and isolated database configuration");
            }
        }

        for (String resourcePath : RESOURCE_CANDIDATES) {
            try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    continue;
                }
                properties.load(is);
                if (isLocal()) {
                    java.nio.file.Path directory = localDirectory();
                    try (InputStream local = java.nio.file.Files.newInputStream(directory.resolve("local.properties"))) {
                        Properties overlay = new Properties();
                        overlay.load(local);
                        if (!Set.of("local_external_integrations").containsAll(overlay.stringPropertyNames())) {
                            throw new IllegalStateException("Unsupported local overlay key; database and provider URLs cannot be overridden");
                        }
                        String integration = overlay.getProperty("local_external_integrations", "disabled");
                        if (!Set.of("disabled", "recaptcha").contains(integration)) {
                            throw new IllegalStateException("local_external_integrations must be disabled or recaptcha");
                        }
                        properties.putAll(overlay);
                    }
                    // Local origins cannot inherit a hosted origin from the bundled config.
                    properties.setProperty("cors_allowed_origins", "http://localhost:5173,http://localhost:8080");
                } else {
                    String origins = System.getenv("CORS_ALLOWED_ORIGINS");
                    if (origins != null && !origins.isBlank()) {
                        if (origins.contains("*")) throw new IllegalStateException("Credentialed CORS requires explicit origins");
                        properties.setProperty("cors_allowed_origins", origins);
                    }
                }
                return properties;
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to load config.properties from " + resourcePath, ioe);
            }
        }

        throw new RuntimeException("config.properties not found in classpath");
    }
}
