package calculator.operators;

import calculator.registry.OperatorRegistry;
import net.objecthunter.exp4j.operator.Operator;

/**
 * XNOR (⊙) — true when both inputs have the same truth value.
 * Truth table:
 *   0⊙0 = 1,  0⊙1 = 0,  1⊙0 = 0,  1⊙1 = 1
 *
 * BUG FIX: The original had:
 *   boolean b = args[1] != 1;
 * This inverts b. With b inverted, the expression ((a && b) || (!a && !b))
 * computes "a and not-b, or not-a and b" — which is XOR, the exact
 * opposite of XNOR. Every result in the truth table was wrong.
 *
 * Corrected to: boolean b = args[1] != 0;
 * The XNOR formula is equivalently written as !(a ^ b), or simply (a == b).
 */
public class xnor extends Operator {

    public xnor() {
        super("⊙", 2, true, Operator.PRECEDENCE_ADDITION - 4);
    }

    static {
        OperatorRegistry.register(new xnor());
    }

    @Override
    public double apply(double... args) {
        boolean a = args[0] != 0;
        boolean b = args[1] != 0;  // FIX: was "!= 1" which inverted b
        return (a == b) ? 1 : 0;   // cleaner than ((a&&b)||(!a&&!b))
    }
}