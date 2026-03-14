package passwordgenerator.utilities;

import org.mindrot.jbcrypt.BCrypt;

/**
 * FIX (package): Renamed from PasswordGenerator.Utilities to passwordgenerator.utilities.
 * Logic is correct — BCrypt.checkpw() is the right companion to BCrypt.hashpw()
 * in the updated HashingUtils.
 */
public class LoginUtils {

    public static boolean verifyUser(String password, String storedHashPassword) {
        return BCrypt.checkpw(password, storedHashPassword);
    }
}
