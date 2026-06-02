package com.vulkantechtt.konvo.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM authenticated encryption for column-level secrets (channel
 * app_secret, channel access_token). The wire format is:
 *
 *   "enc:v1:" + base64( IV(12 bytes) || ciphertext || GCM tag(16 bytes) )
 *
 * Anything without the {@code enc:v1:} prefix is treated as legacy plaintext
 * on read and re-encrypted on the next write — so the M3 channels survive
 * the M8 cutover without a Flyway migration walking the table.
 *
 * Key comes from {@code konvo.security.crypto-key} (base64-encoded 32-byte
 * value). The dev/test profiles ship a placeholder; production MUST set
 * {@code KONVO_CRYPTO_KEY} to a real key.
 */
@Component
public class CryptoService {

    public static final String PREFIX = "enc:v1:";
    private static final String CIPHER_TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(@Value("${konvo.security.crypto-key}") String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "konvo.security.crypto-key is not valid base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException(
                    "konvo.security.crypto-key must decode to exactly 32 bytes (AES-256); got " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    /** Returns null for null input. Any non-null input is encrypted unconditionally. */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * Returns plaintext. Treats a value without the {@code enc:v1:} prefix as
     * legacy unencrypted data and returns it verbatim — see class javadoc.
     */
    public String decrypt(String stored) {
        if (stored == null) return null;
        if (!stored.startsWith(PREFIX)) return stored;
        try {
            byte[] blob = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (blob.length <= IV_LENGTH) {
                throw new IllegalStateException("Ciphertext blob too short");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] ct = new byte[blob.length - IV_LENGTH];
            System.arraycopy(blob, 0, iv, 0, IV_LENGTH);
            System.arraycopy(blob, IV_LENGTH, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
