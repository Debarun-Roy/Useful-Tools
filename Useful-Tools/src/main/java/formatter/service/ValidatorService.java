package formatter.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ValidatorService — Validates JSON responses against JSON schemas.
 *
 * ── Responsibilities ─────────────────────────────────────────────────────
 * - Validate JSON against provided schema
 * - Provide detailed validation error reporting
 * - Check for required fields
 * - Verify field types
 *
 * ── Limitations ──────────────────────────────────────────────────────────
 * This is a basic JSON schema validator that supports:
 * - Type checking (string, number, boolean, object, array, null)
 * - Required fields validation
 * - Basic structure validation
 *
 * For complex JSON Schema validation (minProperties, maxItems, patterns, etc.),
 * consider using a dedicated library like json-schema-validator on Maven.
 *
 * ── Thread Safety ────────────────────────────────────────────────────────
 * All methods are stateless and thread-safe.
 */
public class ValidatorService {

    private static final Gson gson = new Gson();

    /**
     * Validates a JSON document against a JSON schema.
     *
     * @param jsonString The JSON document to validate
     * @param schemaString The JSON schema to validate against
     * @return ValidationReport with success status and detailed results
     */
    public ValidationReport validateAgainstSchema(String jsonString, String schemaString) {
        ValidationReport report = new ValidationReport();

        try {
            JsonElement jsonElement = JsonParser.parseString(jsonString);
            JsonElement schemaElement = JsonParser.parseString(schemaString);

            validateElement(jsonElement, schemaElement, "$", report);

            if (report.errors.isEmpty()) {
                report.isValid = true;
            }
        } catch (JsonSyntaxException e) {
            report.isValid = false;
            report.errors.add("JSON parsing error: " + e.getMessage());
        }

        return report;
    }

    /**
     * Validates that all required fields are present in a JSON object.
     *
     * @param jsonString The JSON document
     * @param requiredFields List of required field names
     * @return ValidationReport with success status
     */
    public ValidationReport validateRequiredFields(String jsonString, List<String> requiredFields) {
        ValidationReport report = new ValidationReport();

        try {
            JsonElement jsonElement = JsonParser.parseString(jsonString);

            if (!jsonElement.isJsonObject()) {
                report.isValid = false;
                report.errors.add("Expected JSON object at root");
                return report;
            }

            var jsonObject = jsonElement.getAsJsonObject();

            for (String fieldName : requiredFields) {
                if (!jsonObject.has(fieldName)) {
                    report.errors.add("Missing required field: " + fieldName);
                } else if (jsonObject.get(fieldName).isJsonNull()) {
                    report.errors.add("Required field is null: " + fieldName);
                }
            }

            report.isValid = report.errors.isEmpty();
        } catch (JsonSyntaxException e) {
            report.isValid = false;
            report.errors.add("JSON parsing error: " + e.getMessage());
        }

        return report;
    }

    /**
     * Checks that a JSON response has expected top-level keys.
     *
     * @param jsonString The JSON document
     * @param expectedKeys Expected keys in the object
     * @return ValidationReport with success status
     */
    public ValidationReport validateStructure(String jsonString, List<String> expectedKeys) {
        ValidationReport report = new ValidationReport();

        try {
            JsonElement jsonElement = JsonParser.parseString(jsonString);

            if (!jsonElement.isJsonObject()) {
                report.isValid = false;
                report.errors.add("Expected JSON object at root");
                return report;
            }

            var jsonObject = jsonElement.getAsJsonObject();
            var actualKeys = jsonObject.keySet();

            for (String expectedKey : expectedKeys) {
                if (!actualKeys.contains(expectedKey)) {
                    report.errors.add("Missing expected key: " + expectedKey);
                }
            }

            report.isValid = report.errors.isEmpty();
        } catch (JsonSyntaxException e) {
            report.isValid = false;
            report.errors.add("JSON parsing error: " + e.getMessage());
        }

        return report;
    }

    // ── Private validation logic ─────────────────────────────────────────────

    /**
     * Recursively validates a JSON element against a schema element.
     */
    private void validateElement(JsonElement jsonElement, JsonElement schemaElement,
                                 String path, ValidationReport report) {
        if (!schemaElement.isJsonObject()) {
            return;
        }

        var schema = schemaElement.getAsJsonObject();

        // Check type if specified
        if (schema.has("type")) {
            String expectedType = schema.get("type").getAsString();
            if (!isTypeMatch(jsonElement, expectedType)) {
                report.errors.add(path + ": Expected type '" + expectedType +
                        "' but got '" + getJsonType(jsonElement) + "'");
                return;
            }
        }

        // Validate object properties
        if (jsonElement.isJsonObject() && schema.has("properties")) {
            var jsonObj = jsonElement.getAsJsonObject();
            var properties = schema.getAsJsonObject("properties");

            for (String key : properties.keySet()) {
                if (jsonObj.has(key)) {
                    validateElement(jsonObj.get(key),
                            properties.get(key),
                            path + "." + key,
                            report);
                }
            }

            // Check required fields
            if (schema.has("required")) {
                var required = schema.getAsJsonArray("required");
                for (var item : required) {
                    String fieldName = item.getAsString();
                    if (!jsonObj.has(fieldName)) {
                        report.errors.add(path + ": Missing required field '" + fieldName + "'");
                    }
                }
            }
        }

        // Validate array items
        if (jsonElement.isJsonArray() && schema.has("items")) {
            var jsonArray = jsonElement.getAsJsonArray();
            var itemSchema = schema.get("items");

            for (int i = 0; i < jsonArray.size(); i++) {
                validateElement(jsonArray.get(i), itemSchema,
                        path + "[" + i + "]", report);
            }
        }
    }

    /**
     * Determines the JSON type of an element.
     */
    private String getJsonType(JsonElement element) {
        if (element.isJsonNull()) return "null";
        if (element.isJsonPrimitive()) {
            var prim = element.getAsJsonPrimitive();
            if (prim.isBoolean()) return "boolean";
            if (prim.isNumber()) return "number";
            if (prim.isString()) return "string";
        }
        if (element.isJsonObject()) return "object";
        if (element.isJsonArray()) return "array";
        return "unknown";
    }

    /**
     * Checks if a JSON element matches an expected type.
     */
    private boolean isTypeMatch(JsonElement element, String expectedType) {
        return switch (expectedType) {
            case "null" -> element.isJsonNull();
            case "boolean" -> element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean();
            case "number" -> element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber();
            case "string" -> element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
            case "object" -> element.isJsonObject();
            case "array" -> element.isJsonArray();
            default -> true; // Unknown types are assumed to match
        };
    }

    // ── Helper Classes ───────────────────────────────────────────────────────

    /**
     * Detailed validation report with error list.
     */
    public static class ValidationReport {
        public boolean isValid = false;
        public List<String> errors = new ArrayList<>();

        /**
         * Converts the report to a map for JSON serialization.
         */
        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("isValid", isValid);
            result.put("errors", errors);
            result.put("errorCount", errors.size());
            return result;
        }
    }
}
