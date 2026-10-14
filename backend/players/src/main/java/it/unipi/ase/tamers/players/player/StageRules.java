package it.unipi.ase.tamers.players.player;

/** Pure rules of the stage progression. Stage 11 means the game is completed. */
public final class StageRules {
    public static final int INITIAL_STAGE = 1;
    public static final int MAX_STAGE = 10;
    public static final int COMPLETED_STAGE = MAX_STAGE + 1;

    private StageRules() {}

    /**
     * Idempotent advance: the stage grows by one only when the cleared stage is exactly the current
     * one and the game is not completed yet. Any other call (repeated, stale, future) is a no-op.
     */
    public static int advance(int currentStage, int clearedStage) {
        if (clearedStage == currentStage && currentStage <= MAX_STAGE) {
            return currentStage + 1;
        }
        return currentStage;
    }

    public static boolean isCompleted(int stage) {
        return stage > MAX_STAGE;
    }
}
