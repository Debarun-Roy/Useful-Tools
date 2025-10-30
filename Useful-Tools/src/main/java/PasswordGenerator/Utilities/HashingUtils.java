package PasswordGenerator.Utilities;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class HashingUtils {
	
	public static String generateHashedPassword(String password) {
		String hashedPassword = "";
		try {
			SecureRandom random = new SecureRandom();
			byte[] salt = new byte[16];
			random.nextBytes(salt);
			KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 128);
			SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
			byte[] hash = f.generateSecret(spec).getEncoded();
			Base64.Encoder enc = Base64.getEncoder();
			
//			System.out.printf("salt: %s%n", enc.encodeToString(salt));
//			System.out.printf("hash: %s%n", enc.encodeToString(hash));
			
			hashedPassword = enc.encodeToString(hash);
		}
		catch(NoSuchAlgorithmException nsae) {
			nsae.printStackTrace();
		}
		catch (InvalidKeySpecException ikse) {
			ikse.printStackTrace();
		}
		return hashedPassword;
	}
}
