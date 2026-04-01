package calculator.utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import calculator.expression.ExpressionBuilderFactory;
import calculator.functions.biconditional;
import calculator.functions.converseNonimplication;
import calculator.functions.implication;
import calculator.functions.nand;
import calculator.functions.nonimplication;
import calculator.functions.nor;
import calculator.functions.reverseImplication;
import calculator.functions.xnor;
import calculator.functions.xor;
import calculator.operators.and;
import calculator.operators.equality;
import calculator.operators.greaterThan;
import calculator.operators.greaterThanOrEqualTo;
import calculator.operators.leftShift;
import calculator.operators.lesserThan;
import calculator.operators.lesserThanOrEqualTo;
import calculator.operators.negation;
import calculator.operators.not;
import calculator.operators.or;
import calculator.operators.rightShift;
import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;

/**
 * FIX (Sprint 7) — evaluateCombinedExpression() now correctly handles
 * expressions with multiple sibling function calls such as
 * sin(0)+cos(0)&xor(1,0) or max(2,3)*min(4,5)+tan(0).
 *
 * The same find-replace loop fix applied to IntermediateUtils is applied here.
 * After all function calls are resolved by substitution, the remaining
 * expression (pure numeric + boolean operators) is evaluated by
 * buildCombinedExpression(), which has both arithmetic and boolean capabilities.
 */
public class CombinedUtils {

    public static double evaluateCombinedExpression(String expr) throws Exception {
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
                evaluatedArgs[i] = evaluateCombinedExpression(args.get(i));
            }
            double result = applyFunction(funcName, evaluatedArgs);

            if (Double.isNaN(result) || Double.isInfinite(result)) return result;

            String resultStr = result < 0
                    ? "(" + result + ")"
                    : String.valueOf(result);

            current = current.substring(0, m.start()) + resultStr + current.substring(m.end());
            m = p.matcher(current);
        }

        ExpressionBuilder exp = buildCombinedExpression(current);
        Expression e = exp.build();
        return e.evaluate();
    }

    /**
     * Applies a named function — FunctionRegistry first, then combined builder fallback.
     * Unchanged from before; extracted here for clarity.
     */
    private static double applyFunction(String funcName, double[] args) {
        for (Function f : FunctionRegistry.getFunctions()) {
            if (f.getName().equals(funcName)) {
                return f.apply(args);
            }
        }
        String reconstructed = funcName + "("
                + Arrays.stream(args).mapToObj(String::valueOf).collect(Collectors.joining(","))
                + ")";
        ExpressionBuilder exp = buildCombinedExpression(reconstructed);
        return exp.build().evaluate();
    }

    /**
     * Builds an ExpressionBuilder with full arithmetic + boolean capability.
     * Unchanged from before.
     */
    private static ExpressionBuilder buildCombinedExpression(String expr) {
        ExpressionBuilder builder = new ExpressionBuilder(expr);

        FunctionRegistry.getFunctions().forEach(builder::function);

        builder
            .function(new xor())
            .function(new xnor())
            .function(new nand())
            .function(new nor())
            .function(new implication())
            .function(new reverseImplication())
            .function(new biconditional())
            .function(new nonimplication())
            .function(new converseNonimplication());

        builder
            .operator(new and())
            .operator(new or())
            .operator(new not())
            .operator(new negation())
            .operator(new equality())
            .operator(new greaterThan())
            .operator(new lesserThan())
            .operator(new greaterThanOrEqualTo())
            .operator(new lesserThanOrEqualTo())
            .operator(new leftShift())
            .operator(new rightShift());

        return builder;
    }
}