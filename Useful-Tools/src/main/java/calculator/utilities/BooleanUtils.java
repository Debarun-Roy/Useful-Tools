package calculator.utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import calculator.functions.biconditional;
import calculator.functions.converseNonimplication;
import calculator.functions.implication;
import calculator.functions.majority;
import calculator.functions.nand;
import calculator.functions.nonimplication;
import calculator.functions.nor;
import calculator.functions.parity;
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
 * FIX (Sprint 7) — evaluateBooleanExpression() now correctly handles
 * expressions with multiple sibling boolean function calls such as
 * majority(1,0,1)&nand(1,1) or xor(1,0)|nor(0,0).
 *
 * The same find-replace loop fix applied to IntermediateUtils is applied here.
 * After evaluating a function call, the result (0.0 or 1.0 for boolean functions,
 * or any numeric value for arithmetic functions in combined contexts) is
 * substituted back into the string and the scan restarts. After all function
 * calls are resolved, buildBooleanExpression() evaluates the remaining
 * operator-only expression (e.g. "1.0&0.0", "1.0|0.0&1.0").
 */
public class BooleanUtils {

    /**
     * Validates a boolean expression by attempting evaluation.
     * See existing comments — logic unchanged.
     */
    public static boolean validateExpression(String expr) {
        if (expr == null || expr.isBlank()) return false;
        try {
            evaluateBooleanExpression(expr);
            return true;
        } catch (Throwable t) {
            System.err.println("[BooleanUtils.validateExpression] rejected '" + expr
                    + "' — " + t.getClass().getName() + ": " + t.getMessage());
            return false;
        }
    }

    public static double evaluateBooleanExpression(String expr) throws Exception {
        if (expr == null || expr.isEmpty()) return 0.0;

        Pattern p = Pattern.compile(
                "(?i)\\b([a-zA-Z_]\\w*)\\b\\s*\\(((?:[^()]++|\\((?:[^()]++|\\([^()]*\\))*\\))*)\\)");

        String current = expr.trim();
        Matcher m = p.matcher(current);

        while (m.find()) {
            String funcName = m.group(1);
            String argsStr  = m.group(2);

            ArrayList<String> args = splitTopLevelCommas(argsStr);
            double[] evaluatedArgs = new double[args.size()];
            for (int i = 0; i < args.size(); i++) {
                evaluatedArgs[i] = evaluateBooleanExpression(args.get(i));
            }
            double result = applyFunction(funcName, evaluatedArgs);

            if (Double.isNaN(result) || Double.isInfinite(result)) return result;

            // Boolean results are 0.0 or 1.0 — never negative — but guard anyway.
            String resultStr = result < 0
                    ? "(" + result + ")"
                    : String.valueOf(result);

            current = current.substring(0, m.start()) + resultStr + current.substring(m.end());
            m = p.matcher(current);
        }

        ExpressionBuilder exp = buildBooleanExpression(current);
        Expression e = exp.build();
        return e.evaluate();
    }

    private static double applyFunction(String funcName, double[] args) {
        if (funcName.equalsIgnoreCase("parity")) {
            return new parity().apply(args);
        }
        if (funcName.equalsIgnoreCase("majority")) {
            return new majority().apply(args);
        }
        for (Function f : FunctionRegistry.getFunctions()) {
            if (f.getName().equals(funcName)) {
                return f.apply(args);
            }
        }
        String reconstructed = funcName + "("
                + Arrays.stream(args).mapToObj(String::valueOf).collect(Collectors.joining(","))
                + ")";
        return buildBooleanExpression(reconstructed).build().evaluate();
    }

    private static ArrayList<String> splitTopLevelCommas(String argsStr) {
        ArrayList<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < argsStr.length(); i++) {
            char ch = argsStr.charAt(i);
            if (ch == ',' && depth == 0) {
                args.add(current.toString().trim());
                current.setLength(0);
            } else {
                if      (ch == '(') depth++;
                else if (ch == ')') depth--;
                current.append(ch);
            }
        }
        if (current.length() > 0) args.add(current.toString().trim());
        return args;
    }

    static ExpressionBuilder buildBooleanExpression(String expr) {
        return new ExpressionBuilder(expr)
                .function(new majority())
                .function(new parity())
                .function(new biconditional())
                .function(new converseNonimplication())
                .function(new implication())
                .function(new nonimplication())
                .function(new nand())
                .function(new nor())
                .function(new reverseImplication())
                .function(new xnor())
                .function(new xor())
                .operator(new and())
                .operator(new equality())
                .operator(new negation())
                .operator(new greaterThan())
                .operator(new lesserThan())
                .operator(new greaterThanOrEqualTo())
                .operator(new lesserThanOrEqualTo())
                .operator(new leftShift())
                .operator(new not())
                .operator(new or())
                .operator(new rightShift());
    }
}