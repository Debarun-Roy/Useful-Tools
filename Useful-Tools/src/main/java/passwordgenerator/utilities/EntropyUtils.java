package passwordgenerator.utilities;

/**
 * EntropyUtils — calculates the information-theoretic entropy of a password.
 *
 * FORMULA:
 *   H = L × log₂(N)
 *   where L = password length, N = effective character-pool size.
 *
 * The character-pool size is estimated from the actual characters present
 * in the password rather than from the declared generator settings.  This
 * gives an accurate reading regardless of how the password was constructed.
 *
 * CHARACTER POOLS:
 *   Lowercase letters  a–z           26 characters
 *   Uppercase letters  A–Z           26 characters
 *   Digits             0–9           10 characters
 *   Special chars      printable     32 characters  (ASCII 33–126 minus alphanumeric)
 *
 * STRENGTH THRESHOLDS (industry-accepted guidelines):
 *   < 28 bits   — Very Weak  (trivially cracked)
 *   28–35 bits  — Weak
 *   36–59 bits  — Fair
 *   60–127 bits — Strong
 *   ≥ 128 bits  — Very Strong
 */
public class EntropyUtils {

    private EntropyUtils() { }

    // Pool sizes
    private static final int POOL_LOWER   = 26;
    private static final int POOL_UPPER   = 26;
    private static final int POOL_DIGIT   = 10;
    private static final int POOL_SPECIAL = 32;   // printable ASCII minus alphanumeric

    /**
     * Calculates the bit-entropy of the given password.
     *
     * @param password The plaintext password. Must not be null.
     * @return Entropy in bits, rounded to two decimal places.
     *         Returns 0.0 for a null or empty password.
     */
    public static double calculate(String password) {
        if (password == null || password.isEmpty()) return 0.0;

        int poolSize = effectivePoolSize(password);
        if (poolSize <= 1) return 0.0;

        double entropy = password.length() * (Math.log(poolSize) / Math.log(2));
        return Math.round(entropy * 100.0) / 100.0;
    }

    /**
     * Returns a human-readable strength label for the given entropy.
     *
     * @param entropyBits Entropy in bits (as returned by {@link #calculate}).
     * @return One of: "Very Weak", "Weak", "Fair", "Strong", "Very Strong".
     */
    public static String strengthLabel(double entropyBits) {
        if (entropyBits < 28)  return "Very Weak";
        if (entropyBits < 36)  return "Weak";
        if (entropyBits < 60)  return "Fair";
        if (entropyBits < 128) return "Strong";
        return "Very Strong";
    }

    /**
     * Returns the estimated effective character-pool size for the given password.
     * Adds the size of each character class that appears at least once.
     */
    private static int effectivePoolSize(String password) {
        boolean hasLower   = false;
        boolean hasUpper   = false;
        boolean hasDigit   = false;
        boolean hasSpecial = false;

        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if      (Character.isLowerCase(c))                hasLower   = true;
            else if (Character.isUpperCase(c))                hasUpper   = true;
            else if (Character.isDigit(c))                    hasDigit   = true;
            else if (c >= 33 && c <= 126)                     hasSpecial = true;
        }

        int pool = 0;
        if (hasLower)   pool += POOL_LOWER;
        if (hasUpper)   pool += POOL_UPPER;
        if (hasDigit)   pool += POOL_DIGIT;
        if (hasSpecial) pool += POOL_SPECIAL;
        return pool;
    }
}
