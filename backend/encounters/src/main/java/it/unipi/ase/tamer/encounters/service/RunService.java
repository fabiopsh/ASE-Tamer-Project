package it.unipi.ase.tamer.encounters.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import it.unipi.ase.tamer.encounters.api.dto.EncounterResponse;
import it.unipi.ase.tamer.encounters.api.dto.NewRunStatusResponse;
import it.unipi.ase.tamer.encounters.api.dto.PageResponse;
import it.unipi.ase.tamer.encounters.api.dto.RunResponse;
import it.unipi.ase.tamer.encounters.domain.Run;
import it.unipi.ase.tamer.encounters.error.ApiException;
import it.unipi.ase.tamer.encounters.error.ErrorCodes;
import it.unipi.ase.tamer.encounters.error.ErrorDetail;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Runs (EN-1) and the run operations of EN-3 (abandon, new run). Every public operation starts with the lazy
 * recovery; mutating ones run under the player lock. Reads happen in short transactions so that each one sees
 * the latest committed state.
 */
@ApplicationScoped
public class RunService {

    private static final Logger LOG = Logger.getLogger(RunService.class);

    @Inject
    RecoveryService recovery;

    @Inject
    BattleRegistry registry;

    @Inject
    AvailabilityService availability;

    @Inject
    BattleCompletionService completions;

    @Inject
    RunResetService resets;

    @Inject
    TimeSource time;

    // ---- queries ---------------------------------------------------------------------------------------------

    public Run activeRun(UUID playerId) {
        return QuarkusTransaction.requiringNew().call(() -> Run.<Run>find(
                "playerId = ?1 and status = ?2", playerId, RunRules.ACTIVE).firstResult());
    }

    public Run latestRun(UUID playerId) {
        return QuarkusTransaction.requiringNew().call(() -> Run.<Run>find(
                "playerId", Sort.descending("startedAt").and("runId"), playerId).firstResult());
    }

    public Run find(UUID runId) {
        return QuarkusTransaction.requiringNew().call(() -> Run.<Run>findById(runId));
    }

    // ---- internal: open a run --------------------------------------------------------------------------------

    /** Result of {@link #open}: {@code created} → 201, else 200. */
    public record Opened(boolean created, RunResponse run) {
    }

    /** {@code POST /internal/runs}: idempotent on {@code run_id}; another active run → 409. */
    public Opened open(UUID runId, UUID playerId) {
        Run existing = find(runId);
        if (existing != null) {
            if (!existing.playerId.equals(playerId)) {
                throw new ApiException(409, EncounterErrors.RUN_CONFLICT, "The run belongs to another player.");
            }
            return new Opened(false, RunResponse.from(existing));
        }
        if (activeRun(playerId) != null) {
            throw new ApiException(409, EncounterErrors.RUN_CONFLICT, "The player already has an active run.");
        }
        try {
            Run run = QuarkusTransaction.requiringNew().call(() -> {
                Run r = new Run();
                r.runId = runId;
                r.playerId = playerId;
                r.status = RunRules.ACTIVE;
                r.stage = 1;
                r.winsInStage = 0;
                r.bossesDefeated = new Integer[0];
                r.startedAt = time.now();
                r.persistAndFlush();
                return r;
            });
            return new Opened(true, RunResponse.from(run));
        } catch (RuntimeException e) {
            // concurrent open: same run id (idempotent) or another active run (unique index)
            Run again = find(runId);
            if (again != null && again.playerId.equals(playerId)) {
                return new Opened(false, RunResponse.from(again));
            }
            throw new ApiException(409, EncounterErrors.RUN_CONFLICT, "The player already has an active run.");
        }
    }

    // ---- public reads ----------------------------------------------------------------------------------------

    public List<EncounterResponse> encounters(UUID playerId) {
        recovery.recover(playerId, false);
        Run run = requireActive(playerId);
        return availability.available(run).stream().map(EncounterResponse::from).toList();
    }

