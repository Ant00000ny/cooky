package com.mikaa404.cookie;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.platform.win32.Crypt32Util;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public class ChromeCookie implements ICookie {
    private final String hostKey;
    private final String name;
    private final String value;
    private final String path;
    private final long creationUtc;
    private final String topFrameSiteKey;
    private final long expiresUtc;
    private final boolean isSecure;
    private final boolean isHttpOnly;
    private final long lastAccessUtc;
    private final boolean hasExpires;
    private final boolean isPersistent;
    private final int priority;
    private final int sameSite;
    private final int sourceScheme;
    private final int sourcePort;
    private final boolean isSameParty;
    private final long lastUpdateUtc;
    
    private static String macOsCookiePassword;
    private static byte[] windowsMasterKey;
    
    public ChromeCookie(String hostKey,
                        String name,
                        byte[] encryptedValue,
                        String path,
                        long creationUtc,
                        String topFrameSiteKey,
                        long expiresUtc,
                        boolean isSecure,
                        boolean isHttpOnly,
                        long lastAccessUtc,
                        boolean hasExpires,
                        boolean isPersistent,
                        int priority,
                        int sameSite,
                        int sourceScheme,
                        int sourcePort,
                        boolean isSameParty,
                        long lastUpdateUtc) {
        // remove "v10" prefix of encrypted value (see https://stackoverflow.com/a/60423699)
        if (Arrays.equals(ArrayUtils.subarray(encryptedValue, 0, 3), "v10".getBytes())) {
            encryptedValue = ArrayUtils.subarray(encryptedValue, 3, encryptedValue.length);
        }
        
        this.hostKey = hostKey;
        this.name = name;
        this.value = decryptEncryptedValue(encryptedValue);
        this.path = path;
        this.creationUtc = creationUtc;
        this.topFrameSiteKey = topFrameSiteKey;
        this.expiresUtc = expiresUtc;
        this.isSecure = isSecure;
        this.isHttpOnly = isHttpOnly;
        this.lastAccessUtc = lastAccessUtc;
        this.hasExpires = hasExpires;
        this.isPersistent = isPersistent;
        this.priority = priority;
        this.sameSite = sameSite;
        this.sourceScheme = sourceScheme;
        this.sourcePort = sourcePort;
        this.isSameParty = isSameParty;
        this.lastUpdateUtc = lastUpdateUtc;
    }
    
    @Override
    public String getHostKey() {
        return hostKey;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getValue() {
        return value;
    }
    
    @Override
    public String getPath() {
        return path;
    }
    
    public long getCreationUtc() {
        return creationUtc;
    }
    
    public String getTopFrameSiteKey() {
        return topFrameSiteKey;
    }
    
    public long getExpiresUtc() {
        return expiresUtc;
    }
    
    public boolean isSecure() {
        return isSecure;
    }
    
    public boolean isHttpOnly() {
        return isHttpOnly;
    }
    
    public long getLastAccessUtc() {
        return lastAccessUtc;
    }
    
    public boolean isHasExpires() {
        return hasExpires;
    }
    
    public boolean isPersistent() {
        return isPersistent;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public int getSameSite() {
        return sameSite;
    }
    
    public int getSourceScheme() {
        return sourceScheme;
    }
    
    public int getSourcePort() {
        return sourcePort;
    }
    
    public boolean isSameParty() {
        return isSameParty;
    }
    
    public long getLastUpdateUtc() {
        return lastUpdateUtc;
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
            try {
                final String password = getMacOsCookiePassword();
                return decryptMacOs(encryptedValue, password);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException | InvalidKeyException |
                     InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
                throw new RuntimeException("Failed to decrypt cookies: %s", e);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else if (SystemUtils.IS_OS_WINDOWS) {
            try {
                final byte[] windowsMasterKey = getWindowsMasterKey();
                return decryptWindows(encryptedValue, windowsMasterKey);
            } catch (InvalidAlgorithmParameterException | NoSuchPaddingException | IllegalBlockSizeException |
                     NoSuchAlgorithmException | BadPaddingException | InvalidKeyException | IOException e) {
                throw new RuntimeException("Failed to decrypt cookies: ", e);
            }
        } else {
            // TODO: support more OS
            // TODO: refactor project with interface "CookieDecrypter" and impl classes like "MacOsCookieDecrypter", then use as "ChromeBrowser.decrypt(MacOsCookieDecrypter)"
            throw new RuntimeException("OS is not supported. ");
        }
    }
    
    private String decryptWindows(byte[] encryptedValue, byte[] windowsMasterKey) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        final byte[] nonce = ArrayUtils.subarray(encryptedValue, 3, 15);
        final byte[] cipherTextTag = ArrayUtils.subarray(encryptedValue, 15, encryptedValue.length);
        final int tagLength = 128;
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(windowsMasterKey, "AES"),
                    new GCMParameterSpec(tagLength, nonce));
        
        return new String(cipher.doFinal(cipherTextTag));
    }
    
    private static String decryptMacOs(byte[] encryptedValue, String password) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
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
    private String getMacOsCookiePassword() throws InterruptedException, IOException {
        if (macOsCookiePassword != null) {
            return macOsCookiePassword;
        }
        
        synchronized (ChromeCookie.class) {
            if (macOsCookiePassword != null) {
                return macOsCookiePassword;
            }
            
            Process process = Runtime.getRuntime()
                                      // use exec(String[]) rather than exec(String). The former supports spaces in args while the latter not.
                                      .exec(new String[]{"security", "find-generic-password", "-w", "-s", "Chrome Safe Storage"});
            
            boolean exited = process.waitFor(60, TimeUnit.SECONDS);
            if (!exited || process.exitValue() != 0) {
                throw new RuntimeException("Failed to read keyring password. ");
            }
            
            
            try (InputStream inputStream = process.getInputStream()) {
                macOsCookiePassword = IOUtils.readLines(inputStream, StandardCharsets.UTF_8)
                                              .stream()
                                              .findFirst()
                                              .orElseThrow(() -> new RuntimeException("Failed to read keyring password. "));
                return macOsCookiePassword;
            } catch (IOException e) {
                throw new RuntimeException("Failed while try to get macOS key ring: %s", e);
            }
        }
    }
    
    /**
     * See <a href="https://stackoverflow.com/a/65953409/1631104">https://stackoverflow.com/a/65953409/1631104</a>
     */
    private byte[] getWindowsMasterKey() throws IOException {
        
        if (windowsMasterKey != null) {
            return windowsMasterKey;
        }
        
        synchronized (ChromeCookie.class) {
            if (windowsMasterKey != null) {
                return windowsMasterKey;
            }
            
            final Path localStatePath = Paths.get(SystemUtils.USER_HOME, "AppData", "Local", "Google", "Chrome", "User Data", "Local State");
            String encryptedMasterKeyWithPrefixBase64 = new ObjectMapper()
                                                                .readTree(localStatePath.toFile())
                                                                .get("os_crypt")
                                                                .get("encrypted_key")
                                                                .asText();
            
            // Remove prefix (DPAPI)
            byte[] encryptedMasterKeyWithPrefix = Base64.getDecoder().decode(encryptedMasterKeyWithPrefixBase64);
            byte[] encryptedMasterKey = Arrays.copyOfRange(encryptedMasterKeyWithPrefix, 5, encryptedMasterKeyWithPrefix.length);
            
            // Decrypt and store the master key for use later
            windowsMasterKey = Crypt32Util.cryptUnprotectData(encryptedMasterKey);
            return windowsMasterKey;
        }
    }
}
