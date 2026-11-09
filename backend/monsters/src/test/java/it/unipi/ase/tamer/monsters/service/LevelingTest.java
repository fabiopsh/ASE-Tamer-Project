package it.unipi.ase.tamer.monsters.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LevelingTest {

    @Test
    void reachesSeveralLevelsInOneGrant() {
        // 15 XP -> level 2; 30 XP -> level 3; 45 XP -> level 4.
        assertEquals(new Leveling.State(4, 10), Leveling.grant(1, 0, 100));
    }

    @Test
    void keepsPartialXpUntilThreshold() {
        assertEquals(new Leveling.State(2, 6), Leveling.grant(1, 9, 12));
        assertEquals(30, Leveling.xpToNextLevel(2));
    }

    @Test
    void capsAtLevelFiftyAndClearsXp() {
        assertEquals(new Leveling.State(50, 0), Leveling.grant(49, 734, 500));
        assertEquals(new Leveling.State(50, 0), Leveling.grant(50, 0, 999));
        assertEquals(0, Leveling.xpToNextLevel(50));
    }

    @Test
    void rejectsNegativeXp() {
        assertThrows(IllegalArgumentException.class, () -> Leveling.grant(1, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> Leveling.grant(51, 0, 0));
    }
}
