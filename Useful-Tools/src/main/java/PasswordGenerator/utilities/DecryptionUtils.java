package passwordgenerator.utilities;

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

/**
 * FIX 1 (cipher mismatch): Now uses "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
 *   consistently with EncryptionUtils. The original mismatch (encrypt with
 *   PKCS1Padding, decrypt with OAEP) guaranteed a BadPaddingException on
 *   every decryption attempt.
 *
 * FIX 2 (Base64 decode): The encrypted password stored in the database is a
 *   Base64-encoded string. The original code passed encryptedPassword.getBytes()
 *   (i.e. the raw bytes of the Base64 text) to the cipher, which is wrong.
 *   The correct approach is to Base64-decode first to recover the actual
 *   ciphertext byte array, then pass that to cipher.doFinal().
 */
public class DecryptionUtils {

    private static final String RSA_CIPHER = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    public static String decryptEncryptedPassword(String encryptedPassword, String privateKeyString) {
        String decryptedPassword = "";
        try {
            // Reconstruct the PrivateKey object from its Base64-encoded PKCS8 form.
            byte[] pkcs8EncodedBytes = Base64.getDecoder().decode(privateKeyString);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8EncodedBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(keySpec);

            // FIX: Base64-decode the ciphertext before passing to the cipher.
            // The original passed encryptedPassword.getBytes(UTF-8) which are the
            // raw bytes of the Base64 string, not the actual cipher bytes.
            byte[] cipherBytes = Base64.getDecoder().decode(encryptedPassword);

            Cipher cipher = Cipher.getInstance(RSA_CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] plainBytes = cipher.doFinal(cipherBytes);
            decryptedPassword = new String(plainBytes, "UTF-8");

        } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException
                | InvalidKeyException | IllegalBlockSizeException | BadPaddingException
                | java.io.UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return decryptedPassword;
    }
}
