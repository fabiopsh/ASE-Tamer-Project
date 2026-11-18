package it.unipi.ase.tamer.auth.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** SEC-3, P-27: the blind index is stable, keyed, and never the username itself. */
class UsernameBlindIndexTest {

    private static final String KEY = "k".repeat(64);
    private final UsernameBlindIndex index = new UsernameBlindIndex(KEY);

    @Test
    void isSixtyFourHexCharactersAndNotTheUsername() {
        String value = index.of("ash_ketchum");

        assertTrue(value.matches("[0-9a-f]{64}"), value);
        assertFalse(value.contains("ash"));
    }

    @Test
    void usesTheSameNormalizationAsTheLogin() {
        assertEquals(index.of("ash_ketchum"), index.of("  ASH_Ketchum "));
    }

    @Test
    void differentUsernamesGiveDifferentValues() {
        assertNotEquals(index.of("ash_ketchum"), index.of("misty"));
    }

    @Test
    void dependsOnTheKey() {
        assertNotEquals(index.of("ash_ketchum"), new UsernameBlindIndex("j".repeat(64)).of("ash_ketchum"));
    }

    @Test
    void aUsernameThatCannotExistIsRecordedAsAQuestionMark() {
        assertEquals("?", index.of(null));
        assertEquals("?", index.of("ab"));
        assertEquals("?", index.of("a".repeat(21)));
        assertEquals("?", index.of("ada\n{\"user\":\"root\"}"));
        assertEquals("?", index.of("' OR 1=1 --"));
    }

    @Test
    void aShortKeyIsRefused() {
        assertThrows(IllegalStateException.class, () -> new UsernameBlindIndex("short"));
        assertThrows(IllegalStateException.class, () -> new UsernameBlindIndex(null));
    }
}
