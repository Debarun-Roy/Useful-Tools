package passwordgenerator.utilities;

import org.mindrot.jbcrypt.BCrypt;

/**
 * FIX: The original HashingUtils used PBKDF2WithHmacSHA1 and discarded the
 * salt, making verification permanently impossible. LoginUtils.verifyUser()
 * uses BCrypt.checkpw(), which expects a BCrypt hash — so if HashingUtils
 * produced a PBKDF2 hash, login would never succeed for any user.
 *
 * This class now uses BCrypt consistently, which:
 *   1. Embeds the salt inside the hash string (no need to store salt separately).
 *   2. Is directly verifiable by LoginUtils.verifyUser()/BCrypt.checkpw().
 *   3. Is the industry-standard approach for password storage.
 *
 * The work factor (log rounds) is set to 12, which is a sensible default
 * as of current hardware. Increase it as hardware speeds improve.
 */
public class HashingUtils {

    private static final int BCRYPT_LOG_ROUNDS = 12;

    public static String generateHashedPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_LOG_ROUNDS));
    }
}
