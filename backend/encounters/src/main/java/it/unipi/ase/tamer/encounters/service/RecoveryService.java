package it.unipi.ase.tamer.encounters.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import it.unipi.ase.tamer.encounters.domain.Run;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Lazy recovery (P-20), at the start of every run/battle request of a player, under the player lock:
 * <ol>
 *   <li>runs with {@code reset_pending} → resume the reset; still failing → {@code 409 RESET_PENDING} on
 *       mutating requests (reads go on);</li>
 *   <li>pending {@code battle_completions} → retry them;</li>
 *   <li>{@code active_battle_id} set but no battle in memory (restart) → close as {@code interrupted};</li>
 *   <li>battle in memory idle for more than {@code tamer.battle.idle-timeout} (30 min) → close as
 *       {@code expired} (no reward, the run continues).</li>
 * </ol>
 */
@ApplicationScoped
public class RecoveryService {

    private static final Logger LOG = Logger.getLogger(RecoveryService.class);

    @Inject
    BattleRegistry registry;

    @Inject
    BattleCompletionService completions;

    @Inject
    RunResetService resets;

    @Inject
    RunService runs;

    @Inject
    TimeSource time;

    @ConfigProperty(name = "tamer.battle.idle-timeout", defaultValue = "PT30M")
    Duration idleTimeout;

    public void recover(UUID playerId, boolean mutating) {
        registry.withPlayerLock(playerId, () -> {
            resumeResets(playerId, mutating);
            for (UUID encounterId : completions.pendingForPlayer(playerId)) {
                completions.processQuietly(encounterId);
            }
            closeStaleBattle(playerId);
            return null;
        });
    }

    private void resumeResets(UUID playerId, boolean mutating) {
        List<UUID> pending = QuarkusTransaction.requiringNew().call(() -> Run.<Run>list(
                "playerId = ?1 and resetPending = true", playerId).stream().map(r -> r.runId).toList());
        for (UUID runId : pending) {
            try {
                resets.resume(runId);
            } catch (RuntimeException e) {
                LOG.warnf("Run reset of %s still pending: %s", runId, e.toString());
                if (mutating) {
                    throw EncounterErrors.resetPending();
                }
            }
        }
    }

    private void closeStaleBattle(UUID playerId) {
        Run active = runs.activeRun(playerId);
        BattleSession session = registry.get(playerId);
        if (active == null || active.activeBattleId == null) {
            if (session != null) {
                // a battle in memory that no run refers to: drop it
                session.close();
                registry.remove(session);
            }
            return;
        }
        if (session == null || !session.encounterId().equals(active.activeBattleId)) {
            if (session != null) {
                session.close();
                registry.remove(session);
            }
            UUID encounterId = completions.closeInterrupted(active.runId);
            if (encounterId != null) {
                LOG.infof("Battle %s of player %s closed as interrupted", encounterId, playerId);
                completions.processQuietly(encounterId);
            }
            return;
        }
        if (Duration.between(session.lastActionAt(), time.now()).compareTo(idleTimeout) > 0) {
            session.lock().lock();
            try {
                if (session.closed()) {
                    return;
                }
                session.close();
                registry.remove(session);
                completions.close(session, BattleCompletionService.EXPIRED, false);
            } finally {
                session.lock().unlock();
            }
            LOG.infof("Battle %s of player %s expired", session.encounterId(), playerId);
            completions.processQuietly(session.encounterId());
        }
    }
}
