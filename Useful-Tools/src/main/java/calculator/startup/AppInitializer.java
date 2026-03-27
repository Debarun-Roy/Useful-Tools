package calculator.startup;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Eagerly loads every calculator function class at startup so their static
 * initialiser blocks fire and register entries into FunctionRegistry before
 * the first HTTP request arrives.
 *
 * FIX: Removed "calculator.functions.not" which does not exist.
 * The unary NOT operator lives in calculator.operators.not (extends Operator,
 * not Function). It is added directly by BooleanUtils.buildBooleanExpression()
 * and CombinedUtils.buildCombinedExpression() — not via FunctionRegistry or
 * AppInitializer. Having it here caused a harmless ClassNotFoundException
 * WARNING at every startup.
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

        // ── Boolean / logic (variadic or fixed-arity functions) ───────────
        // NOTE: boolean OPERATORS (and, or, not, etc.) are NOT loaded here.
        // They live in calculator.operators.* and are added directly by
        // BooleanUtils.buildBooleanExpression() / CombinedUtils.buildCombinedExpression().
        "calculator.functions.majority",
        "calculator.functions.parity",

        // ── Geometry ──────────────────────────────────────────────────────
        "calculator.functions.TwoDdistance",
        "calculator.functions.ThreeDdistance",

        // ── Named boolean functions (registered in FunctionRegistry) ──────
        "calculator.functions.implication",
        "calculator.functions.biconditional",
        "calculator.functions.converseNonimplication",
        "calculator.functions.nonimplication",
        "calculator.functions.reverseImplication",
        "calculator.functions.xor",
        "calculator.functions.xnor",
        "calculator.functions.nand",
        "calculator.functions.nor",
    };

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