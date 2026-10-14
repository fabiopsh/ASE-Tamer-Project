package it.unipi.ase.tamers.monsters.catalog;

import static org.junit.jupiter.api.Assertions.*;

import it.unipi.ase.tamers.monsters.catalog.Growth.Progress;
import org.junit.jupiter.api.Test;

class GrowthTest {

    @Test
    void levelOneStatEqualsBase() {
        assertEquals(38, Growth.effectiveStat(38, 1.5, 1));
    }

    @Test
    void effectiveStatAddsGrowthPerLevel() {
        // 62 + 2.3 * 4 = 71.2
        assertEquals(71, Growth.effectiveStat(62, 2.3, 5));
        // 31 + 1.1 * 49 = 84.9
        assertEquals(85, Growth.effectiveStat(31, 1.1, 50));
    }

    @Test
    void halfRoundsUpDespiteFloatingPointNoise() {
        // 38 + 1.5 * 1 = 39.5 -> 40
        assertEquals(40, Growth.effectiveStat(38, 1.5, 2));
        // 10 + 0.3 * 5 = 11.5 exactly in decimal -> 12
        assertEquals(12, Growth.effectiveStat(10, 0.3, 6));
        assertEquals(10, Growth.effectiveStat(10, 0.0, 30));
    }

    @Test
    void invalidLevelIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Growth.effectiveStat(10, 1.0, 0));
        assertThrows(IllegalArgumentException.class, () -> Growth.xpToNext(0));
    }

    @Test
    void xpThresholdIs25TimesLevel() {
        assertEquals(25, Growth.xpToNext(1));
        assertEquals(75, Growth.xpToNext(3));
        assertEquals(1250, Growth.xpToNext(50));
    }

    @Test
    void displayedXpToNextIsZeroAtMaxLevel() {
        assertEquals(75, Growth.xpToNextForDisplay(3));
        assertEquals(0, Growth.xpToNextForDisplay(Growth.MAX_LEVEL));
    }

    @Test
    void xpBelowThresholdOnlyAccumulates() {
        assertEquals(new Progress(1, 24), Growth.applyXp(1, 0, 24));
        assertEquals(new Progress(3, 30), Growth.applyXp(3, 10, 20));
    }

    @Test
    void reachingTheThresholdLevelsUpAndKeepsTheRemainder() {
        assertEquals(new Progress(2, 0), Growth.applyXp(1, 0, 25));
        assertEquals(new Progress(2, 5), Growth.applyXp(1, 20, 10));
    }

    @Test
    void severalLevelsInOneGo() {
        // level 1 needs 25, level 2 needs 50, level 3 needs 75: 25 + 50 + 75 = 150, 160 leaves 10
        assertEquals(new Progress(4, 10), Growth.applyXp(1, 0, 160));
        assertEquals(new Progress(3, 0), Growth.applyXp(1, 0, 75));
    }

    @Test
    void levelCapsAtMaxAndXpStaysZero() {
        assertEquals(new Progress(50, 0), Growth.applyXp(49, 0, 1_000_000));
        assertEquals(new Progress(50, 0), Growth.applyXp(50, 0, 500));
        assertEquals(new Progress(50, 0), Growth.applyXp(1, 0, Integer.MAX_VALUE));
    }

    @Test
    void justBelowTheLastThresholdKeepsLevel49() {
        assertEquals(new Progress(49, 1224), Growth.applyXp(49, 0, 1224));
        assertEquals(new Progress(50, 0), Growth.applyXp(49, 0, 1225));
    }

    @Test
    void zeroXpChangesNothing() {
        assertEquals(new Progress(7, 12), Growth.applyXp(7, 12, 0));
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Growth.applyXp(0, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> Growth.applyXp(51, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> Growth.applyXp(1, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> Growth.applyXp(1, -1, 5));
    }
}
