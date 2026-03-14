package calculator.utilities;

import java.util.ArrayList;

/**
 * NEW FILE — ExpressionParserUtils
 *
 * WHY THIS IS NEEDED:
 * The method splitTopLevelCommas() existed in three separate classes:
 *   - BooleanUtils
 *   - IntermediateUtils
 *   - CombinedUtils
 *
 * Each copy had subtle bugs (add() called inside the character loop, or
 * missing current.setLength(0) after a comma). Centralising the logic here
 * means it can be fixed and tested once, and all three Utils classes simply
 * call this shared version.
 *
 * USAGE:
 *   ArrayList<String> args = ExpressionParserUtils.splitTopLevelCommas("1,max(2,3),4");
 *   // → ["1", "max(2,3)", "4"]
 */
public class ExpressionParserUtils {

    private ExpressionParserUtils() {
        // Utility class — not instantiable.
    }

    /**
     * Splits a comma-separated argument string at top-level commas only,
     * correctly ignoring commas that appear inside nested parentheses.
     *
     * Examples:
     *   "1,2,3"            → ["1", "2", "3"]
     *   "1,max(2,3),4"     → ["1", "max(2,3)", "4"]
     *   "f(a,b),g(c,d)"    → ["f(a,b)", "g(c,d)"]
     *   ""                  → []
     *
     * @param argsStr The raw argument string (must not include the outer parentheses).
     * @return A list of trimmed argument strings.
     */
    public static ArrayList<String> splitTopLevelCommas(String argsStr) {
        ArrayList<String> args = new ArrayList<>();
        if (argsStr == null || argsStr.isBlank()) {
            return args;
        }

        StringBuilder current = new StringBuilder();
        int depth = 0;

        for (int i = 0; i < argsStr.length(); i++) {
            char ch = argsStr.charAt(i);

            if (ch == ',' && depth == 0) {
                // Top-level comma — flush the current token.
                args.add(current.toString().trim());
                current.setLength(0);             // ← reset for the next token
            } else {
                if (ch == '(')      depth++;
                else if (ch == ')') depth--;
                current.append(ch);
            }
        }

        // Flush the last token (no trailing comma to trigger it above).
        if (current.length() > 0) {
            args.add(current.toString().trim());
        }

        return args;
    }
}