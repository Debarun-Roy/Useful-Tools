package calculator.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for solving equations of the form f(x) = 0.
 * Supports polynomial equations of any degree using numerical methods.
 */
public class EquationSolverService {

    private static final int MAX_ITERATIONS = 100;
    private static final double TOLERANCE = 1e-10;
    private static final double H = 1e-8; // For numerical differentiation

    public double[] solveEquation(String equation) throws Exception {
        requireNonBlank(equation);

        String transformedEquation;

        // Check if equation contains '='
        if (equation.contains("=")) {
            // Split equation by = and move everything to left side
            String[] sides = equation.split("=", 2); // Split only on first =

            String left = sides[0].trim();
            String right = sides[1].trim();

            // If right side is empty, assume = 0
            if (right.isEmpty()) {
                transformedEquation = left;
            } else {
                // Transform: left = right becomes left - right = 0
                transformedEquation = left + "-(" + right + ")";
            }
        } else {
            // No '=' found, assume = 0
            transformedEquation = equation.trim();
        }

        // Parse polynomial coefficients
        double[] coeffs = parsePolynomialCoefficients(transformedEquation);
        if (coeffs == null || coeffs.length == 0) {
            throw new IllegalArgumentException("Could not parse equation as a polynomial");
        }

        // Find roots using numerical methods
        return findRoots(coeffs);
    }

    private double[] parsePolynomialCoefficients(String expr) {
        // Remove spaces
        expr = expr.replaceAll("\\s+", "");

        int maxDegree = 0;

        // Split by + and - but keep the signs
        List<String> terms = new ArrayList<>();
        StringBuilder currentTerm = new StringBuilder();

        for (int i = 0; i < expr.length(); i++) {
            char ch = expr.charAt(i);
            if (ch == '+' || ch == '-') {
                if (currentTerm.length() > 0) {
                    terms.add(currentTerm.toString());
                    currentTerm.setLength(0);
                }
                currentTerm.append(ch);
            } else {
                currentTerm.append(ch);
            }
        }
        if (currentTerm.length() > 0) {
            terms.add(currentTerm.toString());
        }

        // Find the maximum degree
        for (String term : terms) {
            int degree = getTermDegree(term);
            if (degree > maxDegree) {
                maxDegree = degree;
            }
        }

        // Initialize coefficient array (index 0 = constant, index n = x^n coefficient)
        double[] coeffs = new double[maxDegree + 1];

        // Parse each term
        for (String term : terms) {
            double coeff = 1.0;
            boolean negative = false;

            if (term.startsWith("-")) {
                negative = true;
                term = term.substring(1);
            } else if (term.startsWith("+")) {
                term = term.substring(1);
            }

            int degree = getTermDegree(term);
            String coeffStr = term;

            // Remove variable part
            if (degree > 0) {
                if (degree == 1) {
                    coeffStr = term.replaceAll("x$", "").replaceAll("\\*x$", "");
                } else {
                    coeffStr = term.replaceAll("x\\^\\d+$", "").replaceAll("\\*x\\^\\d+$", "");
                }
            }

            // Parse coefficient
            if (coeffStr.isEmpty()) {
                coeff = 1.0;
            } else if (coeffStr.equals("-")) {
                coeff = -1.0;
            } else {
                try {
                    coeff = Double.parseDouble(coeffStr);
                } catch (NumberFormatException e) {
                    continue; // Skip invalid terms
                }
            }

            coeffs[degree] += negative ? -coeff : coeff;
        }

        return coeffs;
    }

