package it.unipi.ase.tamers.encounters.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class RewardsTest {

    @Test
    void xp() {
        assertEquals(20, Rewards.victoryXp(2, false));
        assertEquals(100, Rewards.victoryXp(5, true));
        assertEquals(6, Rewards.defeatXp(2, false));
        assertEquals(30, Rewards.defeatXp(5, true));
        assertEquals(20, Rewards.xp(true, 2, false));
        assertEquals(6, Rewards.xp(false, 2, false));
    }

    @Test
    void captureChances() {
        assertEquals(0.60, Rewards.captureChance("common", false));
        assertEquals(0.35, Rewards.captureChance("rare", false));
        assertEquals(1.0, Rewards.captureChance("rare", true));
        assertEquals(1.0, Rewards.captureChance("legendary", true));
        assertEquals(0.0, Rewards.captureChance("legendary", false));
    }

    @Test
    void captureRollFollowsTheChance() {
        SplittableRandom rnd = new SplittableRandom(1);
        int caught = 0;
        for (int i = 0; i < 20000; i++) {
            if (Rewards.rollCapture(0.35, rnd)) caught++;
        }
        assertEquals(0.35, caught / 20000.0, 0.02);
        assertTrue(Rewards.rollCapture(1.0, rnd));
        assertFalse(Rewards.rollCapture(0.0, rnd));
    }
}
