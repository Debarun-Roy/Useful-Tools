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
 * FIX — buildCombinedExpression() replaces the direct ExpressionBuilderFactory
 * call in the non-function-call path.
 *
 * WHY THE PREVIOUS VERSION FAILED FOR 9&8:
 * When the regex finds no function call in an expression (e.g. "9&8" starts
 * with a digit, not a function name), CombinedUtils fell through to:
 *   ExpressionBuilderFactory.create(expr).build().evaluate()
 * ExpressionBuilderFactory only loads functions from FunctionRegistry and
 * operators from OperatorRegistry. OperatorRegistry was deliberately emptied
 * to prevent unicode operators from crashing exp4j's symbol checker.
 * Therefore "&" was not known to exp4j and it threw:
 *   "Operator is unknown for token: &"
 *
 * THE FIX:
 * buildCombinedExpression() creates an ExpressionBuilder with:
 *   1. All functions from FunctionRegistry (arithmetic, trig, and any boolean
 *      named functions the user registered there).
 *   2. The 11 ASCII boolean operators added directly (bypassing OperatorRegistry).
 *      These characters are all in exp4j's allowed symbol set, so no rejection.
 *   3. The 9 inline boolean named functions (xor, xnor, nand, nor, impl, rimpl,
 *      bicon, nimp, cnimp) added as anonymous Function objects. This ensures
 *      they are available even if the user registered them under different names.
 *
 * applyFunction() is also updated to use this combined builder for reconstruction
 * of any function not found in FunctionRegistry, so that expressions like
 * xor(sin(0),cos(0)) work correctly in the combined calculator.
 *
 * BOOLEAN SEMANTICS IN COMBINED:
 * All boolean operators use logical boolean semantics (any non-zero = true).
 *   9&8  → true AND true → 1   (not bitwise 8)
 *   1|0  → true OR false → 1
 *   xor(9,8) → true XOR true → 0
 * The shift operators (<<, >>) use integer casting and are truly bitwise.
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

        // No function call found — evaluate directly using the full combined builder.
        // This handles pure operator expressions like "9&8", "1|0", "5+3", "2^10".
        ExpressionBuilder exp = buildCombinedExpression(expr);
        Expression e = exp.build();
        return e.evaluate();
    }

    /**
     * Applies a named function to already-evaluated arguments.
     *
     * Priority order:
     *   1. FunctionRegistry — our custom registered functions (max, min, mean,
     *      median, fact, round, trunc, sind, cosd, etc., and any boolean functions
     *      the user registered there). Called directly via f.apply(args) to bypass
     *      exp4j's argument-count enforcement for variadic functions.
     *   2. Combined builder reconstruction — for exp4j built-in functions (sin,
     *      cos, sqrt, log, abs, floor, ceil, etc.) and inline boolean functions
     *      (xor, nand, etc.) that are not in FunctionRegistry. The reconstructed
     *      expression goes through the combined builder which has both arithmetic
     *      and boolean capabilities.
     */
    private static double applyFunction(String funcName, double[] args) {
        // Step 1: Check FunctionRegistry first (handles variadic + all custom funcs).
        for (Function f : FunctionRegistry.getFunctions()) {
            if (f.getName().equals(funcName)) {
                return f.apply(args);
            }
        }

        // Step 2: Reconstruct and evaluate through the combined builder.
        // Uses buildCombinedExpression so that boolean inline functions (xor etc.)
        // and boolean ASCII operators are available during reconstruction.
        String reconstructed = funcName + "("
                + Arrays.stream(args).mapToObj(String::valueOf).collect(Collectors.joining(","))
                + ")";
        ExpressionBuilder exp = buildCombinedExpression(reconstructed);
        return exp.build().evaluate();
    }

    /**
     * Builds an ExpressionBuilder that has the full capability of both the
     * standard arithmetic calculator and the boolean calculator combined.
     *
     * Functions:
     *   - All FunctionRegistry entries (custom arithmetic + trig + any registered
     *     boolean functions)
     *   - 9 inline boolean named functions (xor, xnor, nand, nor, impl, rimpl,
     *     bicon, nimp, cnimp) added unconditionally so they are always available
     *     regardless of what the user separately registered
     *
     * Operators:
     *   - All 11 ASCII boolean operators added directly (NOT via OperatorRegistry,
     *     which is empty). Adding them directly is safe because all their characters
     *     are in exp4j's allowed operator symbol set.
     *   - exp4j's native arithmetic operators (+, -, *, /, ^, %) are always available.
     */
    private static ExpressionBuilder buildCombinedExpression(String expr) {
        ExpressionBuilder builder = new ExpressionBuilder(expr);

        // ── All registered custom functions ──────────────────────────────────
        FunctionRegistry.getFunctions().forEach(builder::function);

        // ── Inline boolean named functions ────────────────────────────────────
        // Added unconditionally so that xor(1,0), nand(1,1), implication(1,0) etc. all
        // work in the combined calculator regardless of FunctionRegistry contents.
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

        // ── ASCII boolean operators — safe for exp4j's symbol checker ─────────
        // All characters in these symbols (&, |, !, !=, ==, >, <, >=, <=, <<, >>)
        // are in exp4j's allowed set. NOT added via OperatorRegistry (which is
        // empty) — added directly to this specific builder.
        builder
            .operator(new and())                  // &
            .operator(new or())                   // |
            .operator(new not())                  // !  (unary prefix)
            .operator(new negation())             // !=
            .operator(new equality())             // ==
            .operator(new greaterThan())          // >
            .operator(new lesserThan())           // <
            .operator(new greaterThanOrEqualTo()) // >=
            .operator(new lesserThanOrEqualTo())  // <=
            .operator(new leftShift())            // <<
            .operator(new rightShift());          // >>

        return builder;
    }
}