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

        patterns.put("date-time", List.of(
                new PatternDefinition("ISO date", "^\\d{4}-\\d{2}-\\d{2}$", "2026-04-30", "YYYY-MM-DD date"),
                new PatternDefinition("US date", "^\\d{2}/\\d{2}/\\d{4}$", "04/30/2026", "MM/DD/YYYY date"),
                new PatternDefinition("Time 24-hour", "^\\d{2}:\\d{2}(:\\d{2})?$", "18:45:30", "HH:MM or HH:MM:SS"),
                new PatternDefinition("ISO timestamp", "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(Z|[+-]\\d{2}:\\d{2})$", "2026-04-30T18:45:30Z", "ISO-8601 timestamp"),
                new PatternDefinition("Month/year", "^(0[1-9]|1[0-2])/(19|20)\\d{2}$", "04/2026", "MM/YYYY month reference"),
                new PatternDefinition("Year", "^(19|20)\\d{2}$", "2026", "Year from 1900 to 2099"),
                new PatternDefinition("Quarter", "^Q[1-4]-(19|20)\\d{2}$", "Q2-2026", "Business quarter label")
        ));

        patterns.put("web", List.of(
                new PatternDefinition("Domain", "^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", "example.com", "Domain name"),
                new PatternDefinition("URL path", "^/[A-Za-z0-9/_-]*$", "/docs/api/v1", "URL path segments"),
                new PatternDefinition("Query pair", "^[A-Za-z0-9_-]+=[^&=]*$", "page=2", "Single query-string key/value pair"),
                new PatternDefinition("HTML id selector", "^#[A-Za-z][A-Za-z0-9_-]*$", "#mainContent", "CSS ID selector"),
                new PatternDefinition("CSS class selector", "^\\.[A-Za-z][A-Za-z0-9_-]*$", ".buttonPrimary", "CSS class selector"),
                new PatternDefinition("CSS custom property", "^--[A-Za-z0-9_-]+$", "--brand-color", "CSS variable name"),
                new PatternDefinition("Social handle", "^@[A-Za-z0-9_]{1,15}$", "@usefultools", "At-prefixed username"),
                new PatternDefinition("Hex color with alpha", "^#[A-Fa-f0-9]{3,8}$", "#3366ffaa", "CSS hex color including optional alpha")
        ));

        patterns.put("identifiers", List.of(
                new PatternDefinition("camelCase", "^[a-z][A-Za-z0-9]*$", "userProfileId", "camelCase identifier"),
                new PatternDefinition("PascalCase", "^[A-Z][A-Za-z0-9]*$", "UserProfileId", "PascalCase identifier"),
                new PatternDefinition("snake_case", "^[a-z][a-z0-9]*(_[a-z0-9]+)*$", "user_profile_id", "snake_case identifier"),
                new PatternDefinition("kebab-case", "^[a-z0-9]+(-[a-z0-9]+)*$", "user-profile-id", "kebab-case identifier"),
                new PatternDefinition("CONSTANT_CASE", "^[A-Z][A-Z0-9_]*$", "MAX_RETRY_COUNT", "Uppercase constant identifier"),
                new PatternDefinition("JavaScript identifier", "^[A-Za-z_$][A-Za-z0-9_$]*$", "$result_1", "JavaScript variable/function identifier"),
                new PatternDefinition("Java package", "^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+$", "com.usefultools.regex", "Java package name")
        ));

        patterns.put("security", List.of(
                new PatternDefinition("MD5 hash", "^[A-Fa-f0-9]{32}$", "d41d8cd98f00b204e9800998ecf8427e", "32-character hexadecimal hash"),
                new PatternDefinition("SHA-1 hash", "^[A-Fa-f0-9]{40}$", "da39a3ee5e6b4b0d3255bfef95601890afd80709", "40-character hexadecimal hash"),
                new PatternDefinition("SHA-256 hash", "^[A-Fa-f0-9]{64}$", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "64-character hexadecimal hash"),
                new PatternDefinition("BCrypt hash", "^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$", "$2a$12$abcdefghijklmnopqrstuuJk1qGxS5lKZ8Y1cTq6s5M1jK4", "BCrypt password hash"),
                new PatternDefinition("JWT token", "^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$", "header.payload.signature", "JSON Web Token structure"),
                new PatternDefinition("Secret key", "^sk-[A-Za-z0-9]{20,}$", "sk-AbCdEf1234567890abcd", "Generic secret key prefix"),
                new PatternDefinition("Base64 token", "^[A-Za-z0-9+/]{40,}={0,2}$", "QWxhZGRpbjpvcGVuIHNlc2FtZQAAAAAAAAAAAAAAAA==", "Long Base64-like token")
        ));

        patterns.put("file", List.of(
                new PatternDefinition("Safe filename", "^[^\\\\/:*?\\\"<>|]+$", "report-final.pdf", "Filename without reserved Windows characters"),
                new PatternDefinition("File with extension", "^.+\\.[A-Za-z0-9]{1,10}$", "archive.tar.gz", "Filename ending in an extension"),
                new PatternDefinition("Windows path", "^[A-Za-z]:\\\\(?:[^\\\\/:*?\\\"<>|]+\\\\)*[^\\\\/:*?\\\"<>|]*$", "C:\\\\Users\\\\royta\\\\file.txt", "Absolute Windows path"),
                new PatternDefinition("Unix path", "^/(?:[^/]+/)*[^/]*$", "/usr/local/bin", "Absolute Unix-style path"),
                new PatternDefinition("Relative path", "^\\.{1,2}(/[^/]+)*$", "../assets/logo.png", "Relative path beginning with . or .."),
                new PatternDefinition("Data URL", "^data:[\\w/+.-]+;base64,[A-Za-z0-9+/=]+$", "data:image/png;base64,iVBORw0KGgo=", "Base64 data URL")
        ));

        patterns.put("network", List.of(
                new PatternDefinition("IPv6 address", "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$", "2001:0db8:85a3:0000:0000:8a2e:0370:7334", "Full IPv6 address"),
                new PatternDefinition("MAC address", "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$", "00:1A:2B:3C:4D:5E", "Colon-separated MAC address"),
                new PatternDefinition("IPv4 CIDR", "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)/(3[0-2]|[12]?[0-9])$", "192.168.1.0/24", "IPv4 network with CIDR mask"),
                new PatternDefinition("Port", "^([0-9]{1,4}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])$", "8080", "TCP/UDP port from 0 to 65535"),
                new PatternDefinition("Hostname", "^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z]{2,})+$", "api.example.com", "DNS hostname")
        ));

        patterns.put("finance", List.of(
                new PatternDefinition("Currency amount", "^-?\\$?\\d{1,3}(,\\d{3})*(\\.\\d{2})?$", "$1,250.00", "Currency-style amount"),
                new PatternDefinition("Percentage", "^-?\\d+(\\.\\d+)?%$", "12.5%", "Percentage value"),
                new PatternDefinition("IBAN", "^[A-Z]{2}\\d{2}[A-Z0-9]{11,30}$", "GB82WEST12345698765432", "Simplified IBAN format"),
                new PatternDefinition("Credit card", "^(?:\\d[ -]*?){13,19}$", "4111 1111 1111 1111", "Payment card number shape")
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

    /**
     * Builds a concise, rule-based explanation for a regex pattern.
     */
    public ExplanationResult explainPattern(String pattern) {
        ExplanationResult result = new ExplanationResult();

        ValidationResult validation = validatePattern(pattern);
        result.isValid = validation.isValid;
        if (!validation.isValid) {
            result.error = validation.error;
            return result;
        }

        if (pattern == null || pattern.isBlank()) {
            result.summary = "Empty pattern.";
            return result;
        }

        int i = 0;
        while (i < pattern.length()) {
            char ch = pattern.charAt(i);

            if (ch == '\\' && i + 1 < pattern.length()) {
                String token = pattern.substring(i, i + 2);
                result.parts.add(new ExplanationPart(token, explainEscape(pattern.charAt(i + 1))));
                i += 2;
                continue;
            }

            if (ch == '[') {
                int end = findClosing(pattern, i, ']');
                if (end > i) {
                    String token = pattern.substring(i, end + 1);
                    String text = token.startsWith("[^")
                            ? "Match one character not in this set."
                            : "Match one character from this set.";
                    result.parts.add(new ExplanationPart(token, text));
                    i = end + 1;
                    continue;
                }
            }

            if (ch == '(') {
                String token = "(";
                String text = "Start a capturing group.";
                if (pattern.startsWith("(?:", i)) {
                    token = "(?:";
                    text = "Start a non-capturing group.";
                    i += 3;
                } else if (pattern.startsWith("(?=", i)) {
                    token = "(?=";
                    text = "Start a positive lookahead.";
                    i += 3;
                } else if (pattern.startsWith("(?!", i)) {
                    token = "(?!";
                    text = "Start a negative lookahead.";
                    i += 3;
                } else if (pattern.startsWith("(?<=", i)) {
                    token = "(?<=";
                    text = "Start a positive lookbehind.";
                    i += 4;
                } else if (pattern.startsWith("(?<!", i)) {
                    token = "(?<!";
                    text = "Start a negative lookbehind.";
                    i += 4;
                } else if (isInlineFlagGroup(pattern, i)) {
                    int close = pattern.indexOf(')', i + 2);
                    if (close > i) {
                        token = pattern.substring(i, close + 1);
                        text = "Set inline regex flags.";
                        i = close + 1;
                    } else {
                        i++;
                    }
                } else if (pattern.startsWith("(?<", i)) {
                    int close = pattern.indexOf('>', i + 3);
                    if (close > i) {
                        token = pattern.substring(i, close + 1);
                        text = "Start a named capturing group.";
                        i = close + 1;
                    } else {
                        i++;
                    }
                } else {
                    i++;
                }
                result.parts.add(new ExplanationPart(token, text));
                continue;
            }

            String token = String.valueOf(ch);
            result.parts.add(new ExplanationPart(token, explainSingleChar(ch)));
            i++;
        }

        result.summary = "Valid regex with " + result.parts.size() + " explained part"
                + (result.parts.size() == 1 ? "." : "s.");
        return result;
    }

    private int findClosing(String pattern, int start, char close) {
        for (int i = start + 1; i < pattern.length(); i++) {
            if (pattern.charAt(i) == close && !isEscaped(pattern, i)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isInlineFlagGroup(String pattern, int start) {
        if (!pattern.startsWith("(?", start)) return false;
        int close = pattern.indexOf(')', start + 2);
        if (close <= start + 2) return false;
        String flags = pattern.substring(start + 2, close);
        return flags.matches("[idmsuxU-]+");
    }

    private boolean isEscaped(String pattern, int index) {
        int slashCount = 0;
        for (int i = index - 1; i >= 0 && pattern.charAt(i) == '\\'; i--) {
            slashCount++;
        }
        return slashCount % 2 == 1;
    }

    private String explainEscape(char ch) {
        return switch (ch) {
            case 'd' -> "Match any digit.";
            case 'D' -> "Match any non-digit.";
            case 'w' -> "Match a word character.";
            case 'W' -> "Match a non-word character.";
            case 's' -> "Match whitespace.";
            case 'S' -> "Match non-whitespace.";
            case 'b' -> "Match a word boundary.";
            case 'B' -> "Match a non-word boundary.";
            case 'n' -> "Match a newline.";
            case 't' -> "Match a tab.";
            default -> "Match the escaped character '" + ch + "'.";
        };
    }

    private String explainSingleChar(char ch) {
        return switch (ch) {
            case '^' -> "Anchor the match to the start of input or line.";
            case '$' -> "Anchor the match to the end of input or line.";
            case '.' -> "Match any character except line breaks.";
            case '*' -> "Repeat the previous token zero or more times.";
            case '+' -> "Repeat the previous token one or more times.";
            case '?' -> "Make the previous token optional or lazy.";
            case '{' -> "Start an explicit repeat range.";
            case '}' -> "End an explicit repeat range.";
            case '|' -> "Separate alternatives.";
            case ')' -> "End a group.";
            default -> "Match the literal character '" + ch + "'.";
        };
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

    /**
     * Token-level regex explanation result.
     */
    public static class ExplanationResult {
        public boolean isValid = false;
        public String summary = "";
        public List<ExplanationPart> parts = new ArrayList<>();
        public String error = null;
    }

    /**
     * A single explained token or token group.
     */
    public static class ExplanationPart {
        public String token;
        public String explanation;

        public ExplanationPart(String token, String explanation) {
            this.token = token;
            this.explanation = explanation;
        }
    }
}
