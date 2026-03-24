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

public class BooleanUtils {

    /**
     * Returns true if the expression is valid for the boolean calculator.
     *
     * PREVIOUS APPROACH — WRONG:
     *   buildBooleanExpression(expr).build()
     *   This uses exp4j's build() which may throw for certain operator symbols
     *   depending on the exp4j version. The exact exception type (and whether it
     *   is an Exception or Error subclass) varies. The result was that expressions
     *   like 1|1 returned valid:false even though evaluation worked correctly.
     *
     * CORRECT APPROACH:
     *   Use evaluateBooleanExpression(expr) directly as the validation mechanism.
     *   This is the IDENTICAL code path used for actual evaluation, so validation
     *   and evaluation are guaranteed to agree. If evaluation succeeds, the
     *   expression is valid. If it throws for any reason (syntax error, unknown
     *   symbol, etc.), the expression is invalid.
     *
     *   Catching Throwable (not just Exception) ensures that Error subclasses
     *   such as ExceptionInInitializerError — which are thrown when a class's
     *   static initialiser fails — are also caught and do not propagate.
     *
     *   The exception is printed to System.err so that if a NEW type of failure
     *   is encountered, the exact cause is visible in the Tomcat console. This
     *   makes future debugging straightforward.
     */
    public static boolean validateExpression(String expr) {
        if (expr == null || expr.isBlank()) return false;
        try {
            evaluateBooleanExpression(expr);
            return true;
        } catch (Throwable t) {
            // Print to console so the exact failure type is visible in Tomcat logs.
            // Remove this line once boolean validation is confirmed stable.
            System.err.println("[BooleanUtils.validateExpression] rejected '" + expr
                    + "' — " + t.getClass().getName() + ": " + t.getMessage());
            return false;
        }
    }

    public static double evaluateBooleanExpression(String expr) throws Exception {
        if (expr == null || expr.isEmpty()) {
            return 0.0;
        }

//        Pattern p = Pattern.compile(
//                "(?i)\\b(majority|parity)\\b\\s*\\(((?:[^()]++|\\((?:[^()]++|\\([^()]*\\))*\\))*)\\)");
        Pattern p = Pattern.compile(
        		"(?i)\\b([a-zA-Z_]\\w*)\\b\\s*\\(((?:[^()]++|\\((?:[^()]++|\\([^()]*\\))*\\))*)\\)"
        		);
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
        if (funcName.equalsIgnoreCase("parity")) {
            return new parity().apply(args);
        }
        if (funcName.equalsIgnoreCase("majority")) {
        	return new majority().apply(args);
        }
        //if our function is found in our function registry
        for (Function f : FunctionRegistry.getFunctions()) {
        	if(f.getName().equals(funcName)) {
        		// Call apply() directly — bypasses exp4j's argument-count check.
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
        if (current.length() > 0) {
            args.add(current.toString().trim());
        }
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