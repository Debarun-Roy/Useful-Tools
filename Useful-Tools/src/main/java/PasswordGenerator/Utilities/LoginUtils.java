package PasswordGenerator.Utilities;
import org.mindrot.jbcrypt.BCrypt;

public class LoginUtils {
	public static boolean verifyUser(String password, String storedHashPassword) {
		return BCrypt.checkpw(password, storedHashPassword);
	}
}