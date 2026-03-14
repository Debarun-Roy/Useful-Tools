package calculator.registry;

import java.util.ArrayList;
import java.util.List;
import net.objecthunter.exp4j.function.Function;

public class FunctionRegistry {
	
	private static final List<Function> functions = new ArrayList<>();
	
	public static void register(Function function) {
		functions.add(function);
	}
	
	public static List<Function> getFunctions() {
        return functions;
    }
}
