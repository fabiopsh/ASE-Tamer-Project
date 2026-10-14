package it.unipi.ase.tamers.players.player;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StageRulesTest {
    @Test
    void constants() {
        assertEquals(10, StageRules.MAX_STAGE);
        assertEquals(11, StageRules.COMPLETED_STAGE);
        assertEquals(1, StageRules.INITIAL_STAGE);
    }

    @Test
    void clearingTheCurrentStageAdvances() {
        assertEquals(2, StageRules.advance(1, 1));
        assertEquals(6, StageRules.advance(5, 5));
    }

    @Test
    void clearingTheLastStageCompletesTheGame() {
        assertEquals(11, StageRules.advance(10, 10));
        assertTrue(StageRules.isCompleted(11));
    }

    @Test
    void repeatedCallIsIdempotent() {
        int stage = StageRules.advance(3, 3);
        assertEquals(4, stage);
        assertEquals(4, StageRules.advance(stage, 3));
        assertEquals(4, StageRules.advance(stage, 3));
    }

    @Test
    void staleOrFutureClearedStageIsIgnored() {
        assertEquals(5, StageRules.advance(5, 4));
        assertEquals(5, StageRules.advance(5, 6));
        assertEquals(5, StageRules.advance(5, 0));
        assertEquals(5, StageRules.advance(5, -1));
    }

    @Test
    void completedGameNeverAdvances() {
        assertEquals(11, StageRules.advance(11, 11));
        assertEquals(11, StageRules.advance(11, 10));
        assertEquals(11, StageRules.advance(11, 12));
    }

    @Test
    void isCompletedOnlyAfterMaxStage() {
        for (int stage = 1; stage <= 10; stage++) {
            assertFalse(StageRules.isCompleted(stage), "stage " + stage);
        }
        assertTrue(StageRules.isCompleted(11));
    }
}
