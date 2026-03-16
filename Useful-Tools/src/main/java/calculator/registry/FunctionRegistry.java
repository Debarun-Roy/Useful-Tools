package calculator.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.objecthunter.exp4j.function.Function;

/**
 * Registry of all exp4j Function instances available to ExpressionBuilderFactory.
 *
 * FIX: The original getFunctions() returned the private ArrayList directly by
 * reference. Any caller could call getFunctions().add(...) or
 * getFunctions().remove(...) and silently mutate the registry without going
 * through register(). This could cause different ExpressionBuilder instances
 * to see different sets of functions depending on call timing.
 *
 * Fixed by wrapping the return value in Collections.unmodifiableList().
 * Callers can still iterate the list (which is all ExpressionBuilderFactory
 * needs: list.forEach(builder::function)), but cannot modify it.
 */
public class FunctionRegistry {

    private static final List<Function> functions = new ArrayList<>();

    public static void register(Function function) {
        if (function == null) {
            throw new IllegalArgumentException("Cannot register a null Function.");
        }
        functions.add(function);
    }

    /**
     * Returns an unmodifiable view of the registered functions.
     * The underlying list can only be modified through register().
     */
    public static List<Function> getFunctions() {
        return Collections.unmodifiableList(functions);
    }
}