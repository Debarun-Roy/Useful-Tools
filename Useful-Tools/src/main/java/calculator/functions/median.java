package calculator.functions;

import java.util.Arrays;
import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

/**
 * median(a, b, ...) — returns the statistical median of all arguments.
 * Argument count declared as 1 — see max.java for the full explanation.
 *
 * Correctness fixes retained from previous batch:
 *   - Arguments are sorted before computing (median requires sorted data)
 *   - Odd count:  single middle element
 *   - Even count: average of the two middle elements
 */
public class median extends Function {

    public median() {
        super("median", 1);
    }

    static {
        FunctionRegistry.register(new median());
    }

    @Override
    public double apply(double... args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("median requires at least 1 argument.");
        }
        double[] sorted = args.clone();
        Arrays.sort(sorted);
        int n   = sorted.length;
        int mid = n / 2;
        if (n % 2 == 1) {
            return sorted[mid];
        } else {
            return (sorted[mid - 1] + sorted[mid]) / 2.0;
        }
    }
}