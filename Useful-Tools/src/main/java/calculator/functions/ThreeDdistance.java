package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

/**
 * dist3d(x1, y1, z1, x2, y2, z2) — Euclidean distance between two points
 * in 3D space.
 *
 * Formula: √((x2-x1)² + (y2-y1)² + (z2-z1)²)
 *
 * BUG FIX: Same "lambda" name collision as TwoDdistance. Renamed to "dist3d".
 *
 * Usage in expressions: dist3d(0, 0, 0, 1, 2, 2) → 3.0
 */
public class ThreeDdistance extends Function {

    public ThreeDdistance() {
        super("dist3d", 6);   // FIX: was "lambda" — conflicted with TwoDdistance
    }

    static {
        FunctionRegistry.register(new ThreeDdistance());
    }

    @Override
    public double apply(double... args) {
        double x1 = args[0];
        double y1 = args[1];
        double z1 = args[2];
        double x2 = args[3];
        double y2 = args[4];
        double z2 = args[5];
        return Math.sqrt(
                Math.pow(x2 - x1, 2) +
                Math.pow(y2 - y1, 2) +
                Math.pow(z2 - z1, 2));
    }
}