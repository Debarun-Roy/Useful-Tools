package PasswordGenerator.Utilities;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class DecryptionUtils {
	
	public static String decryptEncryptedPassword(String encryptedPassword, String privateKeyString) {
		String decryptedPassword = "";
		try {
			byte[] pkcs8EncodedBytes = Base64.getDecoder().decode(privateKeyString);
	        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8EncodedBytes);
	        KeyFactory kf = KeyFactory.getInstance("RSA");
	        PrivateKey privateKey = kf.generatePrivate(keySpec);
	        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
	        cipher.init(Cipher.DECRYPT_MODE, privateKey);
	        byte[] encryptedPasswordByteStream = encryptedPassword.getBytes(StandardCharsets.UTF_8);
	        byte[] byteStreamPassword = cipher.doFinal(encryptedPasswordByteStream);
	        decryptedPassword = new String(byteStreamPassword, "UTF-8");
		}
		catch(NoSuchAlgorithmException nsae) {
			nsae.printStackTrace();
		} 
		catch (InvalidKeySpecException ikse) {
			ikse.printStackTrace();
		} 
		catch (NoSuchPaddingException nspe) {
			nspe.printStackTrace();
		}
		catch (InvalidKeyException ike) {
			ike.printStackTrace();
		}
		catch (IllegalBlockSizeException ibse) {
			ibse.printStackTrace();
		} 
		catch (BadPaddingException bpe) {
			bpe.printStackTrace();
		} 
		catch (UnsupportedEncodingException uee) {
			uee.printStackTrace();
		}
		return decryptedPassword;
	}
}
