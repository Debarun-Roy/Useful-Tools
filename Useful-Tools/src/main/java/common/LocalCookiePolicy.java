package common;

/** Attribute transformation only; called exclusively after the local profile guard. */
public final class LocalCookiePolicy {
    private LocalCookiePolicy() { }

    public static String rewrite(String header) {
        String[] parts = header.split(";", -1);
        StringBuilder result = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            String attribute = parts[i].trim();
            String name = attribute.split("=", 2)[0].trim();
            if (!attribute.isEmpty() && !name.equalsIgnoreCase("Secure") && !name.equalsIgnoreCase("SameSite")) {
                result.append("; ").append(attribute);
            }
        }
        return result.append("; SameSite=Lax").toString();
    }
}
