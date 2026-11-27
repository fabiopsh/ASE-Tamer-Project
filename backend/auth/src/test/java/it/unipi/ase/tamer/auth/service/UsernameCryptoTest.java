package it.unipi.ase.tamer.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** AUTH-8: AES-256-GCM encryption at rest for normalized usernames. */
class UsernameCryptoTest {

    private static final String KEY_1 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String KEY_2 = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";

    private final UsernameCrypto crypto = new UsernameCrypto(KEY_1);

    @Test
    void encryptAndDecryptRoundtrip() {
        byte[] encrypted = crypto.encrypt("ash_ketchum");

        assertEquals("ash_ketchum", crypto.decrypt(encrypted));
    }

    @Test
    void randomIvProducesDifferentCiphertextsForSamePlaintext() {
        byte[] first = crypto.encrypt("ash_ketchum");
        byte[] second = crypto.encrypt("ash_ketchum");

        assertFalse(Arrays.equals(first, second));
        assertFalse(Arrays.equals(Arrays.copyOf(first, 12), Arrays.copyOf(second, 12)));
        assertEquals("ash_ketchum", crypto.decrypt(first));
        assertEquals("ash_ketchum", crypto.decrypt(second));
    }

    @Test
    void tamperedCiphertextOrIvFailsDecryption() {
        byte[] encrypted = crypto.encrypt("ash_ketchum");

        byte[] flippedCipher = encrypted.clone();
        flippedCipher[flippedCipher.length - 1] ^= 0x01;
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(flippedCipher));

        byte[] flippedIv = encrypted.clone();
        flippedIv[0] ^= 0x01;
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(flippedIv));

        assertThrows(IllegalStateException.class, () -> crypto.decrypt(new byte[10]));
    }

    @Test
    void wrongKeyFailsDecryption() {
        byte[] encrypted = crypto.encrypt("ash_ketchum");
        UsernameCrypto other = new UsernameCrypto(KEY_2);

        assertThrows(IllegalStateException.class, () -> other.decrypt(encrypted));
    }

    @Test
    void invalidKeysAreRejected() {
        assertThrows(IllegalStateException.class, () -> new UsernameCrypto(null));
        assertThrows(IllegalStateException.class, () -> new UsernameCrypto(""));
        assertThrows(IllegalStateException.class, () -> new UsernameCrypto("abcd"));
        assertThrows(IllegalStateException.class, () -> new UsernameCrypto("z".repeat(64)));
    }
}
