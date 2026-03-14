package passwordgenerator.utilities;

import java.security.InvalidKeyException;
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

/**
 * FIX: The original used Cipher.getInstance("RSA") which defaults to
 * "RSA/ECB/PKCS1Padding". DecryptionUtils used "RSA/ECB/OAEPWithSHA-256AndMGF1Padding".
 * These two padding schemes are incompatible — encryption with one cannot be
 * decrypted with the other, causing BadPaddingException on every decryption.
 *
 * Both classes now use "RSA/ECB/OAEPWithSHA-256AndMGF1Padding", which is also
 * the more secure choice over PKCS1Padding.
 */
public class EncryptionUtils {

    private static final String RSA_CIPHER = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    /**
     * Encrypts the given password with a freshly generated RSA-2048 key pair.
     * Returns a map containing:
     *   "encrypted_password" — Base64-encoded ciphertext
     *   "private_key"        — Base64-encoded PKCS8 private key (needed for decryption)
     */
    public static LinkedHashMap<String, String> generateEncryptedPassword(String password) {
        LinkedHashMap<String, String> encryptionDetails = new LinkedHashMap<>();
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair pair = keyGen.generateKeyPair();

            PublicKey publicKey = pair.getPublic();
            PrivateKey privateKey = pair.getPrivate();

            Cipher cipher = Cipher.getInstance(RSA_CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] cipherBytes = cipher.doFinal(password.getBytes("UTF-8"));

            String encryptedPassword = Base64.getEncoder().encodeToString(cipherBytes);
            String privateKeyString = Base64.getEncoder().encodeToString(privateKey.getEncoded());

            encryptionDetails.put("encrypted_password", encryptedPassword);
            encryptionDetails.put("private_key", privateKeyString);

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                | BadPaddingException | IllegalBlockSizeException
                | java.io.UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return encryptionDetails;
    }
}
