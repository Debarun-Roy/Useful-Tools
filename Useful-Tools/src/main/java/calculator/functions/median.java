package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

import java.util.Arrays;

/**
 * median(a, b, c, ...) — returns the statistical median of all arguments.
 *
 * The median is the middle value of a sorted dataset:
 *   - Odd count:  the single middle element.
 *   - Even count: the average of the two middle elements.
 *
 * BUG FIX 1 — Even/odd branches were SWAPPED:
 *   The original tested args.length % 2 == 0 for the "single middle element"
 *   case, and args.length % 2 != 0 for the "average two elements" case.
 *   These are backwards. An even-count dataset needs averaging; an odd-count
 *   dataset has a single middle element. This meant:
 *     median(1,2,3)   → returned (1+2)/2 = 1.5  (wrong; correct = 2)
 *     median(1,2,3,4) → returned 3              (wrong; correct = 2.5)
 *
 * BUG FIX 2 — "Even" case returned only one element instead of averaging:
 *   return args[args.length/2];
 *   This returns only the upper-middle element — not an average.
 *   Fixed: return (args[mid - 1] + args[mid]) / 2.0
 *
 * BUG FIX 3 — Arguments were not sorted before computing the median:
 *   The median is defined on a SORTED dataset. exp4j passes arguments in the
 *   order the user typed them. Without sorting, median(3,1,2) would compute
 *   the middle element of [3,1,2] = 1, not the true median = 2.
 *   Fixed: Arrays.sort(args) before any index access.
 *
 * BUG (minor) — Error message said "avg function" instead of "median function".
 *   Fixed.
 */
public class median extends Function {

    public median() {
        super("median", -1);  // -1 = variable argument count
    }

    static {
        FunctionRegistry.register(new median());
    }

    @Override
    public double apply(double... args) {
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "The median function requires at least 1 argument.");
        }

        // FIX 3: Sort before computing — median is defined on a sorted dataset.
        double[] sorted = args.clone();
        Arrays.sort(sorted);

        int n   = sorted.length;
        int mid = n / 2;

        if (n % 2 == 1) {
            // FIX 1+2: Odd count → single middle element.
            return sorted[mid];
        } else {
            // FIX 1+2: Even count → average of the two middle elements.
            return (sorted[mid - 1] + sorted[mid]) / 2.0;
        }
    }
}