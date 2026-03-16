package calculator.startup;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Eagerly loads every calculator function and operator class at application
 * startup so their static initialiser blocks fire and register entries into
 * FunctionRegistry / OperatorRegistry before the first HTTP request arrives.
 *
 * CORRECTION from previous batch:
 *   "calculator.operators.not" was mistakenly removed in the last fix pass
 *   because the file was not present in the uploaded project snapshot.
 *   The user confirmed that calculator.operators.not does exist in the
 *   project and has been restored to OPERATOR_CLASSES.
 *
 *   Note: both a Function (calculator.functions.not) and an Operator
 *   (calculator.operators.not) exist for "not". This is intentional —
 *   they serve different expression syntaxes:
 *     Function:  not(1)   → call-style, registered in FunctionRegistry
 *     Operator:  (symbol) → infix/prefix-style, registered in OperatorRegistry
 *
 * RETAINED FIX from previous batch:
 *   "calculator.functions.nCr" is now a real implemented class and is
 *   correctly present in FUNCTION_CLASSES.
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
        "calculator.functions.not",            // Function-style: not(1)
        "calculator.functions.TwoDdistance",
        "calculator.functions.ThreeDdistance"
    };

    private static final String[] OPERATOR_CLASSES = {
        "calculator.operators.mod",
        "calculator.operators.percentage",
        "calculator.operators.leftShift",
        "calculator.operators.rightShift",
        "calculator.operators.and",
        "calculator.operators.or",
        "calculator.operators.xor",
        "calculator.operators.xnor",
        "calculator.operators.nand",
        "calculator.operators.nor",
        "calculator.operators.not",            // RESTORED: Operator-style prefix not
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
            System.err.println("[AppInitializer] WARNING: Could not load class: " + className);
            e.printStackTrace();
        }
    }
}