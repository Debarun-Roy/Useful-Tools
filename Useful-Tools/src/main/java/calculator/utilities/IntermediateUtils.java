package calculator.utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import calculator.expression.ExpressionBuilderFactory;
import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;

/**
 * FIX (Sprint 7) — evaluateArithmeticExpression() now correctly handles
 * expressions containing multiple sibling function calls such as
 * sin(0)+cos(0) or max(2,3)*min(4,5)+round(3.7,0).
 *
 * ROOT CAUSE OF THE BUG:
 *   The previous implementation used "if (m.find())" — it found the FIRST
 *   regex match, evaluated it, and immediately returned the result. The rest
 *   of the expression was silently discarded. sin(0)+cos(0) returned 0.0
 *   (the result of sin alone) instead of the correct 1.0.
 *
 * THE FIX — find-replace loop:
 *   After evaluating any function call, substitute its numeric result back
 *   into the expression string at the matched position, then re-scan the
 *   modified string from the beginning. Repeat until no function calls
 *   remain. At that point the expression is a pure numeric/operator string
 *   that exp4j can evaluate directly.
 *
 *   Nested calls (e.g. sin(cos(0))) are handled correctly: the regex
 *   matches the outermost call whose arguments are recursively evaluated
 *   before substitution. Deeply nested calls that the regex cannot capture
 *   in one shot are resolved in successive loop iterations.
 *
 * NEGATIVE RESULT WRAPPING:
 *   Negative results are wrapped in parentheses before substitution to
 *   prevent operator-parsing ambiguity. "3+-0.5" is ambiguous; "3+(-0.5)"
 *   is not. exp4j handles parenthesised sub-expressions cleanly.
 *
 * NaN / Infinity SHORT-CIRCUIT:
 *   If any function call returns NaN or Infinity (e.g. fact(-1), sqrt(-1),
 *   1/0), the result is returned immediately. NaN is infectious under
 *   IEEE 754 arithmetic and cannot be represented as an exp4j token.
 */
public class IntermediateUtils {

    public static double evaluateArithmeticExpression(String expr) throws Exception {
        if (expr == null || expr.isEmpty()) return 0.0;

        Pattern p = Pattern.compile(
                "([a-zA-Z_][a-zA-Z0-9_]*)\\(((?:[^()]+|\\((?:[^()]+|\\([^()]*\\))*\\))+)\\)");

        String current = expr.trim();
        Matcher m = p.matcher(current);

        while (m.find()) {
            String funcName = m.group(1);
            String argsStr  = m.group(2);

            ArrayList<String> args = ExpressionParserUtils.splitTopLevelCommas(argsStr);
            double[] evaluatedArgs = new double[args.size()];
            for (int i = 0; i < args.size(); i++) {
                evaluatedArgs[i] = evaluateArithmeticExpression(args.get(i));
            }
            double result = applyFunction(funcName, evaluatedArgs);

            // Short-circuit: NaN and Infinity cannot be exp4j tokens.
            // NaN is infectious (NaN + anything = NaN) so returning early is correct.
            if (Double.isNaN(result) || Double.isInfinite(result)) return result;

            // Wrap negative results in parentheses to prevent operator ambiguity.
            // e.g. "3+-0.5" causes parse errors; "3+(-0.5)" does not.
            String resultStr = result < 0
                    ? "(" + result + ")"
                    : String.valueOf(result);

            current = current.substring(0, m.start()) + resultStr + current.substring(m.end());
            m = p.matcher(current); // re-scan the modified string from the beginning
        }

        // No function calls remain — evaluate the pure numeric expression.
        ExpressionBuilder exp = ExpressionBuilderFactory.create(current);
        Expression e = exp.build();
        return e.evaluate();
    }

    /**
     * Applies a named function to an already-evaluated argument array.
     *
     * Priority:
     *   1. FunctionRegistry — all custom functions (max, min, fact, round, sind, etc.)
     *      called via f.apply(args) directly, bypassing exp4j argument-count checks.
     *   2. exp4j built-ins — sin, cos, sqrt, log, floor, ceil, abs, etc.
     *      Reconstructed as an expression string and evaluated through exp4j.
     */
    private static double applyFunction(String funcName, double[] args) {
        for (Function f : FunctionRegistry.getFunctions()) {
            if (f.getName().equals(funcName)) {
                return f.apply(args);
            }
        }

        // exp4j built-in — reconstruct and evaluate.
        String reconstructed = funcName + "("
                + Arrays.stream(args).mapToObj(String::valueOf).collect(Collectors.joining(","))
                + ")";
        ExpressionBuilder exp = ExpressionBuilderFactory.create(reconstructed);
        return exp.build().evaluate();
    }
}