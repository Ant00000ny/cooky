package com.mikaa404.cookie;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

public class ChromeCookie implements Cookie {
    private final String host;
    private final String name;
    private final String value;
    private final String path;
    
    private static String macOsCookiePassword;
    
    public ChromeCookie(String host, String name, byte[] encryptedValue, String path) {
        this.host = host;
        this.name = name;
        this.value = decryptEncryptedValue(encryptedValue);
        this.path = path;
    }
    
    @Override
    public String getHost() {
        return host;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getValue() {
        return this.value;
    }
    
    @Override
    public String getPath() {
        return path;
    }
    
    private String decryptEncryptedValue(byte[] encryptedValue) {
        final String password = getMacOsCookiePassword();
        try {
            return decrypt(encryptedValue, password);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException(String.format("Failed to decrypt cookies: %s", e.getMessage()));
        }
    }
    
    private static String decrypt(byte[] encryptedValue, String password) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        final byte[] salt = "saltysalt".getBytes();
        final int iterationCount = 1003;
        final int keyLength = 128;
        final byte[] iv = new byte[16];
        Arrays.fill(iv, (byte) ' ');
        
        
        byte[] aesKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                                .generateSecret(new PBEKeySpec(password.toCharArray(), salt, iterationCount, keyLength))
                                .getEncoded();
        
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(aesKey, "AES"),
                    new IvParameterSpec(iv));
        // remove "v10" prefix (since Chrome version v80, see https://stackoverflow.com/a/60423699)
        if (StringUtils.startsWith(new String(encryptedValue), "v10")) {
            encryptedValue = ArrayUtils.subarray(encryptedValue, 3, encryptedValue.length);
        }
        
        return new String(cipher.doFinal(encryptedValue));
    }
    
    /**
     * Retrieve the key to decrypt the {@code encrypted_value} column in sqlite cookie file. This method may prompt to ask for user password.
     */
    private String getMacOsCookiePassword() {
        if (macOsCookiePassword != null) {
            return macOsCookiePassword;
        }
        
        try (InputStream inputStream = Runtime.getRuntime()
                                               .exec(new String[]{"security", "find-generic-password", "-w", "-s", "Chrome Safe Storage"})
                                               .getInputStream()) {
            macOsCookiePassword = IOUtils.readLines(inputStream, StandardCharsets.UTF_8)
                                          .stream()
                                          .filter(s -> !StringUtils.contains(s, "security: SecKeychainSearchCopyNext: The specified item could not be found in the keychain."))
                                          .findFirst()
                                          .orElseThrow(() -> new RuntimeException("Failed to read keyring password. "));
            return macOsCookiePassword;
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed while try to get macOS key ring: %s", e.getMessage()));
        }
    }
}
