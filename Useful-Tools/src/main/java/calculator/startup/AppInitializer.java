package calculator.startup;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Eagerly loads every calculator function and operator class so their static
 * initialiser blocks fire and register entries into FunctionRegistry /
 * OperatorRegistry before the first HTTP request arrives.
 *
 * UPDATE: "and" and "or" are now in calculator.operators (fixed in previous
 *   review batch — they were wrongly in calculator.functions). Their entries
 *   here reflect the corrected package.
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
        "calculator.functions.conj",
        "calculator.functions.logn",
        "calculator.functions.fact",
        "calculator.functions.nCr",
        "calculator.functions.max",
        "calculator.functions.min",
        "calculator.functions.mean",
        "calculator.functions.median",
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

    private static final String[] OPERATOR_CLASSES = {
        "calculator.operators.mod",
        "calculator.operators.percentage",
        "calculator.operators.leftShift",
        "calculator.operators.rightShift",
        "calculator.operators.and",          // ← moved from functions in fix batch
        "calculator.operators.or",           // ← moved from functions in fix batch
        "calculator.operators.xor",
        "calculator.operators.xnor",
        "calculator.operators.nand",
        "calculator.operators.nor",
        "calculator.operators.not",
        "calculator.operators.negation",
        "calculator.operators.implication",
        "calculator.operators.reverseImplication",
        "calculator.operators.biconditional",
        "calculator.operators.nonimplication",
        "calculator.operators.converseNonimplication",
        "calculator.operators.equality",
        "calculator.operators.greaterThan",
        "calculator.operators.greaterThanOrEqualTo",
        "calculator.operators.lesserThan",
        "calculator.operators.lesserThanOrEqualTo"
    };

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[AppInitializer] Loading calculator functions and operators...");
        for (String cls : FUNCTION_CLASSES)  loadClass(cls);
        for (String cls : OPERATOR_CLASSES)  loadClass(cls);
        System.out.println("[AppInitializer] All functions and operators registered successfully.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) { }

    private void loadClass(String className) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException e) {
            System.err.println("[AppInitializer] WARNING: Could not load " + className);
            e.printStackTrace();
        }
    }
}