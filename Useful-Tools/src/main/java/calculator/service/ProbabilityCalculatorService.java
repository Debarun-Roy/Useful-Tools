package calculator.service;

import java.util.Map;

/**
 * Service for calculating probability distributions.
 * Supports normal, binomial, and Poisson distributions.
 */
public class ProbabilityCalculatorService {

    public double calculateProbability(String distribution, Map<String, Double> params) throws Exception {
        switch (distribution.toLowerCase()) {
            case "normal":
                return calculateNormalProbability(params);
            case "binomial":
                return calculateBinomialProbability(params);
            case "poisson":
                return calculatePoissonProbability(params);
            default:
                throw new IllegalArgumentException("Unsupported distribution: " + distribution +
                    ". Supported: normal, binomial, poisson");
        }
    }

    private double calculateNormalProbability(Map<String, Double> params) throws Exception {
        Double mean = params.get("mean");
        Double std = params.get("std");
        Double x = params.get("x");

        if (mean == null || std == null || x == null) {
            throw new IllegalArgumentException("Normal distribution requires mean, std, and x parameters");
        }

        if (std <= 0) {
            throw new IllegalArgumentException("Standard deviation must be positive");
        }

        // Calculate cumulative distribution function (CDF) for normal distribution
        // Using approximation formula
        double z = (x - mean) / std;
        return 0.5 * (1 + erf(z));
    }

    private double calculateBinomialProbability(Map<String, Double> params) throws Exception {
        Double n = params.get("n");
        Double p = params.get("p");
        Double k = params.get("k");

        if (n == null || p == null || k == null) {
            throw new IllegalArgumentException("Binomial distribution requires n, p, and k parameters");
        }

        if (n < 0 || n != Math.floor(n)) {
            throw new IllegalArgumentException("n must be a non-negative integer");
        }

        if (p < 0 || p > 1) {
            throw new IllegalArgumentException("p must be between 0 and 1");
        }

        if (k < 0 || k > n || k != Math.floor(k)) {
            throw new IllegalArgumentException("k must be an integer between 0 and n");
        }

        // Calculate P(X = k) for binomial distribution
        return binomialCoefficient(n.intValue(), k.intValue()) * Math.pow(p, k) * Math.pow(1 - p, n - k);
    }

    private double calculatePoissonProbability(Map<String, Double> params) throws Exception {
        Double lambda = params.get("lambda");
        Double k = params.get("k");

        if (lambda == null || k == null) {
            throw new IllegalArgumentException("Poisson distribution requires lambda and k parameters");
        }

        if (lambda <= 0) {
            throw new IllegalArgumentException("lambda must be positive");
        }

        if (k < 0 || k != Math.floor(k)) {
            throw new IllegalArgumentException("k must be a non-negative integer");
        }

        // Calculate P(X = k) for Poisson distribution
        return Math.exp(-lambda) * Math.pow(lambda, k) / factorial(k.intValue());
    }

    // Approximation of error function (erf) for normal CDF
    private double erf(double z) {
        // Abramowitz and Stegun approximation
        double t = 1.0 / (1.0 + 0.5 * Math.abs(z));
        double ans = 1 - t * Math.exp(-z*z - 1.26551223 +
                      t * (1.00002368 +
                      t * (0.37409196 +
                      t * (0.09678418 +
                      t * (-0.18628806 +
                      t * (0.27886807 +
                      t * (-1.13520398 +
                      t * (1.48851587 +
                      t * (-0.82215223 +
                      t * (0.17087277))))))))));
        return z >= 0 ? ans : -ans;
    }

    private long binomialCoefficient(int n, int k) {
        if (k > n - k) {
            k = n - k;
        }

        long result = 1;
        for (int i = 1; i <= k; i++) {
            result *= (n - k + i);
            result /= i;
        }
        return result;
    }

    private long factorial(int n) {
        if (n <= 1) return 1;
        long result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }
}