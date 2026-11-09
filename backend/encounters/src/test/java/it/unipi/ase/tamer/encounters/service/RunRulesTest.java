package it.unipi.ase.tamer.encounters.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** EN-1 availability and EN-3 run progression, without Quarkus. */
class RunRulesTest {

    /** Template ids available for a state, over the 32 ids of the seed. */
    static List<Integer> available(RunRules.State state) {
        List<Integer> ids = new ArrayList<>();
        for (int stage = 1; stage <= 8; stage++) {
            for (int k = 1; k <= 4; k++) {
                if (RunRules.available(state, stage, k == 4)) {
                    ids.add(stage * 10 + k);
                }
            }
        }
        return ids;
    }

    static RunRules.State active(int stage, int wins, Integer... bosses) {
        return new RunRules.State(RunRules.ACTIVE, stage, wins, List.of(bosses));
    }

    @Test
    void stageOneShowsOnlyItsNormalTemplates() {
        assertEquals(List.of(11, 12, 13), available(active(1, 0)));
        assertEquals(List.of(11, 12, 13), available(active(1, 1)));
        assertFalse(RunRules.bossAvailable(active(1, 1)));
    }

    @Test
    void bossUnlocksAfterTwoWins() {
        assertTrue(RunRules.bossAvailable(active(1, 2)));
        assertEquals(List.of(11, 12, 13, 14), available(active(1, 2)));
        assertEquals(List.of(11, 12, 13, 14), available(active(1, 5)));
    }

    @Test
    void afterTheBossTheNextStageOpensAndClearedStagesStayPlayable() {
        RunRules.State next = RunRules.afterVictory(active(1, 2), true, 1);
        assertEquals(active(2, 0, 1), next);
        assertEquals(List.of(11, 12, 13, 21, 22, 23), available(next));
        // two more wins (any template, also of stage 1) unlock the stage-2 boss only
        RunRules.State farmed = RunRules.afterVictory(RunRules.afterVictory(next, false, 1), false, 2);
        assertEquals(2, farmed.winsInStage());
        assertEquals(List.of(11, 12, 13, 21, 22, 23, 24), available(farmed));
    }

    @Test
    void bossAlreadyBeatenIsNotOfferedAgain() {
        RunRules.State odd = active(3, 4, 1, 2, 3);
        assertFalse(RunRules.bossAvailable(odd));
        assertFalse(available(odd).contains(34));
    }

    @Test
    void normalVictoryOnlyCountsAWin() {
        assertEquals(active(4, 2, 1, 2, 3), RunRules.afterVictory(active(4, 1, 1, 2, 3), false, 2));
    }

    @Test
    void stageEightBossWinsTheRun() {
        Integer[] first7 = IntStream.rangeClosed(1, 7).boxed().toArray(Integer[]::new);
        RunRules.State last = RunRules.afterVictory(active(8, 2, first7), true, 8);
        assertEquals(RunRules.WON, last.status());
        assertEquals(8, last.stage());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), last.bossesDefeated());
        assertEquals(List.of(), available(last));
    }

    @Test
    void defeatLosesTheRunAndNothingIsAvailableOutsideAnActiveRun() {
        RunRules.State lost = RunRules.afterDefeat(active(2, 1, 1));
        assertEquals(RunRules.LOST, lost.status());
        assertEquals(List.of(), available(lost));
        assertEquals(List.of(), available(new RunRules.State(RunRules.ABANDONED, 3, 2, List.of(1, 2))));
    }
}
