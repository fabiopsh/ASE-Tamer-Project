package it.unipi.ase.tamer.encounters.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * {@code result} of a finished battle (US-20). {@code outcome}: {@code victory | defeat | fled}. {@code reward} is
 * {@code null} unless victory; {@code reward.pending = true} (and {@code owned_id = null}) when monsters could not
 * be reached yet: the reward is retried automatically. {@code xp_gained} is the XP given to every team member;
 * {@code level_ups} lists only the members that gained at least one level. {@code run} is the run after the
 * battle.
 */
public record BattleResultResponse(String outcome, RewardResult reward, int xpGained, List<LevelUp> levelUps,
                                   RunProgress run) {

    public record RewardResult(UUID ownedId, int monsterId, int level, boolean pending) {
    }

    public record LevelUp(UUID ownedId, int levelBefore, int levelAfter) {
    }

    /** {@code status}: {@code active | won | lost}. */
    public record RunProgress(int stage, int winsInStage, boolean bossAvailable, String status) {
    }
}
