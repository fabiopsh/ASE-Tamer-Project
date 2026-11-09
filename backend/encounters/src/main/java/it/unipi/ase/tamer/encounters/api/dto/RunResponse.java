package it.unipi.ase.tamer.encounters.api.dto;

import it.unipi.ase.tamer.encounters.domain.Run;
import it.unipi.ase.tamer.encounters.service.RunRules;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * A run. {@code status}: {@code active | won | lost | abandoned}. {@code bosses_defeated}: stages whose boss was
 * beaten. {@code battle_in_progress}: a battle is open ({@code GET /api/battles/current}). {@code reset_pending}:
 * the end-of-run reset (archive, teams reset, new run) is not finished yet and will be resumed automatically.
 * {@code ended_at} is {@code null} while the run is active.
 */
public record RunResponse(UUID runId, String status, int stage, int winsInStage, boolean bossAvailable,
                          List<Integer> bossesDefeated, boolean battleInProgress, boolean resetPending,
                          Instant startedAt, Instant endedAt) {

    public static RunResponse from(Run run) {
        return new RunResponse(run.runId, run.status, run.stage, run.winsInStage, RunRules.bossAvailable(run.state()),
                run.bossesDefeated == null ? List.of() : Arrays.asList(run.bossesDefeated),
                run.activeBattleId != null, run.resetPending, run.startedAt, run.endedAt);
    }
}
