package calculator.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import calculator.dao.ComputeDAO;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import calculator.functions.cadd;
import calculator.functions.csq;
import calculator.functions.csub;
import calculator.functions.imag;
import calculator.functions.radd;
import calculator.functions.real;
import calculator.functions.rsub;

/**
 * NEW FILE — ComplexNumberService
 *
 * WHY THIS IS NEEDED:
 * ComplexNumberCalculatorController.doPost() contained ~250 lines of inline
 * complex-number arithmetic using regex, exp4j, and direct DAO calls, all
 * crammed into the servlet. This made the logic impossible to unit-test without
 * a running servlet container.
 *
 * This service class extracts every operation into its own testable method.
 * The controller becomes a thin dispatcher (~60 lines) that delegates to here.
 *
 * RESULT MODEL:
 * Every operation returns a ComplexResult record containing the real part,
 * imaginary part, and a formatted display string "a+bi".
 */
public class ComplexNumberService {

    /**
     * Immutable result of a complex number operation.
     */
    public static class ComplexResult {
        public final double real;
        public final double imag;
        public final String display;

        public ComplexResult(double real, double imag) {
            this.real = real;
            this.imag = imag;
            // Format: avoids ugly "3.0+-2.0i" by using subtraction for negative imag
            if (imag < 0) {
                this.display = real + "-" + Math.abs(imag) + "i";
            } else {
                this.display = real + "+" + imag + "i";
            }
        }

        @Override
        public String toString() { return display; }
    }

    // -------------------------------------------------------------------------
    // Public operation methods
    // -------------------------------------------------------------------------

    /**
     * Adds two complex numbers expressed as "(a+bi, c+di)".
     * Input pattern: "complex_add(a+bi,c+di)"
     */
    public ComplexResult add(String input) throws Exception {
        double[] parts = parseTwoComplexArgs(input, "complex_add");
        double realResult = buildAndEval("radd", parts[0], parts[2], new radd());
        double imagResult = buildAndEval("cadd", parts[1], parts[3], new cadd());
        ComputeDAO.storeExpressionResult(input, new ComplexResult(realResult, imagResult).display);
        return new ComplexResult(realResult, imagResult);
    }

    /**
     * Subtracts two complex numbers.
     * Input pattern: "complex_subtract(a+bi,c+di)"
     */
    public ComplexResult subtract(String input) throws Exception {
        double[] parts = parseTwoComplexArgs(input, "complex_subtract");
        double realResult = buildAndEval("rsub", parts[0], parts[2], new rsub());
        double imagResult = buildAndEval("csub", parts[1], parts[3], new csub());
        ComputeDAO.storeExpressionResult(input, new ComplexResult(realResult, imagResult).display);
        return new ComplexResult(realResult, imagResult);
    }

    /**
     * Computes the conjugate of a complex number.
     * Input pattern: "conj(a+bi)"
     */
    public ComplexResult conjugate(String input) throws Exception {
        Pattern p = Pattern.compile("conj\\(([^+\\-]+)\\+([^+\\-]+)i\\)");
        Matcher m = p.matcher(input);
        if (!m.find()) {
            throw new IllegalArgumentException("Invalid conj() expression: " + input);
        }
        double realPart = new ExpressionBuilder(m.group(1)).build().evaluate();
        double imagPart = new ExpressionBuilder(m.group(2)).build().evaluate();
        // Conjugate negates the imaginary part
        ComplexResult result = new ComplexResult(realPart, -imagPart);
        ComputeDAO.storeExpressionResult(input, result.display);
        return result;
    }

    /**
     * Extracts the imaginary component.
     * Input pattern: "imag(a+bi)"
     */
    public ComplexResult imagPart(String input) throws Exception {
        Expression e = new ExpressionBuilder(input).function(new imag()).build();
        double imagResult = e.evaluate();
        ComputeDAO.storeExpressionResult(input, Double.toString(imagResult));
        return new ComplexResult(0.0, imagResult);
    }

    /**
     * Extracts the real component.
     * Input pattern: "real(a+bi)"
     */
    public ComplexResult realPart(String input) throws Exception {
        Expression e = new ExpressionBuilder(input).function(new real()).build();
        double realResult = e.evaluate();
        ComputeDAO.storeExpressionResult(input, Double.toString(realResult));
        return new ComplexResult(realResult, 0.0);
    }

    /**
     * Computes the modulus squared |z|² = a² + b².
     * Input pattern: "csq(a+bi)"
     */
    public ComplexResult modulusSquared(String input) throws Exception {
        Expression e = new ExpressionBuilder(input).function(new csq()).build();
        double result = e.evaluate();
        ComputeDAO.storeExpressionResult(input, Double.toString(result));
        return new ComplexResult(result, 0.0);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Parses a two-complex-number function call of the form:
     *   funcName(a+bi,c+di)
     * Returns [a, b, c, d] as doubles.
     */
    private double[] parseTwoComplexArgs(String input, String funcName) throws Exception {
        String pattern = funcName + "\\(([^+\\-]+)\\+([^+\\-]+)i,([^+\\-]+)\\+([^+\\-]+)i\\)";
        Matcher m = Pattern.compile(pattern).matcher(input);
        if (!m.find()) {
            throw new IllegalArgumentException("Invalid " + funcName + "() expression: " + input);
        }
        return new double[]{
            new ExpressionBuilder(m.group(1)).build().evaluate(),
            new ExpressionBuilder(m.group(2)).build().evaluate(),
            new ExpressionBuilder(m.group(3)).build().evaluate(),
            new ExpressionBuilder(m.group(4)).build().evaluate()
        };
    }

    /**
     * Builds an exp4j two-argument expression string for the given function
     * and evaluates it.
     * Example: buildAndEval("radd", 3.0, 4.0, new radd()) → evaluates "radd(3.0,4.0)"
     */
    private double buildAndEval(String funcName, double arg1, double arg2,
                                net.objecthunter.exp4j.function.Function fn) throws Exception {
        String expr = funcName + "(" + arg1 + "," + arg2 + ")";
        return new ExpressionBuilder(expr).function(fn).build().evaluate();
    }
}