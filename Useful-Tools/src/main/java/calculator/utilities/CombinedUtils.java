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
 * IMPROVEMENT: Removed ~100 lines of commented-out code (the old manual
 *   constructor-per-function block that was replaced by ExpressionBuilderFactory).
 *   The factory approach is already the right design; the dead comments only added noise.
 *
 * FIX: Replaced the private splitTopLevelCommas() — which had the same
 *   accumulation bug as the original BooleanUtils — with a call to the shared
 *   ExpressionParserUtils.splitTopLevelCommas(). The bug caused every token
 *   after the first comma to contain all previous tokens' characters appended
 *   to it, producing nonsense argument values.
 */
public class CombinedUtils {

    public static double evaluateCombinedExpression(String expr) throws Exception {
        if (expr == null || expr.isEmpty()) {
            return 0.0;
        }

        Pattern p = Pattern.compile(
                "([a-zA-Z_][a-zA-Z0-9_]*)\\(((?:[^()]+|\\((?:[^()]+|\\([^()]*\\))*\\))+)\\)");
        Matcher m = p.matcher(expr);

        if (m.find()) {
            String funcName = m.group(1);
            String argsStr  = m.group(2);

            ArrayList<String> args = ExpressionParserUtils.splitTopLevelCommas(argsStr);
            double[] evaluatedArgs = new double[args.size()];
            for (int i = 0; i < args.size(); i++) {
                evaluatedArgs[i] = evaluateCombinedExpression(args.get(i));
            }
            return applyFunction(funcName, evaluatedArgs);
        }

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