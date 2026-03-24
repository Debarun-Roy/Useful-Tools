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
 *
 * WHY THE PREVIOUS FIX FAILED:
 * The previous attempt used a VARIADIC_SENTINEL constant (100) to identify
 * variadic functions. This was fragile — if the declared argument count in
 * any function class was changed (e.g. max changed from 100 to 50), the
 * sentinel check silently stopped matching, and the function fell through to
 * the exp4j reconstruction path which demanded the exact declared count.
 *
 * THE CORRECT APPROACH:
 * For any function found in FunctionRegistry, call f.apply(args) directly.
 * This completely bypasses exp4j's argument-count enforcement. It works for
 * both variadic functions (max, min, mean, median, parity) and fixed-arity
 * custom functions (round, trunc, fact, nCr, logn, etc.) — for all of them,
 * the Java apply() method receives exactly the arguments it was given.
 *
 * Only functions NOT in FunctionRegistry (exp4j built-ins: sin, cos, sqrt,
 * log, floor, ceil, abs, etc.) use the exp4j reconstruction path. Those
 * functions are never registered in our FunctionRegistry, so the lookup
 * correctly falls through to exp4j for them.
 *
 * This approach is robust regardless of what declared argument count is used
 * in any Function subclass constructor.
 */
public class IntermediateUtils {

    public static double evaluateArithmeticExpression(String expr) throws Exception {
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
                evaluatedArgs[i] = evaluateArithmeticExpression(args.get(i));
            }
            return applyFunction(funcName, evaluatedArgs);
        }

        ExpressionBuilder exp = ExpressionBuilderFactory.create(expr);
        Expression e = exp.build();
        return e.evaluate();
    }

    /**
     * Applies a named function to an already-evaluated argument array.
     *
     * Strategy:
     *   1. Search FunctionRegistry for a function with this name.
     *      If found → call f.apply(args) directly. No exp4j argument-count check.
     *   2. Not found → this is an exp4j built-in (sin, cos, sqrt, log, etc.).
     *      Reconstruct the expression string and evaluate through exp4j.
     */
    private static double applyFunction(String funcName, double[] args) {
        // Step 1: Look for the function in our registry.
        for (Function f : FunctionRegistry.getFunctions()) {
            if (f.getName().equals(funcName)) {
                // Call apply() directly — bypasses exp4j's argument-count check.
                return f.apply(args);
            }
        }

        // Step 2: Not in our registry — must be an exp4j built-in.
        String reconstructed = funcName + "("
                + Arrays.stream(args).mapToObj(String::valueOf).collect(Collectors.joining(","))
                + ")";
        ExpressionBuilder exp = ExpressionBuilderFactory.create(reconstructed);
        return exp.build().evaluate();
    }
}