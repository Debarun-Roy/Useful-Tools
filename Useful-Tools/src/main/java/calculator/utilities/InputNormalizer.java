package calculator.utilities;

public class InputNormalizer {

    private InputNormalizer() { }

    public static String normalize(String expr) {
        if (expr == null) {
            return null;
        }

        return expr.replace("\u2295", "#")
                .replace("\u2299", "#=")
                .replace("\u2194", "~")
                .replace("\u2285", "$")
                .replace("\u2192", "=>")
                .replace("\u2284", ":")
                .replace("\u2190", "=<")
                .replace("\u2191", "!&")
                .replace("\u2193", "!|")
                .replaceAll("\\bmod\\b", "%");
    }
}
