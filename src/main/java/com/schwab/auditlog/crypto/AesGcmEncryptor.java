package com.schwab.auditlog.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class AesGcmEncryptor {
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private final SecureRandom rnd = new SecureRandom();

    public static byte[] generateKey(int bits) {
        byte[] k = new byte[bits / 8];
        new SecureRandom().nextBytes(k);
        return k;
    }

    public String encrypt(byte[] key, String plaintext) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        rnd.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(out);
    }

    public String decrypt(byte[] key, String encryptedBase64) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(encryptedBase64);
        if (decoded.length < IV_LENGTH) {
            throw new IllegalArgumentException("Invalid ciphertext");
        }
        byte[] iv = java.util.Arrays.copyOfRange(decoded, 0, IV_LENGTH);
        byte[] ciphertext = java.util.Arrays.copyOfRange(decoded, IV_LENGTH, decoded.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
        byte[] plain = cipher.doFinal(ciphertext);
        return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
    }
}
