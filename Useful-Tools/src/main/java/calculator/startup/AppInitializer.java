package calculator.startup;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Eagerly loads every calculator function class at application startup so their
 * static initialiser blocks fire and register entries into FunctionRegistry
 * before the first HTTP request arrives.
 *
 * OPERATOR_CLASSES is intentionally empty:
 *   All boolean/logic/unicode operators (xor, nand, implication, etc.) have had
 *   their static registration blocks removed. They are instantiated directly by
 *   BooleanUtils using 'new xor()' etc. and never go through OperatorRegistry.
 *   This is necessary because exp4j's ExpressionBuilder.checkOperatorSymbol()
 *   rejects any symbol that is not a valid special-character token — it rejects
 *   unicode characters (xor symbol, implication arrow, etc.) and letter-based
 *   words ("mod") alike. Loading them into ExpressionBuilderFactory crashed ALL
 *   calculators, not just the boolean one.
 *
 *   The 'mod' operator was removed because exp4j already handles '%' as a native
 *   binary modulo operator. The 'percentage' unary operator was removed because
 *   its '%' symbol conflicts with exp4j's built-in '%'. Percentage is now the
 *   'percent' function: percent(x) returns x/100.
 *
 * loadClass() catches both ClassNotFoundException and Error so that a single
 * broken class does not prevent the rest of the application from starting.
 */
@WebListener
public class AppInitializer implements ServletContextListener {

    private static final String[] FUNCTION_CLASSES = {
        "calculator.functions.radd",
        "calculator.functions.rsub",
        "calculator.functions.cadd",
        "calculator.functions.csub",
        "calculator.functions.csq",
        "calculator.functions.real",
        "calculator.functions.imag",
        "calculator.functions.logn",
        "calculator.functions.fact",
        "calculator.functions.nCr",
        "calculator.functions.max",
        "calculator.functions.min",
        "calculator.functions.mean",
        "calculator.functions.median",
        "calculator.functions.percent",
        "calculator.functions.round",
        "calculator.functions.trunc",
        "calculator.functions.sind",
        "calculator.functions.cosd",
        "calculator.functions.tand",
        "calculator.functions.sec",
        "calculator.functions.cosec",
        "calculator.functions.cot",
        "calculator.functions.secd",
        "calculator.functions.cosecd",
        "calculator.functions.cotd",
        "calculator.functions.acosec",
        "calculator.functions.acot",
        "calculator.functions.asec",
        "calculator.functions.atan2",
        "calculator.functions.majority",
        "calculator.functions.parity",
        "calculator.functions.not",
        "calculator.functions.TwoDdistance",
        "calculator.functions.ThreeDdistance"
    };

    // Intentionally empty — see class-level comment above.
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
            System.err.println("[AppInitializer] WARNING: failed to initialise class: " + className);
            e.printStackTrace();
        }
    }
}