    public EncounterResponse encounter(UUID playerId, int templateId) {
        recovery.recover(playerId, false);
        Run run = requireActive(playerId);
        return availability.availableTemplate(run, templateId).map(EncounterResponse::from)
                .orElseThrow(() -> ApiException.notFound("Encounter not available."));
    }

    public PageResponse<RunResponse> runs(UUID playerId, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;
        if (p < 0 || s < 1 || s > 50) {
            throw new ApiException(400, ErrorCodes.VALIDATION_ERROR,
                    "Page must be >= 0 and size must be between 1 and 50.",
                    List.of(new ErrorDetail(p < 0 ? "page" : "size", "OUT_OF_RANGE")));
        }
        recovery.recover(playerId, false);
        return QuarkusTransaction.requiringNew().call(() -> {
            var query = Run.<Run>find("playerId", Sort.descending("startedAt").and("runId"), playerId);
            long total = query.count();
            List<RunResponse> items = query.page(Page.of(p, s)).list().stream().map(RunResponse::from).toList();
            return PageResponse.of(items, p, s, total);
        });
    }

    /** The active run, or the latest {@code won} run that still waits for {@code POST /api/runs}. */
    public RunResponse current(UUID playerId) {
        recovery.recover(playerId, false);
        Run active = activeRun(playerId);
        if (active != null) {
            return RunResponse.from(active);
        }
        Run latest = latestRun(playerId);
        if (latest != null && RunRules.WON.equals(latest.status) && !latest.resetDone) {
            return RunResponse.from(latest);
        }
        throw EncounterErrors.noActiveRun(404);
    }

    public RunResponse run(UUID playerId, UUID runId) {
        recovery.recover(playerId, false);
        Run run = find(runId);
        if (run == null || !run.playerId.equals(playerId)) {
            throw ApiException.notFound("Run not found.");
        }
        return RunResponse.from(run);
    }

    // ---- public mutations ------------------------------------------------------------------------------------

    /** US-21: closes any battle as {@code abandoned}, run → {@code abandoned}, reset. */
    public RunResponse abandon(UUID playerId) {
        return registry.withPlayerLock(playerId, () -> {
            recovery.recover(playerId, true);
            Run run = activeRun(playerId);
            if (run == null) {
                throw EncounterErrors.noActiveRun(409);
            }
            BattleSession session = registry.get(playerId);
            if (session != null && session.encounterId().equals(run.activeBattleId)) {
                session.lock().lock();
                try {
                    session.close();
                    registry.remove(session);
                    completions.close(session, BattleCompletionService.ABANDONED, true);
                } finally {
                    session.lock().unlock();
                }
                completions.processQuietly(session.encounterId());
            } else {
                resets.begin(run.runId, RunRules.ABANDONED);
            }
            try {
                resets.resume(run.runId);
            } catch (RuntimeException e) {
                LOG.warnf("Run reset of %s postponed: %s", run.runId, e.toString());
            }
            return RunResponse.from(find(run.runId));
        });
    }

    /** {@code POST /api/runs}: only after a {@code won} run; resets it (idempotent) so new starters can be chosen. */
    public NewRunStatusResponse newRun(UUID playerId) {
        return registry.withPlayerLock(playerId, () -> {
            recovery.recover(playerId, true);
            Run latest = latestRun(playerId);
            if (latest == null || !RunRules.WON.equals(latest.status)) {
                throw new ApiException(409, EncounterErrors.NEW_RUN_NOT_ALLOWED,
                        "A new run can be started only after a won run.");
            }
            if (!latest.resetDone) {
                resets.begin(latest.runId, RunRules.WON);
                resets.resume(latest.runId);
            }
            return new NewRunStatusResponse(NewRunStatusResponse.CHOOSE_STARTERS);
        });
    }

    private Run requireActive(UUID playerId) {
        Run run = activeRun(playerId);
        if (run == null) {
            throw EncounterErrors.noActiveRun(409);
        }
        return run;
    }
}
