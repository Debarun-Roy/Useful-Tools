package calculator.startup;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Eagerly loads every calculator function class at application startup so their
 * static initialiser blocks fire and register entries into FunctionRegistry
 * before the first HTTP request arrives.
 *
 * WHY THE HYPERBOLIC FUNCTIONS WERE FAILING:
 * The new hyperbolic function classes (sinh, cosh, tanh, asinh, acosh, etc.)
 * were created and have correct static { FunctionRegistry.register(...) } blocks,
 * but they were not listed in FUNCTION_CLASSES below.
 *
 * Without a Class.forName() call here, the JVM never loads those classes, so
 * their static blocks never fire, and they are never registered in FunctionRegistry.
 *
 * When IntermediateUtils.applyFunction searches FunctionRegistry for "asinh" and
 * finds nothing, it falls through to ExpressionBuilderFactory.create("asinh(1.0)").
 * The factory builds an ExpressionBuilder and calls builder::function for every
 * entry in FunctionRegistry — which includes "asin" (the inverse trig function)
 * but NOT "asinh" (unregistered). exp4j's tokenizer then reads "asin" as a prefix
 * match inside "asinh", emits it as a function token, and leaves "h" as an unknown
 * variable. The expression fails.
 *
 * The fix: add every new function class to FUNCTION_CLASSES so Class.forName
 * loads it, fires the static block, and registers it in FunctionRegistry.
 * After that, IntermediateUtils finds the function in FunctionRegistry and
 * calls f.apply(args) directly — exp4j's tokenizer is never involved.
 *
 * NOTE: Adjust class names below to exactly match your .java file names if any
 * differ from the pattern used here (e.g. if you named the class Sinh vs sinh).
 *
 * OPERATOR_CLASSES is intentionally empty — see previous commit comments.
 */
@WebListener
public class AppInitializer implements ServletContextListener {

    private static final String[] FUNCTION_CLASSES = {

        // ── Complex number helpers ────────────────────────────────────────
        "calculator.functions.radd",
        "calculator.functions.rsub",
        "calculator.functions.cadd",
        "calculator.functions.csub",
        "calculator.functions.csq",
        "calculator.functions.real",
        "calculator.functions.imag",

        // ── Logarithm ─────────────────────────────────────────────────────
        "calculator.functions.logn",

        // ── Combinatorics and number theory ───────────────────────────────
        "calculator.functions.fact",
        "calculator.functions.nCr",

        // ── Statistics (variadic — declared with argc=1 as sentinel) ──────
        "calculator.functions.max",
        "calculator.functions.min",
        "calculator.functions.mean",
        "calculator.functions.median",

        // ── Utility ───────────────────────────────────────────────────────
        "calculator.functions.percent",
        "calculator.functions.round",
        "calculator.functions.trunc",

        // ── Standard trig (degrees) ───────────────────────────────────────
        "calculator.functions.sind",
        "calculator.functions.cosd",
        "calculator.functions.tand",
        "calculator.functions.secd",
        "calculator.functions.cosecd",
        "calculator.functions.cotd",

        // ── Standard trig (radians — reciprocal) ─────────────────────────
        "calculator.functions.sec",
        "calculator.functions.cosec",
        "calculator.functions.cot",

        // ── Inverse trig (radians) ────────────────────────────────────────
        "calculator.functions.acosec",
        "calculator.functions.asec",
        "calculator.functions.acot",
        "calculator.functions.atan2",

        // ── Hyperbolic trig (radians) ─────────────────────────────────────
        // These were NOT here before — the missing registration was the bug.
        "calculator.functions.sinh",
        "calculator.functions.cosh",
        "calculator.functions.tanh",
        "calculator.functions.cosech",
        "calculator.functions.sech",
        "calculator.functions.coth",

        // ── Hyperbolic trig (degrees) ─────────────────────────────────────
        "calculator.functions.sinhd",
        "calculator.functions.coshd",
        "calculator.functions.tanhd",
        "calculator.functions.cosechd",
        "calculator.functions.sechd",
        "calculator.functions.cothd",

        // ── Arc hyperbolic (radians) ──────────────────────────────────────
        // These share prefixes with existing inverse trig functions (asinh/asin,
        // acosh/acos, etc.) but that is NOT a problem: IntermediateUtils finds
        // them in FunctionRegistry by EXACT name and calls f.apply() directly,
        // bypassing exp4j's tokenizer entirely.
        "calculator.functions.asinh",
        "calculator.functions.acosh",
        "calculator.functions.atanh",
        "calculator.functions.acosech",
        "calculator.functions.asech",
        "calculator.functions.acoth",

        // ── Arc hyperbolic (degrees) ──────────────────────────────────────
        "calculator.functions.asinhd",
        "calculator.functions.acoshd",
        "calculator.functions.atanhd",
        "calculator.functions.acosechd",
        "calculator.functions.asechd",
        "calculator.functions.acothd",

        // ── Boolean / logic ───────────────────────────────────────────────
        "calculator.functions.majority",
        "calculator.functions.parity",
        "calculator.functions.not",

        // ── Geometry ──────────────────────────────────────────────────────
        "calculator.functions.TwoDdistance",
        "calculator.functions.ThreeDdistance",
        // ── Boolean ──────────────────────────────────────────────────────
        "calculator.functions.implication",
        "calculator.functions.biconditional",
        "calculator.functions.converseNonimplication",
        "calculator.functions.nonimplication",
        "calculator.functions.reverseImplication"
    };

    // Intentionally empty — all boolean/logic operators are registered
    // directly in BooleanUtils.buildBooleanExpression() and
    // CombinedUtils.buildCombinedExpression() to avoid exp4j rejecting
    // unicode symbols at global registration time.
    private static final String[] OPERATOR_CLASSES = {};

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[AppInitializer] Loading calculator functions...");
        for (String cls : FUNCTION_CLASSES) loadClass(cls);
        System.out.println("[AppInitializer] Startup class loading complete.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) { }

    private void loadClass(String className) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException e) {
            System.err.println("[AppInitializer] WARNING: class not found: " + className);
            e.printStackTrace();
        } catch (Error e) {
            System.err.println("[AppInitializer] WARNING: failed to initialise: " + className);
            e.printStackTrace();
        }
    }
}