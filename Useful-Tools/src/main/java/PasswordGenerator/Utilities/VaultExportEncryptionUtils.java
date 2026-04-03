package passwordgenerator.utilities;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts vault-export JSON using an AES key derived from the user's stored
 * BCrypt hash.
 *
 * Sprint 9 note:
 *   The roadmap specifies the user's "BCrypt token" as the export key. BCrypt
 *   hashes are one-way values, so this utility derives an AES-256 key from the
 *   stored BCrypt hash plus a random salt. The exported file therefore remains
 *   opaque at rest and can be tied to the user's account secret material.
 */
public final class VaultExportEncryptionUtils {

    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int PBKDF2_ITERATIONS = 65_536;
    private static final int AES_KEY_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private VaultExportEncryptionUtils() { }

    public static LinkedHashMap<String, Object> encryptJson(String plainJson, String bcryptToken)
            throws Exception {

        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        SECURE_RANDOM.nextBytes(iv);

        SecretKey key = deriveKey(bcryptToken, salt);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plainJson.getBytes(StandardCharsets.UTF_8));

        LinkedHashMap<String, Object> encrypted = new LinkedHashMap<>();
        encrypted.put("algorithm", "AES-256-GCM");
        encrypted.put("kdf", "PBKDF2-HMAC-SHA256");
        encrypted.put("iterations", PBKDF2_ITERATIONS);
        encrypted.put("salt", Base64.getEncoder().encodeToString(salt));
        encrypted.put("iv", Base64.getEncoder().encodeToString(iv));
        encrypted.put("ciphertext", Base64.getEncoder().encodeToString(ciphertext));
        encrypted.put("keySource", "bcrypt-hash-derived");
        return encrypted;
    }

    private static SecretKey deriveKey(String bcryptToken, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(
                bcryptToken.toCharArray(),
                salt,
                PBKDF2_ITERATIONS,
                AES_KEY_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        spec.clearPassword();
        return new SecretKeySpec(keyBytes, "AES");
    }
}