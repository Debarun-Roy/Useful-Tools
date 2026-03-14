package calculator.utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import calculator.expression.ExpressionBuilderFactory;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

/**
 * IMPROVEMENT: Now uses ExpressionBuilderFactory (which draws from the
 *   FunctionRegistry and OperatorRegistry populated by AppInitializer) instead
 *   of manually instantiating every function and operator class in two separate
 *   methods. This eliminates ~50 lines of repeated constructor calls and means
 *   new functions/operators are automatically available here once registered.
 *
 * FIX: The private splitTopLevelCommas() was replaced by a call to the shared
 *   ExpressionParserUtils.splitTopLevelCommas(), which is correct and tested
 *   in one place. The original local copy was missing current.setLength(0)
 *   after flushing a token on a comma, causing every subsequent token to
 *   accumulate all previous tokens' content.
 *
 *   Example of the old bug:
 *     Input:  "1,2,3"
 *     Old:    ["1", "12", "123"]   ← each token grew from the last
 *     Fixed:  ["1", "2", "3"]
 */
public class IntermediateUtils {

    public static double evaluateArithmeticExpression(String expr) throws Exception {
        if (expr == null || expr.isEmpty()) {
            return 0.0;
        }

        // Match the outermost function call so nested args can be recursively evaluated.
        Pattern p = Pattern.compile(
                "([a-zA-Z_][a-zA-Z0-9_]*)\\(((?:[^()]+|\\((?:[^()]+|\\([^()]*\\))*\\))+)\\)");
        Matcher m = p.matcher(expr);

        if (m.find()) {
            String funcName = m.group(1);
            String argsStr  = m.group(2);

            ArrayList<String> args = ExpressionParserUtils.splitTopLevelCommas(argsStr);
            double[] evaluatedArgs = new double[args.size()];
            for (int i = 0; i < args.size(); i++) {
                evaluatedArgs[i] = evaluateArithmeticExpression(args.get(i));
            }
            return applyFunction(funcName, evaluatedArgs);
        }

        // No function call found — evaluate as a plain expression.
        ExpressionBuilder exp = ExpressionBuilderFactory.create(expr);
        Expression e = exp.build();
        return e.evaluate();
    }

    private static double applyFunction(String funcName, double[] args) {
        String reconstructed = funcName + "("
                + Arrays.stream(args).mapToObj(String::valueOf).collect(Collectors.joining(","))
                + ")";
        ExpressionBuilder exp = ExpressionBuilderFactory.create(reconstructed);
        return exp.build().evaluate();
    }
}