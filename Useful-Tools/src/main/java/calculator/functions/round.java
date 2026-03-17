package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

/**
 * round(number, decimalPlaces) — rounds number to the specified number of
 * decimal places.
 *
 * Examples:
 *   round(3.14159, 2) → 3.14
 *   round(3.14159, 0) → 3.0
 *   round(3.14159, 4) → 3.1416
 *
 * BUG FIX 1 — Wrong argument used for decimal places:
 *   The original had: int dec = (int) Math.rint(args[0]);
 *   args[0] is the NUMBER to round (e.g. 3.14159), not the decimal place count.
 *   For round(3.14159, 2), Math.rint(3.14159) = 3, giving dec=3 accidentally.
 *   For round(99.5, 2), Math.rint(99.5) = 100, causing a substring overflow.
 *   Fixed: int dec = (int) Math.rint(args[1]);
 *
 * BUG FIX 2 — String-based rounding was fragile and crash-prone:
 *   The original used String.substring(0, indexOfDecimal + dec + 1), which
 *   threw StringIndexOutOfBoundsException whenever dec exceeded the number
 *   of available decimal digits in the string representation.
 *   Replaced with the standard numeric approach:
 *     result = Math.round(num * 10^dec) / 10^dec
 *   This is correct, safe, and handles all inputs.
 *
 * BUG FIX 3 — Negative decimal places now return Double.NaN instead of
 *   producing undefined substring behaviour.
 */
public class round extends Function {

    public round() {
        super("round", 2);
    }

    static {
        FunctionRegistry.register(new round());
    }

    @Override
    public double apply(double... args) {
        double num = args[0];
        int    dec = (int) Math.rint(args[1]);  // FIX: was args[0]

        if (dec < 0) {
            return Double.NaN;
        }

        double factor = Math.pow(10.0, dec);
        return Math.round(num * factor) / factor;
    }
}