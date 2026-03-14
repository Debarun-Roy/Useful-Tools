package calculator.startup;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * NEW FILE — AppInitializer
 *
 * WHY THIS IS NEEDED:
 * FunctionRegistry and OperatorRegistry are populated via static initialiser
 * blocks inside each individual function/operator class (e.g. radd, xor, mod).
 * The JVM only executes a static block when the class is first loaded.
 * If no code ever references a class by name, it is never loaded, its static
 * block never fires, and it is never registered — meaning the ExpressionBuilder
 * would silently fail to recognise those functions/operators at runtime.
 *
 * This ServletContextListener runs once when the web application starts up.
 * It triggers Class.forName() for every function and operator class, which
 * forces the JVM to load them and execute their static registration blocks,
 * ensuring every function and operator is registered before any request arrives.
 *
 * HOW TO USE IN ECLIPSE:
 * This file goes in:
 *   src/calculator/startup/AppInitializer.java
 * The @WebListener annotation means no web.xml entry is needed (Servlet 3.0+).
 */
@WebListener
public class AppInitializer implements ServletContextListener {

    /** All calculator function classes (in calculator.functions). */
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

    /** All calculator operator classes (in calculator.operators). */
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

        for (String className : FUNCTION_CLASSES) {
            loadClass(className);
        }
        for (String className : OPERATOR_CLASSES) {
            loadClass(className);
        }

        System.out.println("[AppInitializer] All functions and operators registered.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Nothing to clean up.
    }

    private void loadClass(String className) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException e) {
            System.err.println("[AppInitializer] WARNING: Could not load class: " + className);
            e.printStackTrace();
        }
    }
}
