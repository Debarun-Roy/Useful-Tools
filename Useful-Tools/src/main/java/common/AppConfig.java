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

        for (String resourcePath : RESOURCE_CANDIDATES) {
            try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    continue;
                }
                properties.load(is);
                return properties;
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to load config.properties from " + resourcePath, ioe);
            }
        }

        throw new RuntimeException("config.properties not found in classpath");
    }
}
