package calculator.utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import calculator.functions.majority;
import calculator.functions.parity;
import calculator.operators.and;
import calculator.operators.biconditional;
import calculator.operators.converseNonimplication;
import calculator.operators.equality;
import calculator.operators.greaterThan;
import calculator.operators.greaterThanOrEqualTo;
import calculator.operators.implication;
import calculator.operators.leftShift;
import calculator.operators.lesserThan;
import calculator.operators.lesserThanOrEqualTo;
import calculator.operators.nand;
import calculator.operators.negation;
import calculator.operators.nonimplication;
import calculator.operators.nor;
import calculator.operators.not;
import calculator.operators.or;
import calculator.operators.reverseImplication;
import calculator.operators.rightShift;
import calculator.operators.xnor;
import calculator.operators.xor;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

public class BooleanUtils {

    public static double evaluateBooleanExpression(String expr) throws Exception {
        if (expr == null || expr.isEmpty()) {
            return 0.0;
        }

        Pattern p = Pattern.compile(
                "(?i)\\b(majority|parity)\\b\\s*\\(((?:[^()]++|\\((?:[^()]++|\\([^()]*\\))*\\))*)\\)");
        Matcher m = p.matcher(expr);

        if (m.find()) {
            String funcName = m.group(1);
            String argsStr  = m.group(2);

            ArrayList<String> args = splitTopLevelCommas(argsStr);
            double[] evaluatedArgs = new double[args.size()];

            for (int i = 0; i < args.size(); i++) {
                evaluatedArgs[i] = evaluateBooleanExpression(args.get(i));
            }

            return applyFunction(funcName, evaluatedArgs);
        }

        ExpressionBuilder exp = buildBooleanExpression(expr);
        Expression e = exp.build();
        return e.evaluate();
    }

    private static double applyFunction(String funcName, double[] args) {
        String reconstructed = funcName + "("
                + Arrays.stream(args).mapToObj(String::valueOf).collect(Collectors.joining(","))
                + ")";
        return buildBooleanExpression(reconstructed).build().evaluate();
    }

    /**
     * FIX: The original implementation had two bugs:
     *
     * Bug 1 — args.add() was called INSIDE the main loop body after every
     *   non-comma character, appending a partial in-progress token on every
     *   iteration. Parsing "1,0,1" produced ["1","1,","1,0","0","0,","0,1","1"]
     *   instead of the correct ["1","0","1"].
     *
     * Bug 2 — current.setLength(0) was never called after flushing a token on
     *   a top-level comma, so every subsequent token accumulated all the
     *   characters from all previous tokens.
     *
     * Fix: args.add() is called in exactly two places:
     *   1. When a top-level comma is encountered (flushes the current token).
     *   2. After the loop ends (flushes the final token).
     *   current.setLength(0) resets the buffer immediately after each flush.
     */
    private static ArrayList<String> splitTopLevelCommas(String argsStr) {
        ArrayList<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;

        for (int i = 0; i < argsStr.length(); i++) {
            char ch = argsStr.charAt(i);

            if (ch == ',' && depth == 0) {
                // Top-level comma — flush the completed token.
                args.add(current.toString().trim());
                current.setLength(0);    // reset buffer for the next token
            } else {
                if (ch == '(')      depth++;
                else if (ch == ')') depth--;
                current.append(ch);
            }
        }

        // Flush the last token (no trailing comma to trigger the flush above).
        if (current.length() > 0) {
            args.add(current.toString().trim());
        }

        return args;
    }

    /**
     * Shared helper that constructs an ExpressionBuilder pre-loaded with all
     * boolean operators and the majority/parity functions.
     * Extracted to avoid duplicating the long operator list between
     * evaluateBooleanExpression() and applyFunction().
     */
    private static ExpressionBuilder buildBooleanExpression(String expr) {
        return new ExpressionBuilder(expr)
                .function(new majority())
                .function(new parity())
                .operator(new and())
                .operator(new biconditional())
                .operator(new converseNonimplication())
                .operator(new equality())
                .operator(new negation())
                .operator(new greaterThan())
                .operator(new lesserThan())
                .operator(new greaterThanOrEqualTo())
                .operator(new lesserThanOrEqualTo())
                .operator(new implication())
                .operator(new leftShift())
                .operator(new nand())
                .operator(new nonimplication())
                .operator(new nor())
                .operator(new not())
                .operator(new or())
                .operator(new reverseImplication())
                .operator(new rightShift())
                .operator(new xnor())
                .operator(new xor());
    }
}