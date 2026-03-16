package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

/**
 * nCr(n, r) — returns the binomial coefficient C(n, r) = n! / (r! × (n-r)!)
 * This is the number of ways to choose r items from n items without regard
 * to order (combinations).
 *
 * FIX: The original file was entirely commented-out placeholder code with
 * no implementation. AppInitializer attempted to load "calculator.functions.nCr"
 * at startup, which threw ClassNotFoundException on every deployment.
 * This implementation provides the complete, working function.
 *
 * Input validation:
 *   - n and r must be non-negative integers. Returns Double.NaN otherwise.
 *   - r must be <= n. Returns Double.NaN otherwise (C(n,r) is 0 in
 *     combinatorics when r > n, but NaN makes the error visible to the user).
 *   - C(n, 0) = C(n, n) = 1 by definition.
 *
 * Implementation note:
 *   Uses the multiplicative formula C(n,r) = ∏(i=1 to r) [(n+1-i) / i]
 *   rather than computing three separate factorials. This is more numerically
 *   stable and avoids intermediate overflow for moderate n values. Computed
 *   in double arithmetic; result is rounded to the nearest integer since
 *   C(n,r) is always a whole number.
 *
 * Range: reliable up to approximately n=60 before double precision loss.
 * For n > 60 the result is still correct in magnitude but may not be exact.
 */
public class nCr extends Function {

    public nCr() {
        super("nCr", 2);
    }

    static {
        FunctionRegistry.register(new nCr());
    }

    @Override
    public double apply(double... args) {
        double nd = args[0];
        double rd = args[1];

        // Both must be non-negative integers.
        if (nd != Math.floor(nd) || rd != Math.floor(rd)) {
            return Double.NaN;
        }
        if (nd < 0 || rd < 0) {
            return Double.NaN;
        }

        int n = (int) nd;
        int r = (int) rd;

        // r must not exceed n.
        if (r > n) {
            return Double.NaN;
        }

        // C(n, 0) = C(n, n) = 1.
        if (r == 0 || r == n) {
            return 1.0;
        }

        // Optimisation: C(n, r) == C(n, n-r); use the smaller r.
        if (r > n - r) {
            r = n - r;
        }

        // Multiplicative formula: avoids computing large intermediate factorials.
        double result = 1.0;
        for (int i = 0; i < r; i++) {
            result *= (n - i);
            result /= (i + 1);
        }

        // Round to nearest integer — C(n,r) is always a whole number.
        return Math.round(result);
    }
}