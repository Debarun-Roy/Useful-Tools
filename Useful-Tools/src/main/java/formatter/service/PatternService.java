package formatter.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

/**
 * PatternService — Manages regex pattern validation and matching.
 *
 * ── Responsibilities ─────────────────────────────────────────────────────
 * - Validate regex pattern syntax
 * - Test patterns against strings
 * - Provide common regex patterns library
 * - Report detailed match information
 *
 * ── Thread Safety ────────────────────────────────────────────────────────
 * All methods are stateless and thread-safe. Note that compiling patterns
 * multiple times is acceptable for occasional use; for high-frequency use
 * of the same pattern, caching in the caller is recommended.
 */
public class PatternService {

    // ── Common Regex Patterns Library ────────────────────────────────────────

    /**
     * Returns a map of common regex patterns by category.
     * Each pattern includes a name, regex, and example.
     */
    public static Map<String, List<PatternDefinition>> getCommonPatterns() {
        Map<String, List<PatternDefinition>> patterns = new LinkedHashMap<>();

        // Email patterns
        patterns.put("email", List.of(
                new PatternDefinition(
                        "Email (simple)",
                        "^[^@]+@[^@]+\\.[^@]+$",
                        "user@example.com",
                        "Basic email validation - does not follow all RFC rules"
                ),
                new PatternDefinition(
                        "Email (RFC 5322)",
                        "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$",
                        "user.name+tag@example.co.uk",
                        "More complete RFC 5322 email pattern"
                )
        ));

        // URL patterns
        patterns.put("url", List.of(
                new PatternDefinition(
                        "URL (http/https)",
                        "^https?://[^\\s]+$",
                        "https://www.example.com",
                        "Matches HTTP and HTTPS URLs"
                ),
                new PatternDefinition(
                        "URL (strict)",
                        "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$",
                        "https://example.com/path?query=value",
                        "Stricter URL validation"
                )
        ));

        // Phone patterns
        patterns.put("phone", List.of(
                new PatternDefinition(
                        "Phone (US)",
                        "^(?:\\+?1[-.]?)?(?:\\(?[0-9]{3}\\)?[-.]?)?[0-9]{3}[-.]?[0-9]{4}$",
                        "+1 (555) 123-4567",
                        "US phone numbers with various formats"
                ),
                new PatternDefinition(
                        "Phone (international)",
                        "^\\+?[1-9]\\d{1,14}$",
                        "+14155552671",
                        "E.164 international format"
                )
        ));

        // Number patterns
        patterns.put("number", List.of(
                new PatternDefinition(
                        "Integer",
                        "^-?\\d+$",
                        "-42",
                        "Integers, positive or negative"
                ),
                new PatternDefinition(
                        "Decimal number",
                        "^-?\\d+(\\.\\d+)?$",
                        "3.14",
                        "Numbers with optional decimal part"
                ),
                new PatternDefinition(
                        "Positive float",
                        "^\\d+\\.\\d{2}$",
                        "19.99",
                        "Currency format (2 decimal places)"
                )
        ));

        // Text patterns
        patterns.put("text", List.of(
                new PatternDefinition(
                        "Alphanumeric only",
                        "^[a-zA-Z0-9]+$",
                        "HelloWorld123",
                        "Only letters and numbers"
                ),
                new PatternDefinition(
                        "Slug format",
                        "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                        "my-awesome-slug",
                        "URL-safe format"
                ),
                new PatternDefinition(
                        "Username",
                        "^[a-zA-Z0-9_]{3,16}$",
                        "user_name123",
                        "3-16 characters, alphanumeric and underscore"
                )
        ));

        // Code patterns
        patterns.put("code", List.of(
                new PatternDefinition(
                        "Hex color",
                        "^#?([a-fA-F0-9]{6}|[a-fA-F0-9]{3})$",
                        "#FF5733",
                        "CSS hex color codes"
                ),
                new PatternDefinition(
                        "IPv4 address",
                        "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$",
                        "192.168.1.1",
                        "IPv4 address validation"
                ),
                new PatternDefinition(
                        "UUID",
                        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
                        "550e8400-e29b-41d4-a716-446655440000",
                        "UUID v4 format"
                )
        ));

        return patterns;
    }

