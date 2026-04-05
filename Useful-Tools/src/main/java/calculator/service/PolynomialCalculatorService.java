package calculator.service;

/**
 * Service for performing polynomial operations.
 * Supports evaluation, differentiation, integration, and root finding.
 */
public class PolynomialCalculatorService {

    public Object performOperation(String operation, double[] coefficients, Double x) throws Exception {
        switch (operation.toLowerCase()) {
            case "evaluate":
                if (x == null) {
                    throw new IllegalArgumentException("x parameter required for evaluation");
                }
                return evaluatePolynomial(coefficients, x);
            case "derivative":
                if (x == null) {
                    throw new IllegalArgumentException("x parameter required for derivative evaluation");
                }
                double[] derivativeCoeffs = differentiatePolynomial(coefficients);
                return evaluatePolynomial(derivativeCoeffs, x);
            case "integral":
                // Return coefficients of antiderivative (without constant)
                return integratePolynomial(coefficients);
            case "roots":
                return findRoots(coefficients);
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation +
                    ". Supported: evaluate, derivative, integral, roots");
        }
    }

    private double evaluatePolynomial(double[] coeffs, double x) {
        double result = 0;
        for (int i = 0; i < coeffs.length; i++) {
            result += coeffs[i] * Math.pow(x, coeffs.length - 1 - i);
        }
        return result;
    }

    private double[] differentiatePolynomial(double[] coeffs) {
        if (coeffs.length <= 1) {
            return new double[]{0};
        }

        double[] derivative = new double[coeffs.length - 1];
        for (int i = 0; i < derivative.length; i++) {
            // d/dx(a*x^n) = n*a*x^(n-1)
            int power = coeffs.length - 1 - i;
            derivative[i] = power * coeffs[i];
        }
        return derivative;
    }

    private double[] integratePolynomial(double[] coeffs) {
        double[] integral = new double[coeffs.length + 1];
        for (int i = 0; i < coeffs.length; i++) {
            // ∫(a*x^n)dx = (a/(n+1))*x^(n+1)
            int power = coeffs.length - 1 - i;
            integral[i] = coeffs[i] / (power + 1);
        }
        // Last coefficient is 0 (constant of integration)
        integral[integral.length - 1] = 0;
        return integral;
    }

    private double[] findRoots(double[] coeffs) throws Exception {
        // For now, only support quadratic equations
        if (coeffs.length != 3) {
            throw new IllegalArgumentException("Root finding currently only supports quadratic polynomials (3 coefficients)");
        }

        double a = coeffs[0];
        double b = coeffs[1];
        double c = coeffs[2];

        if (Math.abs(a) < 1e-10) {
            throw new IllegalArgumentException("Leading coefficient cannot be zero");
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
}