    private int getTermDegree(String term) {
        if (term.contains("x^")) {
            String[] parts = term.split("x\\^");
            if (parts.length > 1) {
                try {
                    return Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        } else if (term.contains("x") && !term.contains("^")) {
            return 1;
        }
        return 0; // Constant term
    }

    private double[] findRoots(double[] coeffs) throws Exception {
        // For polynomials of degree 0, 1, or 2, use exact methods
        int degree = coeffs.length - 1;

        if (degree == 0) {
            throw new IllegalArgumentException("Constant equation has no solutions");
        } else if (degree == 1) {
            return solveLinear(coeffs[1], coeffs[0]);
        } else if (degree == 2) {
            return solveQuadratic(coeffs[2], coeffs[1], coeffs[0]);
        }

        // For higher degrees, use numerical methods
        return findRootsNumerically(coeffs);
    }

    private double[] solveLinear(double a, double b) {
        if (Math.abs(a) < TOLERANCE) {
            throw new IllegalArgumentException("Linear coefficient cannot be zero");
        }
        return new double[]{-b / a};
    }

    private double[] solveQuadratic(double a, double b, double c) throws Exception {
        if (Math.abs(a) < TOLERANCE) {
            throw new IllegalArgumentException("Leading coefficient cannot be zero");
        }

        double discriminant = b * b - 4 * a * c;

        if (discriminant > 0) {
            double sqrtD = Math.sqrt(discriminant);
            double root1 = (-b + sqrtD) / (2 * a);
            double root2 = (-b - sqrtD) / (2 * a);
            return new double[]{root1, root2};
        } else if (Math.abs(discriminant) < TOLERANCE) {
            return new double[]{-b / (2 * a)};
        } else {
            throw new IllegalArgumentException("Complex roots are not supported. Discriminant is negative: " + discriminant);
        }
    }

    private double[] findRootsNumerically(double[] coeffs) throws Exception {
        List<Double> roots = new ArrayList<>();

        // Try to find roots in different intervals
        double[] testPoints = {-10, -5, -1, 0, 1, 5, 10};

        for (int i = 0; i < testPoints.length - 1; i++) {
            double a = testPoints[i];
            double b = testPoints[i + 1];

            try {
                double root = newtonRaphson(coeffs, a, b);
                // Check if root is already found (within tolerance)
                boolean isDuplicate = false;
                for (double existingRoot : roots) {
                    if (Math.abs(root - existingRoot) < TOLERANCE) {
                        isDuplicate = true;
                        break;
                    }
                }
                if (!isDuplicate) {
                    roots.add(root);
                }
            } catch (Exception e) {
                // No root found in this interval, continue
            }
        }

        if (roots.isEmpty()) {
            throw new IllegalArgumentException("Could not find any real roots in the tested range");
        }

        return roots.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private double newtonRaphson(double[] coeffs, double x0, double x1) throws Exception {
        // Use bisection to get a good starting point
        double x = bisection(coeffs, x0, x1);

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double fx = evaluatePolynomial(coeffs, x);
            double fpx = numericalDerivative(coeffs, x);

            if (Math.abs(fpx) < TOLERANCE) {
                throw new Exception("Derivative too small");
            }

            double xNew = x - fx / fpx;

            if (Math.abs(xNew - x) < TOLERANCE) {
                return xNew;
            }

            x = xNew;
        }

        throw new Exception("Newton-Raphson did not converge");
    }

    private double bisection(double[] coeffs, double a, double b) throws Exception {
        double fa = evaluatePolynomial(coeffs, a);
        double fb = evaluatePolynomial(coeffs, b);

        if (fa * fb >= 0) {
            throw new Exception("No root in interval");
        }

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double c = (a + b) / 2;
            double fc = evaluatePolynomial(coeffs, c);

            if (Math.abs(fc) < TOLERANCE) {
                return c;
            }

            if (fc * fa < 0) {
                b = c;
                fb = fc;
            } else {
                a = c;
                fa = fc;
            }
        }

        return (a + b) / 2;
    }

    private double evaluatePolynomial(double[] coeffs, double x) {
        double result = 0.0;
        for (int i = 0; i < coeffs.length; i++) {
            result += coeffs[i] * Math.pow(x, i);
        }
        return result;
    }

    private double numericalDerivative(double[] coeffs, double x) {
        return (evaluatePolynomial(coeffs, x + H) - evaluatePolynomial(coeffs, x - H)) / (2 * H);
    }

    private void requireNonBlank(String str) {
        if (str == null || str.trim().isEmpty()) {
            throw new IllegalArgumentException("Equation cannot be null or empty");
        }
    }
}