package calculator.utilities;

/**
 * IMPROVEMENT: Removed three dead-code methods — reciprocalFunctions(),
 *   degreeFunctions(), and specialFunctions(). Each defined local Function
 *   objects that went out of scope immediately without being registered or
 *   returned, making them completely non-functional. The actual working
 *   function classes (cosec.java, sec.java, cot.java, sind.java, etc.) in
 *   calculator.functions are the correct implementations and are registered
 *   by AppInitializer at startup.
 *
 * IMPROVEMENT: Removed evaluateExpression() — it used a local ExpressionBuilder
 *   that would miss any function not explicitly listed there, and it contained
 *   a nonsensical recursive check:
 *     if (resultStr.matches(".*[a-zA-Z]+\\(.*\\).*")) { return evaluateExpression(resultStr); }
 *   A numeric result from exp4j can never match that pattern. Dead path removed.
 *
 * KEPT: getUnaryResult() is a useful standalone lookup method used by callers
 *   that want to evaluate a single named trig function on a value without
 *   building a full expression string. Improved to use a proper custom
 *   exception instead of a bare throw new Exception().
 *
 * IMPROVEMENT: Added degree-mode overloads (getUnaryResultDegrees) and
 *   hyperbolic functions so callers do not need to convert units themselves.
 */
public class TrigonometricUtils {

    private TrigonometricUtils() { }

    /**
     * Evaluates a single named trigonometric or inverse-trig function on a
     * value given in RADIANS.
     *
     * @param category  "trig"    → sin, cos, tan, cosec, sec, cot
     *                  "inverse" → asin, acos, atan
     *                  "hyp"     → sinh, cosh, tanh
     * @param fn        Function name (case-sensitive, as listed above).
     * @param a         Argument in radians.
     * @return Result, or Double.NaN if the function is undefined at the given input.
     * @throws IllegalArgumentException if category or fn is unrecognised.
     */
    public static double getUnaryResult(String category, String fn, double a) {
        switch (category.toLowerCase()) {
            case "trig":
                switch (fn) {
                    case "sin":   return Math.sin(a);
                    case "cos":   return Math.cos(a);
                    case "tan":   return Math.tan(a);
                    case "cosec": return safeReciprocal(Math.sin(a));
                    case "sec":   return safeReciprocal(Math.cos(a));
                    case "cot":   return safeReciprocal(Math.tan(a));
                    default: throw new IllegalArgumentException("Unknown trig function: " + fn);
                }
            case "inverse":
                switch (fn) {
                    case "asin": return Math.asin(a);
                    case "acos": return Math.acos(a);
                    case "atan": return Math.atan(a);
                    default: throw new IllegalArgumentException("Unknown inverse function: " + fn);
                }
            case "hyp":
                switch (fn) {
                    case "sinh": return Math.sinh(a);
                    case "cosh": return Math.cosh(a);
                    case "tanh": return Math.tanh(a);
                    default: throw new IllegalArgumentException("Unknown hyperbolic function: " + fn);
                }
            default:
                throw new IllegalArgumentException("Unknown category: " + category);
        }
    }

    /**
     * Same as getUnaryResult() but accepts the argument in DEGREES,
     * converting to radians internally before computing trig functions.
     * Inverse functions return results in DEGREES.
     */
    public static double getUnaryResultDegrees(String category, String fn, double aDegrees) {
        double aRad = Math.toRadians(aDegrees);
        switch (category.toLowerCase()) {
            case "trig":
                return getUnaryResult("trig", fn, aRad);
            case "inverse":
                return Math.toDegrees(getUnaryResult("inverse", fn, aRad));
            case "hyp":
                return getUnaryResult("hyp", fn, aRad);
            default:
                throw new IllegalArgumentException("Unknown category: " + category);
        }
    }

    /** Returns 1/value, substituting Double.NaN when value is zero. */
    private static double safeReciprocal(double value) {
        if (value == 0.0) return Double.NaN;
        return 1.0 / value;
    }
}