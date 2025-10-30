package PasswordGenerator.Utilities;

import java.security.KeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class EncryptionUtils {
	
	public static LinkedHashMap<String, String> generateEncryptedPassword(String password) {
		LinkedHashMap<String, String> encryptionDetails = new LinkedHashMap<>();
		String encryptedPassword = "";
		try {
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
			keyGen.initialize(2048);
			KeyPair pair = keyGen.generateKeyPair();
			
			PublicKey publicKey = pair.getPublic();
			PrivateKey privateKey = pair.getPrivate();
			Cipher cipher = Cipher.getInstance("RSA");
			cipher.init(Cipher.ENCRYPT_MODE, publicKey);
			byte[] byteStreamPassword = cipher.doFinal(password.getBytes());
			byte[] privateKeyBytes = privateKey.getEncoded();
			String privateKeyString = Base64.getEncoder().encodeToString(privateKeyBytes);
			encryptedPassword = Base64.getEncoder().encodeToString(byteStreamPassword);
			encryptionDetails.put("encrypted_password", encryptedPassword);
			encryptionDetails.put("private_key", privateKeyString);
		}
		catch(NoSuchAlgorithmException nsae) {
			nsae.printStackTrace();
		}
		catch(NoSuchPaddingException nspe) {
			nspe.printStackTrace();
		}
		catch(KeyException ike) {
			ike.printStackTrace();
		}
		catch(BadPaddingException bpe) {
			bpe.printStackTrace();
		}
		catch(IllegalBlockSizeException ibse) {
			ibse.printStackTrace();
		}
		return encryptionDetails;
	}
	

}
