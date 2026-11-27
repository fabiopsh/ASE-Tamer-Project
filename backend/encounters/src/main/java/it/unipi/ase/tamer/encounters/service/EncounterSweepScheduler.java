package it.unipi.ase.tamer.encounters.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import it.unipi.ase.tamer.encounters.domain.Run;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Periodic recovery sweep (EN-5):
 * <ol>
 *   <li>expires in-memory battles idle longer than {@code tamer.battle.idle-timeout} (30 min);</li>
 *   <li>retries pending {@code battle_completions} outbox rows;</li>
 *   <li>resumes runs with {@code reset_pending = true}.</li>
 * </ol>
 */
@ApplicationScoped
public class EncounterSweepScheduler {

    private static final Logger LOG = Logger.getLogger(EncounterSweepScheduler.class);

    @ConfigProperty(name = "tamer.sweep.enabled", defaultValue = "true")
    boolean enabled;

    @Inject
    RecoveryService recovery;

    @Inject
    BattleCompletionService completions;

    @Inject
    RunResetService resets;

    @Inject
    BattleRegistry registry;

    private record PendingReset(UUID playerId, UUID runId) {
    }

    @Scheduled(every = "{tamer.sweep.interval:60s}", concurrentExecution = ConcurrentExecution.SKIP)
    void scheduledSweep() {
        if (!enabled) {
            return;
        }
        sweep();
    }

    /** Runs one full sweep pass (safe to call directly in tests). */
    public void sweep() {
        recovery.expireIdleBattles();

        for (BattleCompletionService.PendingEntry pending : completions.allPending()) {
            try {
                registry.withPlayerLock(pending.playerId(), () -> {
                    completions.processQuietly(pending.encounterId());
                    return null;
                });
            } catch (RuntimeException e) {
                LOG.warnf("Sweep retry of completion %s failed: %s", pending.encounterId(), e.toString());
            }
        }

        List<PendingReset> pendingResets = QuarkusTransaction.requiringNew().call(() -> Run.<Run>list(
                "resetPending = true").stream().map(r -> new PendingReset(r.playerId, r.runId)).toList());
        for (PendingReset pending : pendingResets) {
            try {
                registry.withPlayerLock(pending.playerId(), () -> {
                    try {
                        resets.resume(pending.runId());
                    } catch (RuntimeException e) {
                        LOG.warnf("Sweep resume of run %s postponed: %s", pending.runId(), e.toString());
                    }
                    return null;
                });
            } catch (RuntimeException e) {
                LOG.warnf("Sweep lock/resume of run %s failed: %s", pending.runId(), e.toString());
            }
        }
    }
}
