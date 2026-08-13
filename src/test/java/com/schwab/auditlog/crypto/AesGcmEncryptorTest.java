package com.schwab.auditlog.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmEncryptorTest {

    @Test
    void roundtrip() throws Exception {
        byte[] key = AesGcmEncryptor.generateKey(256);
        AesGcmEncryptor enc = new AesGcmEncryptor();
        String plaintext = "hello world";
        String ct = enc.encrypt(key, plaintext);
        String pt = enc.decrypt(key, ct);
        assertThat(pt).isEqualTo(plaintext);
    }

    @Test
    void tamperDetects() throws Exception {
        byte[] key = AesGcmEncryptor.generateKey(256);
        AesGcmEncryptor enc = new AesGcmEncryptor();
        String plaintext = "sensitive";
        String ct = enc.encrypt(key, plaintext);
        byte[] decoded = Base64.getDecoder().decode(ct);
        // flip a byte in ciphertext (after IV)
        if (decoded.length > 15) {
            decoded[15] ^= 0xFF;
        }
        String tampered = Base64.getEncoder().encodeToString(decoded);
        assertThatThrownBy(() -> enc.decrypt(key, tampered)).isInstanceOf(javax.crypto.AEADBadTagException.class);
    }

    @Test
    void simulateEcbMigration() throws Exception {
        // simulate legacy ECB ciphertext then migrate to GCM
        byte[] key = AesGcmEncryptor.generateKey(256);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher ecb = Cipher.getInstance("AES/ECB/PKCS5Padding");
        ecb.init(Cipher.ENCRYPT_MODE, keySpec);
        String plaintext = "{\"acct\":123, \"secret\":\"s\"}";
        byte[] ecbCt = ecb.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String ecbBase64 = Base64.getEncoder().encodeToString(ecbCt);

        // migration: decrypt ECB then re-encrypt with GCM
        Cipher ecbDec = Cipher.getInstance("AES/ECB/PKCS5Padding");
        ecbDec.init(Cipher.DECRYPT_MODE, keySpec);
        String original = new String(ecbDec.doFinal(Base64.getDecoder().decode(ecbBase64)), java.nio.charset.StandardCharsets.UTF_8);

        AesGcmEncryptor enc = new AesGcmEncryptor();
        String migrated = enc.encrypt(key, original);
        String after = enc.decrypt(key, migrated);
        assertThat(after).isEqualTo(original);
    }
}
