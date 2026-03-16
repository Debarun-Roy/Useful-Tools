package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

/**
 * atan2(y, x) — returns the angle θ in radians between the positive x-axis
 * and the point (x, y), in the range (-π, π].
 *
 * This is the standard two-argument inverse tangent that correctly handles
 * all four quadrants, unlike single-argument atan(y/x) which loses quadrant
 * information and fails when x = 0.
 *
 * BUG FIX 1 — Wrong argument count:
 *   The original declared super("atan2", 1) — registering as a ONE-argument
 *   function. exp4j therefore only passes args[0] into apply(). The method
 *   then accessed args[1], which always throws ArrayIndexOutOfBoundsException.
 *   Every atan2() call crashed at runtime.
 *   Fixed: super("atan2", 2)
 *
 * BUG FIX 2 — Wrong implementation:
 *   The original computed:
 *     r = sqrt(x² + y²)
 *     θ = asin(x / r)
 *   This is NOT atan2. It is a projection formula that:
 *     (a) loses quadrant information entirely (asin range is only [-π/2, π/2])
 *     (b) uses x where y should be in the standard atan2(y, x) signature
 *   Fixed: delegates to Java's Math.atan2(y, x) which implements the correct
 *   four-quadrant inverse tangent per IEEE 754.
 *
 * Convention: atan2(y, x) — first argument is the y-coordinate (opposite),
 * second argument is the x-coordinate (adjacent). This matches C, Python,
 * Java's Math.atan2, and every major programming language.
 */
public class atan2 extends Function {

    public atan2() {
        super("atan2", 2);  // FIX: was 1 — caused ArrayIndexOutOfBoundsException
    }

    static {
        FunctionRegistry.register(new atan2());
    }

    @Override
    public double apply(double... args) {
        double y = args[0];  // first arg is y (opposite side)
        double x = args[1];  // second arg is x (adjacent side)
        return Math.atan2(y, x);
    }
}