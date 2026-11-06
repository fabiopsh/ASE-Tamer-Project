package it.unipi.ase.tamer.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class UsernameNormalizerTest {

    @Test
    void lowercasesAndTrims() {
        assertEquals("ash_99", UsernameNormalizer.normalize("  Ash_99 "));
    }

    @Test
    void usesRootLocale() {
        // In a Turkish locale "I".toLowerCase() would give a dotless i.
        assertEquals("misty", UsernameNormalizer.normalize("MISTY"));
    }

    @Test
    void nullStaysNull() {
        assertNull(UsernameNormalizer.normalize(null));
    }
}
