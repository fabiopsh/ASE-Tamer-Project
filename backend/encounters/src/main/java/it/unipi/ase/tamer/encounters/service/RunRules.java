package it.unipi.ase.tamer.encounters.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure run rules (docs/plan/07-encounters.md): availability of templates and progression after a battle.
 * No Quarkus, no DB: unit-tested directly.
 */
public final class RunRules {

    public static final String ACTIVE = "active";
    public static final String WON = "won";
    public static final String LOST = "lost";
    public static final String ABANDONED = "abandoned";

    /** Wins in the current stage needed to unlock its boss. */
    public static final int WINS_FOR_BOSS = 2;
    public static final int FINAL_STAGE = 8;

    /** Progression fields of a run. */
    public record State(String status, int stage, int winsInStage, List<Integer> bossesDefeated) {
        public State {
            Objects.requireNonNull(status, "status");
            bossesDefeated = List.copyOf(bossesDefeated);
        }
    }

    private RunRules() {
    }

    /** Boss of the current stage unlocked: run active, at least 2 wins in the stage, boss not yet beaten. */
    public static boolean bossAvailable(State run) {
        return ACTIVE.equals(run.status()) && run.winsInStage() >= WINS_FOR_BOSS
                && !run.bossesDefeated().contains(run.stage());
    }

    /**
     * {@code AvailabilityService.available(run)}: non-boss templates with {@code stage ≤ run.stage}; the boss of
     * {@code run.stage} when {@link #bossAvailable}. Nothing is available outside an active run.
     */
    public static boolean available(State run, int templateStage, boolean isBoss) {
        if (!ACTIVE.equals(run.status())) {
            return false;
        }
        if (!isBoss) {
            return templateStage <= run.stage();
        }
        return templateStage == run.stage() && bossAvailable(run);
    }

    /**
     * Victory: {@code wins_in_stage + 1}; on a boss the stage goes into {@code bosses_defeated} and the run moves
     * to the next stage with {@code wins_in_stage = 0}, or is {@code won} after the stage-8 boss.
     * Every victory counts, including one on a template of an earlier stage (plan, EN-3).
     */
    public static State afterVictory(State run, boolean isBoss, int templateStage) {
        if (!isBoss) {
            return new State(run.status(), run.stage(), run.winsInStage() + 1, run.bossesDefeated());
        }
        List<Integer> bosses = new ArrayList<>(run.bossesDefeated());
        if (!bosses.contains(templateStage)) {
            bosses.add(templateStage);
        }
        if (templateStage >= FINAL_STAGE) {
            return new State(WON, run.stage(), run.winsInStage() + 1, bosses);
        }
        return new State(run.status(), Math.max(run.stage(), templateStage + 1), 0, bosses);
    }

    /** Defeat: the run is lost (the reset follows). */
    public static State afterDefeat(State run) {
        return new State(LOST, run.stage(), run.winsInStage(), run.bossesDefeated());
    }
}
