package passwordgenerator.models;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * FIX (package): Renamed from PasswordGenerator.Models to passwordgenerator.models.
 *
 * FIX (naming): Field "specialCharactercount" (lowercase 'c') corrected to
 *   "specialCharacterCount" (uppercase 'C') for consistent Java camelCase.
 *   The corresponding getter/setter are also renamed:
 *     getSpecialCharactercount() → getSpecialCharacterCount()
 *     setSpecialCharactercount() → setSpecialCharacterCount()
 *   All callers (UserPasswordDAO, PasswordGenerationController) updated accordingly.
 */
public class PasswordModel {

    private int passwordId;
    private String username;
    private String platform;
    private String encryptedPassword;
    private String hashedPassword;
    private Instant createdDate;
    private String password;
    private int numberCount;
    private int specialCharacterCount;   // FIX: was specialCharactercount
    private int lowercaseCount;
    private int uppercaseCount;
    private Timestamp generatedTimestamp;
    private String privateKey;

    public int getPasswordId() { return passwordId; }
    public void setPasswordId(int passwordId) { this.passwordId = passwordId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getEncryptedPassword() { return encryptedPassword; }
    public void setEncryptedPassword(String encryptedPassword) { this.encryptedPassword = encryptedPassword; }

    public String getHashedPassword() { return hashedPassword; }
    public void setHashedPassword(String hashedPassword) { this.hashedPassword = hashedPassword; }

    public Instant getCreatedDate() { return createdDate; }
    public void setCreatedDate(Instant createdDate) { this.createdDate = createdDate; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getNumberCount() { return numberCount; }
    public void setNumberCount(int numberCount) { this.numberCount = numberCount; }

    public int getSpecialCharacterCount() { return specialCharacterCount; }
    public void setSpecialCharacterCount(int specialCharacterCount) { this.specialCharacterCount = specialCharacterCount; }

    public int getLowercaseCount() { return lowercaseCount; }
    public void setLowercaseCount(int lowercaseCount) { this.lowercaseCount = lowercaseCount; }

    public int getUppercaseCount() { return uppercaseCount; }
    public void setUppercaseCount(int uppercaseCount) { this.uppercaseCount = uppercaseCount; }

    public Timestamp getGeneratedTimestamp() { return generatedTimestamp; }
    public void setGeneratedTimestamp(Timestamp generatedTimestamp) { this.generatedTimestamp = generatedTimestamp; }

    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
}
