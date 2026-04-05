package calculator.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for solving equations of the form f(x) = 0.
 * Currently supports quadratic equations of the form ax^2 + bx + c = 0.
 */
public class EquationSolverService {

    // Pattern to match quadratic equations: ax^2 + bx + c
    private static final Pattern QUADRATIC_PATTERN =
        Pattern.compile("(-?\\d*(?:\\.\\d+)?)\\*?x\\^2\\s*([+-]\\s*-?\\d*(?:\\.\\d+)?)\\*?x\\s*([+-]\\s*-?\\d*(?:\\.\\d+)?)");

    public double[] solveEquation(String equation) throws Exception {
        requireNonBlank(equation);

        // Remove spaces and convert to lowercase
        String normalized = equation.replaceAll("\\s+", "").toLowerCase();

        // Try to match quadratic pattern
        Matcher matcher = QUADRATIC_PATTERN.matcher(normalized);
        if (matcher.matches()) {
            double a = parseCoefficient(matcher.group(1));
            double b = parseCoefficient(matcher.group(2));
            double c = parseCoefficient(matcher.group(3));

            return solveQuadratic(a, b, c);
        }

        // For now, only support quadratic equations
        throw new IllegalArgumentException("Only quadratic equations of the form ax^2 + bx + c = 0 are currently supported");
    }

    private double parseCoefficient(String coeff) {
        if (coeff == null || coeff.isEmpty() || coeff.equals("+")) {
            return 1.0;
        }
        if (coeff.equals("-")) {
            return -1.0;
        }
        return Double.parseDouble(coeff);
    }

    private double[] solveQuadratic(double a, double b, double c) throws Exception {
        if (Math.abs(a) < 1e-10) {
            throw new IllegalArgumentException("Coefficient 'a' cannot be zero for quadratic equation");
        }

        double discriminant = b * b - 4 * a * c;

        if (discriminant > 0) {
            // Two real roots
            double sqrtD = Math.sqrt(discriminant);
            double root1 = (-b + sqrtD) / (2 * a);
            double root2 = (-b - sqrtD) / (2 * a);
            return new double[]{root1, root2};
        } else if (Math.abs(discriminant) < 1e-10) {
            // One real root (repeated)
            double root = -b / (2 * a);
            return new double[]{root};
        } else {
            // Complex roots - for now, throw exception
            throw new IllegalArgumentException("Complex roots are not supported. Discriminant is negative: " + discriminant);
        }
    }

    private void requireNonBlank(String str) {
        if (str == null || str.trim().isEmpty()) {
            throw new IllegalArgumentException("Equation cannot be null or empty");
        }
    }
}