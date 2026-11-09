package it.unipi.ase.tamer.auth.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashIsBcryptAndNotThePassword() {
        String hash = hasher.hash("pikachu123");

        assertTrue(hash.startsWith("$2"));
        assertTrue(hash.contains("$12$"));
        assertNotEquals("pikachu123", hash);
        assertTrue(hasher.matches("pikachu123", hash));
        assertFalse(hasher.matches("pikachu124", hash));
    }

    @Test
    void passwordsLongerThan72BytesNeverMatch() {
        String hash = hasher.hash("a".repeat(72));

        assertFalse(hasher.matches("a".repeat(73), hash));
        assertTrue(PasswordHasher.exceedsMaxLength("è".repeat(37)));
        assertFalse(PasswordHasher.exceedsMaxLength("è".repeat(36)));
    }
}