    // ── Pattern Validation ───────────────────────────────────────────────────

    /**
     * Validates that a regex pattern is syntactically correct.
     *
     * @param pattern The regex pattern to validate
     * @return ValidationResult with status and optional error message
     */
    public ValidationResult validatePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return new ValidationResult(false, "Pattern cannot be empty");
        }

        try {
            java.util.regex.Pattern.compile(pattern);
            return new ValidationResult(true, null);
        } catch (PatternSyntaxException e) {
            return new ValidationResult(false, "Pattern error: " + e.getDescription() +
                    " (at position " + e.getIndex() + ")");
        }
    }

    // ── Pattern Testing ─────────────────────────────────────────────────────

    /**
     * Tests a regex pattern against a string and returns match results.
     *
     * @param pattern The regex pattern
     * @param testString The string to test against
     * @return MatchResult with matches found and statistics
     */
    public MatchResult testPattern(String pattern, String testString) {
        MatchResult result = new MatchResult();

        try {
            var compiledPattern = java.util.regex.Pattern.compile(pattern);
            var matcher = compiledPattern.matcher(testString);

            result.patternValid = true;

            while (matcher.find()) {
                Match match = new Match();
                match.text = matcher.group();
                match.startIndex = matcher.start();
                match.endIndex = matcher.end();
                result.matches.add(match);
            }

            result.matchCount = result.matches.size();
            result.isMatched = result.matchCount > 0;

        } catch (PatternSyntaxException e) {
            result.patternValid = false;
            result.error = "Invalid pattern: " + e.getDescription();
        }

        return result;
    }

    /**
     * Tests multiple strings against a pattern in a single call.
     *
     * @param pattern The regex pattern
     * @param testStrings List of strings to test
     * @return List of MatchResult for each test string
     */
    public List<MatchResult> testPatternMultiple(String pattern, List<String> testStrings) {
        List<MatchResult> results = new ArrayList<>();

        for (String testString : testStrings) {
            results.add(testPattern(pattern, testString));
        }

        return results;
    }

    /**
     * Splits a string using a regex pattern and returns the parts.
     *
     * @param pattern The regex pattern to split on
     * @param input The string to split
     * @return SplitResult with parts and statistics
     */
    public SplitResult splitByPattern(String pattern, String input) {
        SplitResult result = new SplitResult();

        try {
            var compiledPattern = java.util.regex.Pattern.compile(pattern);
            String[] parts = compiledPattern.split(input, -1); // -1 = keep trailing empty strings

            for (String part : parts) {
                result.parts.add(part);
            }

            result.partCount = result.parts.size();
            result.isValid = true;

        } catch (PatternSyntaxException e) {
            result.isValid = false;
            result.error = "Invalid pattern: " + e.getDescription();
        }

        return result;
    }

    // ── Helper Classes ───────────────────────────────────────────────────────

    /**
     * Definition of a common regex pattern.
     */
    public static class PatternDefinition {
        public String name;
        public String pattern;
        public String example;
        public String description;

        public PatternDefinition(String name, String pattern, String example, String description) {
            this.name = name;
            this.pattern = pattern;
            this.example = example;
            this.description = description;
        }
    }

    /**
     * Result of pattern validation.
     */
    public static class ValidationResult {
        public boolean isValid;
        public String error;

        public ValidationResult(boolean isValid, String error) {
            this.isValid = isValid;
            this.error = error;
        }
    }

    /**
     * A single match found in a test string.
     */
    public static class Match {
        public String text;
        public int startIndex;
        public int endIndex;
    }

    /**
     * Result of testing a pattern against a string.
     */
    public static class MatchResult {
        public boolean patternValid = false;
        public boolean isMatched = false;
        public int matchCount = 0;
        public List<Match> matches = new ArrayList<>();
        public String error = null;
    }

    /**
     * Result of splitting a string with a pattern.
     */
    public static class SplitResult {
        public boolean isValid = false;
        public int partCount = 0;
        public List<String> parts = new ArrayList<>();
        public String error = null;
    }
}
