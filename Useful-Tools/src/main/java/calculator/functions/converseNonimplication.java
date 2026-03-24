package calculator.functions;

import calculator.registry.FunctionRegistry;
import net.objecthunter.exp4j.function.Function;

/**
 * Converse Non-implication (⊅) — true when b is true and a is false.
 * Truth table:
 *   0⊅0 = 0,  0⊅1 = 1,  1⊅0 = 0,  1⊅1 = 0
 *
 * This is the converse of non-implication (⊄): it is true precisely when
 * a does NOT imply b would be false from b's perspective — i.e. b is true
 * but a is false.
 *
 * BUG FIX: The original body was: return (a && !b) ? 1 : 0;
 * This is identical to nonimplication.java (⊄), making the two operators
 * indistinguishable. Converse non-implication is NOT(a) AND b, i.e. !a && b.
 *
 * Corrected to: return (!a && b) ? 1 : 0;
 */
public class converseNonimplication extends Function {

    public converseNonimplication() {
        super("converseNonimplication", 2);
    }
    
    static {
    	FunctionRegistry.register(new converseNonimplication());
    }

    @Override
    public double apply(double... args) {
        boolean a = args[0] != 0;
        boolean b = args[1] != 0;
        return (!a && b) ? 1 : 0;   // FIX: was (a && !b) — identical to nonimplication
    }
}