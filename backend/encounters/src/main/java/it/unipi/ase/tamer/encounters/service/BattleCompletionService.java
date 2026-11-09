package it.unipi.ase.tamer.encounters.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.narayana.jta.QuarkusTransaction;
import it.unipi.ase.tamer.encounters.api.dto.BattleResultResponse;
import it.unipi.ase.tamer.encounters.client.HistoryClient;
import it.unipi.ase.tamer.encounters.client.MonstersClient;
import it.unipi.ase.tamer.encounters.client.OwnedMonster;
import it.unipi.ase.tamer.encounters.client.RewardRequest;
import it.unipi.ase.tamer.encounters.client.XpGain;
import it.unipi.ase.tamer.encounters.client.XpRequest;
import it.unipi.ase.tamer.encounters.domain.BattleCompletion;
import it.unipi.ase.tamer.encounters.domain.EncounterTemplate;
import it.unipi.ase.tamer.encounters.domain.Run;
import it.unipi.ase.tamer.encounters.engine.Battle;
import it.unipi.ase.tamer.encounters.engine.Combatant;
import it.unipi.ase.tamer.encounters.engine.Outcome;
import it.unipi.ase.tamer.encounters.engine.TemplateInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * End of a battle (EN-3, "End-of-battle consistency"). {@link #close} commits, in one local transaction, the run
 * update, the cleared {@code active_battle_*} columns and the {@code battle_completions} outbox row; then
 * {@link #process} performs the remote side effects in order (reward → XP → history), each idempotent on
 * {@code encounter_id}. A failed step leaves its flag false and is retried by the lazy recovery.
 *
 * <p>A 4xx answer is permanent (the same request would fail again): the step is logged and marked done so that
 * it does not block the run reset forever.
 */
@ApplicationScoped
public class BattleCompletionService {

    public static final String VICTORY = "victory";
    public static final String DEFEAT = "defeat";
    public static final String FLED = "fled";
    public static final String EXPIRED = "expired";
    public static final String INTERRUPTED = "interrupted";
    public static final String ABANDONED = "abandoned";

    private static final Logger LOG = Logger.getLogger(BattleCompletionService.class);

    /** Result of {@link #process}: {@code xp_gains} is filled only by the call that granted the XP. */
    public record Processed(UUID rewardOwnedId, boolean rewardPending, List<XpGain> xpGains, boolean done) {
    }

    @Inject
    ObjectMapper mapper;

    @Inject
    @RestClient
    MonstersClient monsters;

    @Inject
    @RestClient
    HistoryClient history;

    @Inject
    TimeSource time;

    @Inject
    BattleRegistry registry;

    @Inject
    RunResetService resets;

    @Inject
    AvailabilityService availability;

    // ---- closing ---------------------------------------------------------------------------------------------

    /**
     * A battle finished by the engine ({@code victory | defeat | fled}): close it, process the outbox, run the
     * reset after a defeat, and build the {@code result} object of the last action.
     */
    public BattleResultResponse finish(BattleSession session) {
        session.close();
        registry.remove(session);
        Battle battle = session.battle();
        String outcome = battle.outcome().wireName();
        close(session, outcome, false);
        Processed processed = processQuietly(session.encounterId());
        if (battle.outcome() == Outcome.DEFEAT) {
            try {
                resets.resume(session.runId());
            } catch (RuntimeException e) {
                LOG.warnf("Run reset of %s postponed: %s", session.runId(), e.toString());
            }
        }
        Run run = QuarkusTransaction.requiringNew().call(() -> Run.<Run>findById(session.runId()));
        Combatant wild = battle.spec().wild();
        BattleResultResponse.RewardResult reward = battle.outcome() == Outcome.VICTORY
                ? new BattleResultResponse.RewardResult(processed.rewardOwnedId(), wild.monsterId(), wild.level(),
                processed.rewardPending())
                : null;
        List<BattleResultResponse.LevelUp> levelUps = processed.xpGains().stream()
                .filter(g -> g.levelAfter() > g.levelBefore())
                .map(g -> new BattleResultResponse.LevelUp(g.ownedId(), g.levelBefore(), g.levelAfter()))
                .toList();
        return new BattleResultResponse(outcome, reward, battle.xpGained(), levelUps,
                new BattleResultResponse.RunProgress(run.stage, run.winsInStage, RunRules.bossAvailable(run.state()),
                        run.status));
    }

    /**
     * Writes the outbox row of a battle and updates the run in one transaction.
     *
     * @param outcome   history outcome: {@code victory | defeat | fled | expired | abandoned}
     * @param abandonRun the run is abandoned at the same time ({@code POST /api/runs/current/abandon})
     */
    public void close(BattleSession session, String outcome, boolean abandonRun) {
        Instant now = time.now();
        Battle battle = session.battle();
        TemplateInfo template = battle.spec().template();
        Combatant wild = battle.spec().wild();
        UUID encounterId = session.encounterId();
        QuarkusTransaction.requiringNew().run(() -> {
            if (BattleCompletion.findById(encounterId) != null) {
                return;
            }
            Run run = Run.findById(session.runId(), LockModeType.PESSIMISTIC_WRITE);
            JsonNode rewardRequest = null;
            JsonNode xpRequest = null;
            JsonNode historyReward = null;
            int xp = 0;
            if (VICTORY.equals(outcome)) {
                rewardRequest = mapper.valueToTree(new RewardRequest(session.runId(), wild.monsterId(), wild.level(),
                        encounterId, template.isBoss() ? "boss" : "encounter"));
                historyReward = HistoryRecordBuilder.reward(mapper, wild.monsterId(), wild.level());
                xp = battle.xpGained();
                if (xp > 0) {
                    List<UUID> ownedIds = battle.teamInputs().stream().map(c -> UUID.fromString(c.id())).toList();
                    xpRequest = mapper.valueToTree(new XpRequest(encounterId, ownedIds, xp));
                }
                run.apply(RunRules.afterVictory(run.state(), template.isBoss(), template.stage()));
                if (RunRules.WON.equals(run.status) && run.endedAt == null) {
                    run.endedAt = now;
                }
            } else if (DEFEAT.equals(outcome)) {
                run.apply(RunRules.afterDefeat(run.state()));
                run.endedAt = now;
                run.resetPending = true;
            }
            if (abandonRun) {
                run.status = RunRules.ABANDONED;
                run.endedAt = now;
                run.resetPending = true;
            }
            if (encounterId.equals(run.activeBattleId)) {
                run.clearActiveBattle();
            }
            ObjectNode record = HistoryRecordBuilder.record(mapper, encounterId, session.playerId(), session.runId(),
                    template, session.wildInfo(), session.teamSnapshot(), outcome, historyReward, xp, battle.log(),
                    battle.seed(), session.startedAt(), now);
            persist(encounterId, session.playerId(), session.runId(), outcome, rewardRequest, xpRequest, record, now);
        });
    }

    /**
     * The run records a battle that is not in memory (service restarted): close it as {@code interrupted} with a
     * minimal record built from the {@code active_battle_*} columns; no reward, the run continues.
     *
     * @return the encounter id, or {@code null} if there was nothing to close
     */
    public UUID closeInterrupted(UUID runId) {
        Instant now = time.now();
        return QuarkusTransaction.requiringNew().call(() -> {
            Run run = Run.findById(runId, LockModeType.PESSIMISTIC_WRITE);
            if (run == null || run.activeBattleId == null) {
                return null;
            }
            UUID encounterId = run.activeBattleId;
            EncounterTemplate template = run.activeBattleTemplateId == null ? null
                    : availability.template(run.activeBattleTemplateId).orElse(null);
            if (BattleCompletion.findById(encounterId) == null && template != null && run.activeBattleWild != null) {
                Instant started = run.activeBattleStartedAt == null ? now : run.activeBattleStartedAt;
                ObjectNode record = HistoryRecordBuilder.record(mapper, encounterId, run.playerId, runId,
                        template.info(), run.activeBattleWild,
                        run.activeBattleTeam == null ? mapper.createArrayNode() : run.activeBattleTeam,
                        INTERRUPTED, null, 0, List.of(), run.activeBattleSeed, started, now);
                persist(encounterId, run.playerId, runId, INTERRUPTED, null, null, record, now);
            } else if (template == null || run.activeBattleWild == null) {
                LOG.warnf("Battle %s of run %s closed without a history record (missing data)", encounterId, runId);
            }
            run.clearActiveBattle();
            return encounterId;
        });
    }

    private void persist(UUID encounterId, UUID playerId, UUID runId, String outcome, JsonNode rewardRequest,
                         JsonNode xpRequest, ObjectNode historyRecord, Instant now) {
        ObjectNode payload = mapper.createObjectNode();
        payload.set("reward", rewardRequest == null ? mapper.nullNode() : rewardRequest);
        payload.set("xp", xpRequest == null ? mapper.nullNode() : xpRequest);
        payload.set("history", historyRecord);
        BattleCompletion completion = new BattleCompletion();
        completion.encounterId = encounterId;
        completion.playerId = playerId;
        completion.runId = runId;
        completion.outcome = outcome;
        completion.payload = payload;
        completion.rewardDone = rewardRequest == null;
        completion.xpDone = xpRequest == null;
        completion.historyDone = false;
        completion.createdAt = now;
        completion.persist();
    }

    // ---- outbox processing -----------------------------------------------------------------------------------

    public List<UUID> pendingForPlayer(UUID playerId) {
        return QuarkusTransaction.requiringNew().call(() -> BattleCompletion.<BattleCompletion>list(
                        "playerId = ?1 and (rewardDone = false or xpDone = false or historyDone = false) "
                                + "order by createdAt", playerId)
                .stream().map(c -> c.encounterId).toList());
    }

    public List<UUID> pendingForRun(UUID runId) {
        return QuarkusTransaction.requiringNew().call(() -> BattleCompletion.<BattleCompletion>list(
                        "runId = ?1 and (rewardDone = false or xpDone = false or historyDone = false) "
                                + "order by createdAt", runId)
                .stream().map(c -> c.encounterId).toList());
    }

    /** {@link #process} that never throws (a DB failure is logged and reported as not done). */
    public Processed processQuietly(UUID encounterId) {
        try {
            return process(encounterId);
        } catch (RuntimeException e) {
            LOG.warnf("Processing of battle completion %s failed: %s", encounterId, e.toString());
            return new Processed(null, true, List.of(), false);
        }
    }

    /** Performs the pending remote steps of one outbox row: reward → XP → history (history after the reward). */
    public Processed process(UUID encounterId) {
        BattleCompletion c = QuarkusTransaction.requiringNew().call(
                () -> BattleCompletion.<BattleCompletion>findById(encounterId));
        if (c == null) {
            return new Processed(null, false, List.of(), true);
        }
        UUID ownedId = c.rewardOwnedId;
        boolean rewardDone = c.rewardDone;
        boolean xpDone = c.xpDone;
        boolean historyDone = c.historyDone;
        List<XpGain> gains = List.of();

        if (!rewardDone) {
            try {
                OwnedMonster owned = monsters.reward(c.playerId, read(c.payload.get("reward"), RewardRequest.class));
                ownedId = owned == null ? null : owned.ownedId();
                rewardDone = true;
            } catch (RuntimeException e) {
                rewardDone = giveUpIfPermanent("reward", encounterId, e);
            }
            if (rewardDone) {
                UUID granted = ownedId;
                update(encounterId, row -> {
                    row.rewardDone = true;
                    row.rewardOwnedId = granted;
                });
            }
        }
        if (!xpDone) {
            try {
                List<XpGain> result = monsters.xp(c.playerId, read(c.payload.get("xp"), XpRequest.class));
                gains = result == null ? List.of() : result;
                xpDone = true;
            } catch (RuntimeException e) {
                xpDone = giveUpIfPermanent("xp", encounterId, e);
            }
            if (xpDone) {
                update(encounterId, row -> row.xpDone = true);
            }
        }
        if (!historyDone && rewardDone) {
            ObjectNode record = c.payload.get("history").deepCopy();
            if (ownedId != null && record.get("reward") instanceof ObjectNode reward) {
                reward.put("owned_id", ownedId.toString());
            }
            try {
                history.store(record);
                historyDone = true;
            } catch (RuntimeException e) {
                historyDone = giveUpIfPermanent("history", encounterId, e);
            }
            if (historyDone) {
                update(encounterId, row -> row.historyDone = true);
            }
        }
        return new Processed(ownedId, !rewardDone, gains, rewardDone && xpDone && historyDone);
    }

    private boolean giveUpIfPermanent(String step, UUID encounterId, RuntimeException e) {
        if (Remote.permanent(e)) {
            LOG.errorf("Battle completion %s: %s rejected permanently (%s); step skipped", encounterId, step,
                    e.getMessage());
            return true;
        }
        LOG.warnf("Battle completion %s: %s postponed (%s)", encounterId, step, e.toString());
        return false;
    }

    private void update(UUID encounterId, Consumer<BattleCompletion> change) {
        QuarkusTransaction.requiringNew().run(() -> {
            BattleCompletion row = BattleCompletion.findById(encounterId, LockModeType.PESSIMISTIC_WRITE);
            if (row != null) {
                change.accept(row);
            }
        });
    }

    private <T> T read(JsonNode node, Class<T> type) {
        try {
            return mapper.treeToValue(node, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupted battle completion payload", e);
        }
    }
}
