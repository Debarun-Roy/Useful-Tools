package formatter.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * FormatterService — Handles formatting and minification of JSON/XML/YAML.
 *
 * ── Responsibilities ─────────────────────────────────────────────────────
 * - Format JSON with customizable indentation
 * - Minify JSON (remove all whitespace)
 * - Parse and validate JSON syntax
 * - Provide statistics on formatted output
 *
 * ── Notes ────────────────────────────────────────────────────────────────
 * - Uses Google Gson for JSON parsing and formatting
 * - All methods are stateless and thread-safe
 * - Throws JsonSyntaxException for invalid input
 */
public class FormatterService {

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final Gson compactGson = new Gson();

    /**
     * Formats JSON with pretty-printing (indentation).
     *
     * @param jsonString The JSON string to format
     * @return Formatted JSON with newlines and indentation
     * @throws JsonSyntaxException if the input is not valid JSON
     */
    public String formatJson(String jsonString) throws JsonSyntaxException {
        if (jsonString == null || jsonString.isBlank()) {
            throw new JsonSyntaxException("JSON input cannot be empty");
        }

        try {
            JsonElement element = JsonParser.parseString(jsonString);
            return gson.toJson(element);
        } catch (JsonSyntaxException e) {
            throw new JsonSyntaxException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Minifies JSON by removing all unnecessary whitespace.
     *
     * @param jsonString The JSON string to minify
     * @return Minified JSON without newlines or extra spaces
     * @throws JsonSyntaxException if the input is not valid JSON
     */
    public String minifyJson(String jsonString) throws JsonSyntaxException {
        if (jsonString == null || jsonString.isBlank()) {
            throw new JsonSyntaxException("JSON input cannot be empty");
        }

        try {
            JsonElement element = JsonParser.parseString(jsonString);
            return compactGson.toJson(element);
        } catch (JsonSyntaxException e) {
            throw new JsonSyntaxException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Validates JSON syntax without modifying the input.
     *
     * @param jsonString The JSON string to validate
     * @return true if valid JSON, false otherwise
     */
    public boolean validateJson(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) {
            return false;
        }

        try {
            JsonParser.parseString(jsonString);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    /**
     * Validates JSON and returns detailed error information if invalid.
     *
     * @param jsonString The JSON string to validate
     * @return ValidationResult with status and optional error message
     */
    public ValidationResult validateJsonDetailed(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) {
            return new ValidationResult(false, "JSON input cannot be empty");
        }

        try {
            JsonParser.parseString(jsonString);
            return new ValidationResult(true, null);
        } catch (JsonSyntaxException e) {
            return new ValidationResult(false, "Invalid JSON: " + e.getMessage());
        }
    }

    /**
     * Provides statistics about a JSON string.
     *
     * @param jsonString The JSON string to analyze
     * @return Map of statistics: size (characters), minified_size, compression_ratio
     * @throws JsonSyntaxException if the input is not valid JSON
     */
    public FormattingStats getFormattingStats(String jsonString) throws JsonSyntaxException {
        if (jsonString == null || jsonString.isBlank()) {
            throw new JsonSyntaxException("JSON input cannot be empty");
        }

        // Parse and validate
        JsonElement element = JsonParser.parseString(jsonString);

        // Calculate sizes
        int originalSize = jsonString.length();
        String minified = compactGson.toJson(element);
        int minifiedSize = minified.length();
        double compressionRatio = ((double) (originalSize - minifiedSize) / originalSize) * 100;

        return new FormattingStats(originalSize, minifiedSize, compressionRatio);
    }

    // ── Helper Classes ───────────────────────────────────────────────────────

    /**
     * Result of JSON validation attempt.
     */
    public static class ValidationResult {
        public final boolean isValid;
        public final String errorMessage;

        public ValidationResult(boolean isValid, String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }
    }

    /**
     * Statistics about formatting operations.
     */
    public static class FormattingStats {
        public final int originalSize;
        public final int minifiedSize;
        public final double compressionRatio; // percentage

        public FormattingStats(int originalSize, int minifiedSize, double compressionRatio) {
            this.originalSize = originalSize;
            this.minifiedSize = minifiedSize;
            this.compressionRatio = compressionRatio;
        }
    }
}
