package it.unipi.ase.tamers.players.player;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileRulesTest {
    @Test
    void acceptsLettersDigitsUnderscoreAndSpaces() {
        assertTrue(ProfileRules.validateDisplayName("Ash").isEmpty());
        assertTrue(ProfileRules.validateDisplayName("Ash Ketchum_99").isEmpty());
        assertTrue(ProfileRules.validateDisplayName("a".repeat(20)).isEmpty());
    }

    @Test
    void rejectsWrongLength() {
        assertFalse(ProfileRules.validateDisplayName("ab").isEmpty());
        assertFalse(ProfileRules.validateDisplayName("").isEmpty());
        assertFalse(ProfileRules.validateDisplayName("a".repeat(21)).isEmpty());
    }

    @Test
    void rejectsForbiddenCharacters() {
        assertFalse(ProfileRules.validateDisplayName("ash<script>").isEmpty());
        assertFalse(ProfileRules.validateDisplayName("ash-ketchum").isEmpty());
        assertFalse(ProfileRules.validateDisplayName("ash;drop").isEmpty());
        assertFalse(ProfileRules.validateDisplayName("città").isEmpty());
        assertFalse(ProfileRules.validateDisplayName("ash\nkey").isEmpty());
    }

    @Test
    void rejectsNull() {
        assertFalse(ProfileRules.validateDisplayName(null).isEmpty());
    }

    @Test
    void normalizeTrimsSurroundingWhitespace() {
        assertEquals("Ash", ProfileRules.normalizeName("  Ash  "));
        assertNull(ProfileRules.normalizeName(null));
        // a name made only of spaces becomes empty and is rejected
        assertFalse(ProfileRules.validateDisplayName(ProfileRules.normalizeName("     ")).isEmpty());
        // trimming can bring a too long name back into range
        assertTrue(ProfileRules.validateDisplayName(ProfileRules.normalizeName(" " + "a".repeat(20) + " ")).isEmpty());
    }

    @Test
    void avatarMustBeAnOwnedSpecies() {
        List<Integer> owned = List.of(3, 12, 12, 40);
        assertTrue(ProfileRules.validateAvatar(12, owned).isEmpty());
        assertEquals(1, ProfileRules.validateAvatar(13, owned).size());
        assertEquals(1, ProfileRules.validateAvatar(12, List.of()).size());
    }
}
