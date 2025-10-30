package PasswordGenerator.Models;

import java.sql.Timestamp;
import java.time.Instant;

public class PasswordModel {
	private int password_id;
	private String username;
	private String platform;
	private String encryptedPassword;
	private String hashedPassword;
	private Instant createdDate;
	private String password;
	private int numberCount;
	private int specialCharactercount;
	private int lowercaseCount;
	private int uppercaseCount;
	private Timestamp generatedTimestamp;
	private String privateKey;
	
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public int getNumberCount() {
		return numberCount;
	}
	public void setNumberCount(int numberCount) {
		this.numberCount = numberCount;
	}
	public int getSpecialCharactercount() {
		return specialCharactercount;
	}
	public void setSpecialCharactercount(int specialCharactercount) {
		this.specialCharactercount = specialCharactercount;
	}
	public int getLowercaseCount() {
		return lowercaseCount;
	}
	public void setLowercaseCount(int lowercaseCount) {
		this.lowercaseCount = lowercaseCount;
	}
	public int getUppercaseCount() {
		return uppercaseCount;
	}
	public void setUppercaseCount(int uppercaseCount) {
		this.uppercaseCount = uppercaseCount;
	}
	public Timestamp getGeneratedTimestamp() {
		return generatedTimestamp;
	}
	public void setGeneratedTimestamp(Timestamp generatedTimestamp) {
		this.generatedTimestamp = generatedTimestamp;
	}
	
	public int getPassword_id() {
		return password_id;
	}
	public void setPassword_id(int password_id) {
		this.password_id = password_id;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getPlatform() {
		return platform;
	}
	public void setPlatform(String platform) {
		this.platform = platform;
	}
	public String getHashedPassword() {
		return hashedPassword;
	}
	public void setHashedPassword(String hashedPassword) {
		this.hashedPassword = hashedPassword;
	}
	public Instant getCreatedDate() {
		return createdDate;
	}
	public void setCreatedDate(Instant createdDate) {
		this.createdDate = createdDate;
	}
	public String getEncryptedPassword() {
		return encryptedPassword;
	}
	public void setEncryptedPassword(String encryptedPassword) {
		this.encryptedPassword = encryptedPassword;
	}
	public String getPrivateKey() {
		return privateKey;
	}
	public void setPrivateKey(String privateKey) {
		this.privateKey = privateKey;
	}
}
