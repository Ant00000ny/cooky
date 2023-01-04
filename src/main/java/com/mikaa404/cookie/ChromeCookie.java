package com.mikaa404.cookie;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

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
        // remove "v10" prefix of encrypted value (see https://stackoverflow.com/a/60423699)
        if (Arrays.equals(ArrayUtils.subarray(encryptedValue, 0, 3), "v10".getBytes())) {
            encryptedValue = ArrayUtils.subarray(encryptedValue, 3, encryptedValue.length);
        }
        this.value = decryptEncryptedValue(encryptedValue);
        this.path = path;
    }
    
    @Override
    public String getHost() {
        return this.host;
    }
    
    @Override
    public String getName() {
        return this.name;
    }
    
    @Override
    public String getValue() {
        return this.value;
    }
    
    @Override
    public String getPath() {
        return this.path;
    }
    
    /**
     * Since Chrome version v80, value of cookies stored in Chrome is encrypted with AES. This method will try to access
     * system keyring and may prompt for user login password to get the PBE password to get AES key then decrypt cookies
     * values.
     *
     * @param encryptedValue encrypted value stored in Chrome `Cookies` file.
     * @return decrypted value.
     */
    private String decryptEncryptedValue(byte[] encryptedValue) {
        if (SystemUtils.IS_OS_MAC) {
            final String password = getMacOsCookiePassword();
            try {
                return decrypt(encryptedValue, password);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException | InvalidKeyException |
                     InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
//                String stackTrace = ExceptionUtils.getStackTrace(e);
                throw new RuntimeException(String.format("Failed to decrypt cookies: %s", e.getMessage()));
            }
        } else {
            // TODO: support more OS
            // TODO: refactor project with interface "CookieDecrypter" and impl classes like "MacOsCookieDecrypter", then use as "ChromeBrowser.decrypt(MacOsCookieDecrypter)"
            throw new RuntimeException("OS is not supported. ");
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
        
        return new String(cipher.doFinal(encryptedValue));
    }
    
    /**
     * Retrieve the key to decrypt the {@code encrypted_value} column in sqlite cookie file. This method may prompt to
     * ask for user password.
     */
    private String getMacOsCookiePassword() {
        if (macOsCookiePassword != null) {
            return macOsCookiePassword;
        }
        
        synchronized (ChromeCookie.class) {
            if (macOsCookiePassword != null) {
                return macOsCookiePassword;
            }
            
            try (InputStream inputStream = Runtime.getRuntime()
                                                   // use exec(String[]) rather than exec(String). The former supports spaces in args while the latter not.
                                                   .exec(new String[]{"security", "find-generic-password", "-w", "-s", "Chrome Safe Storage"})
                                                   .getInputStream()) {
                macOsCookiePassword = IOUtils.readLines(inputStream, StandardCharsets.UTF_8)
                                              .stream()
                                              // TODO: find a better way to identify fail message (subprocess exit value?)
                                              .filter(s -> !StringUtils.contains(s, "security: SecKeychainSearchCopyNext: The specified item could not be found in the keychain."))
                                              .findFirst()
                                              .orElseThrow(() -> new RuntimeException("Failed to read keyring password. "));
                return macOsCookiePassword;
            } catch (IOException e) {
                throw new RuntimeException(String.format("Failed while try to get macOS key ring: %s", e.getMessage()));
            }
        }
    }
}
