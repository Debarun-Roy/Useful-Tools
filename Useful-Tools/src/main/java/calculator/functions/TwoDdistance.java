package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

/**
 * dist2d(x1, y1, x2, y2) — Euclidean distance between two points in 2D space.
 *
 * Formula: √((x2-x1)² + (y2-y1)²)
 *
 * BUG FIX: The original registered this function under the name "lambda"
 * (super("lambda", 4)). ThreeDdistance also used "lambda" (super("lambda", 6)).
 * Two different functions sharing the same name in the registry means whichever
 * is loaded second silently replaces the first. Neither was reliably callable.
 *
 * Renamed to "dist2d" to be distinct, descriptive, and consistent with the
 * companion function "dist3d".
 *
 * Usage in expressions: dist2d(0, 0, 3, 4) → 5.0
 */
public class TwoDdistance extends Function {

    public TwoDdistance() {
        super("dist2d", 4);   // FIX: was "lambda" — conflicted with ThreeDdistance
    }

    static {
        FunctionRegistry.register(new TwoDdistance());
    }

    @Override
    public double apply(double... args) {
        double x1 = args[0];
        double y1 = args[1];
        double x2 = args[2];
        double y2 = args[3];
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }
}