package calculator.utilities;

public class InputNormalizer {
	
	public static String normalize(String expr) {
		return expr.replace('⊕', '@')
				.replace('⊙', '#')
				.replace('↔', '~')
				.replace('⊅', '$')
				.replace('→', '_')
				.replace('⊄', ':')
				.replace('←', '?')
				.replaceAll("mod", ";");
	}
}
