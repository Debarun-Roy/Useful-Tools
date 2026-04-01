package calculator.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Descriptive statistics for a numeric dataset.
 *
 * All statistics use POPULATION formulas (divides by n, not n-1) for
 * consistency regardless of dataset size. Population variance is the natural
 * choice when the user supplies the entire dataset, which is the assumed use case.
 *
 * Skewness — Pearson's second coefficient:
 *   skewness = 3(mean - median) / stdDev
 *   Range: typically [-3, 3]. Positive = right-skewed. Zero if stdDev = 0.
 *
 * Kurtosis — excess (Fisher) kurtosis:
 *   kurtosis = E[(X-μ)^4] / σ^4 - 3
 *   A normal distribution has excess kurtosis = 0. Positive = leptokurtic
 *   (heavier tails). Zero if stdDev = 0.
 *
 * Mode:
 *   Reports the most frequent value(s). Returns "None" when all values are
 *   unique (every dataset of distinct floats). Returns multiple values
 *   (comma-separated, sorted) when multiple modes exist (multimodal distribution).
 */
public class StatisticsCalculatorService {

    public LinkedHashMap<String, Object> calculate(double[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Dataset must contain at least one number.");
        }

        int n = data.length;
        double[] sorted = data.clone();
        Arrays.sort(sorted);

        double sum = 0;
        for (double v : data) sum += v;
        double mean = sum / n;

        double min    = sorted[0];
        double max    = sorted[n - 1];
        double range  = max - min;
        double median = computeMedian(sorted, n);
        String mode   = computeMode(data);

        // Population variance: E[(X - μ)²]
        double varianceSum = 0;
        for (double v : data) varianceSum += (v - mean) * (v - mean);
        double variance = varianceSum / n;
        double stdDev   = Math.sqrt(variance);

        // Pearson's second skewness coefficient
        double skewness = stdDev > 1e-12 ? 3.0 * (mean - median) / stdDev : 0.0;

        // Excess (Fisher) kurtosis: E[(X-μ)^4]/σ^4 - 3
        double kurtosisNumerator = 0;
        for (double v : data) {
            double d = v - mean;
            kurtosisNumerator += d * d * d * d;
        }
        double kurtosis = stdDev > 1e-12
                ? (kurtosisNumerator / n) / (variance * variance) - 3.0
                : 0.0;

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("count",    n);
        result.put("sum",      r6(sum));
        result.put("mean",     r6(mean));
        result.put("median",   r6(median));
        result.put("mode",     mode);
        result.put("min",      min);
        result.put("max",      max);
        result.put("range",    r6(range));
        result.put("variance", r6(variance));
        result.put("stdDev",   r6(stdDev));
        result.put("skewness", r6(skewness));
        result.put("kurtosis", r6(kurtosis));
        return result;
    }

    private static double computeMedian(double[] sorted, int n) {
        return n % 2 == 1
                ? sorted[n / 2]
                : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }

    private static String computeMode(double[] data) {
        Map<Double, Long> freq = Arrays.stream(data)
                .boxed()
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

        long maxFreq = freq.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        if (maxFreq <= 1) return "None"; // all values unique

        List<String> modes = freq.entrySet().stream()
                .filter(e -> e.getValue() == maxFreq)
                .map(Map.Entry::getKey)
                .sorted()
                .map(v -> {
                    // Display integers without decimal point for readability.
                    if (!Double.isInfinite(v) && v == Math.floor(v))
                        return String.valueOf((long) v.doubleValue());
                    return String.valueOf(v);
                })
                .collect(Collectors.toList());

        return String.join(", ", modes);
    }

    /** Rounds to 6 significant decimal places; propagates NaN/Infinity. */
    private static double r6(double v) {
        if (!Double.isFinite(v)) return v;
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }
}