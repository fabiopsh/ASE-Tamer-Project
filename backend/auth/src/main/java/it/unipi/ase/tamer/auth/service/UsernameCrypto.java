package it.unipi.ase.tamer.auth.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Encrypts and decrypts normalized usernames at rest using AES-256-GCM with a random 12-byte IV
 * prepended to the ciphertext (AUTH-8).
 */
@ApplicationScoped
public class UsernameCrypto {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;
    private static final int KEY_BYTES = 32;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public UsernameCrypto(@ConfigProperty(name = "tamer.crypto.username-enc-key") String hexKey) {
        if (hexKey == null || hexKey.isBlank()) {
            throw new IllegalStateException("tamer.crypto.username-enc-key must be configured");
        }
        byte[] decoded;
        try {
            decoded = HexFormat.of().parseHex(hexKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("tamer.crypto.username-enc-key must be valid hex", e);
        }
        if (decoded.length != KEY_BYTES) {
            throw new IllegalStateException("tamer.crypto.username-enc-key must be 64 hex characters (32 bytes)");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public byte[] encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext username must not be null");
        }
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, out, IV_LENGTH, ciphertext.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt username", e);
        }
    }

    public String decrypt(byte[] ivAndCiphertext) {
        if (ivAndCiphertext == null || ivAndCiphertext.length < IV_LENGTH + TAG_BYTES) {
            throw new IllegalStateException("Encrypted username payload is malformed");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, ivAndCiphertext, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(ivAndCiphertext, IV_LENGTH, ivAndCiphertext.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt username", e);
        }
    }
}
