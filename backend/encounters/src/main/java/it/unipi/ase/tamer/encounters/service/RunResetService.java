package it.unipi.ase.tamer.encounters.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import it.unipi.ase.tamer.encounters.client.MonstersClient;
import it.unipi.ase.tamer.encounters.client.NewRunRequest;
import it.unipi.ase.tamer.encounters.client.PlayersClient;
import it.unipi.ase.tamer.encounters.client.RunIdRequest;
import it.unipi.ase.tamer.encounters.client.TeamsClient;
import it.unipi.ase.tamer.encounters.domain.Run;
import it.unipi.ase.tamer.encounters.error.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * {@code RunResetService} (docs/plan/07-encounters.md, "Run reset"), after a defeat, an abandon, or
 * {@code POST /api/runs} on a won run:
 * <ol>
 *   <li>{@link #begin}: status ({@code lost | abandoned}, or {@code won} kept), {@code ended_at},
 *       {@code reset_pending = true} in one transaction (for a defeat this is part of the battle completion);</li>
 *   <li>{@link #resume}: outstanding {@code battle_completions} of the run (history before archiving), then
 *       monsters archive, teams reset, players new-run, and finally {@code reset_pending = false},
 *       {@code reset_done = true}.</li>
 * </ol>
 * Every step is idempotent, so {@link #resume} can always be re-run from the start.
 */
@ApplicationScoped
public class RunResetService {

    @Inject
    BattleCompletionService completions;

    @Inject
    TimeSource time;

    @Inject
    @RestClient
    MonstersClient monsters;

    @Inject
    @RestClient
    TeamsClient teams;

    @Inject
    @RestClient
    PlayersClient players;

    /** Step 1: {@code status} is {@code lost}, {@code abandoned} or {@code won} (kept). No-op once reset. */
    public void begin(UUID runId, String status) {
        QuarkusTransaction.requiringNew().run(() -> {
            Run run = Run.findById(runId, LockModeType.PESSIMISTIC_WRITE);
            if (run == null || run.resetDone || run.resetPending) {
                return;
            }
            run.status = status;
            if (run.endedAt == null) {
                run.endedAt = time.now();
            }
            run.resetPending = true;
        });
    }

    /**
     * Steps 2–6. Failure → {@code 503 DEPENDENCY_UNAVAILABLE}; {@code reset_pending} stays true and the lazy
     * recovery resumes the reset at the next request of the player.
     */
    public void resume(UUID runId) {
        Run run = QuarkusTransaction.requiringNew().call(() -> Run.<Run>findById(runId));
        if (run == null || !run.resetPending) {
            return;
        }
        for (UUID encounterId : completions.pendingForRun(runId)) {
            if (!completions.process(encounterId).done()) {
                throw ApiException.dependencyUnavailable(
                        new IllegalStateException("Battle completion " + encounterId + " still pending"));
            }
        }
        UUID playerId = run.playerId;
        Remote.call(() -> monsters.archive(playerId, new RunIdRequest(runId)));
        Remote.call(() -> teams.reset(playerId, new RunIdRequest(runId)));
        Remote.call(() -> players.newRun(playerId, new NewRunRequest(runId)));
        QuarkusTransaction.requiringNew().run(() -> {
            Run row = Run.findById(runId, LockModeType.PESSIMISTIC_WRITE);
            row.resetPending = false;
            row.resetDone = true;
        });
    }
}
