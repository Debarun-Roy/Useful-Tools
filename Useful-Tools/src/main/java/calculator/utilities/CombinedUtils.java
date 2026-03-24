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
 * FIX — applyFunction() now looks up every function in FunctionRegistry first
 * and calls f.apply(args) directly if found.
 * Identical fix to IntermediateUtils — see that class for the full explanation.
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
        // Step 1: Look for the function in our registry — call directly.
        for (Function f : FunctionRegistry.getFunctions()) {
            if (f.getName().equals(funcName)) {
                return f.apply(args);
            }
        }

        // Step 2: exp4j built-in — reconstruct and evaluate.
        String reconstructed = funcName + "("
                + Arrays.stream(args).mapToObj(String::valueOf).collect(Collectors.joining(","))
                + ")";
        ExpressionBuilder exp = ExpressionBuilderFactory.create(reconstructed);
        return exp.build().evaluate();
    }
}