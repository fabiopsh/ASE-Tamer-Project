package it.unipi.ase.tamer.history.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import it.unipi.ase.tamer.history.api.dto.EncounterRecordRequest;
import it.unipi.ase.tamer.history.api.dto.EncounterRecordResponse;
import it.unipi.ase.tamer.history.api.dto.PageResponse;
import it.unipi.ase.tamer.history.api.dto.RecordSummary;
import it.unipi.ase.tamer.history.api.dto.TemplateInfo;
import it.unipi.ase.tamer.history.api.dto.VerifyHistoryResponse;
import it.unipi.ase.tamer.history.domain.EncounterRecord;
import it.unipi.ase.tamer.history.domain.Outcome;
import it.unipi.ase.tamer.history.error.ApiException;
import it.unipi.ase.tamer.history.error.ErrorCodes;
import it.unipi.ase.tamer.history.error.ErrorDetail;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.hibernate.Session;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stores finished encounters (idempotently) and reads them back, always scoped by player. */
@ApplicationScoped
public class HistoryService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 50;

    private static final String INSERT = """
            INSERT INTO encounter_records (
                encounter_id, player_id, run_id, template_id, template_name, template_element, stage, is_boss,
                wild, team_snapshot, outcome, reward, xp_gained, turns, rng_seed, engine_version,
                started_at, ended_at, seq, prev_hash, hash)
            VALUES (
                :encounterId, :playerId, :runId, :templateId, :templateName, :templateElement, :stage, :isBoss,
                CAST(:wild AS jsonb), CAST(:teamSnapshot AS jsonb), :outcome, CAST(:reward AS jsonb), :xpGained,
                CAST(:turns AS jsonb), :rngSeed, :engineVersion, :startedAt, :endedAt, :seq, :prevHash, :hash)
            ON CONFLICT (encounter_id) DO NOTHING
            """;

    private static final String ENSURE_HEAD = """
            INSERT INTO chain_heads (player_id, seq, last_hash)
            VALUES (:playerId, 0, :genesis)
            ON CONFLICT (player_id) DO NOTHING
            """;

    private static final String LOCK_HEAD = """
            SELECT seq, CAST(last_hash AS varchar)
            FROM chain_heads
            WHERE player_id = :playerId
            FOR UPDATE
            """;

    private static final String READ_HEAD = """
            SELECT seq, CAST(last_hash AS varchar)
            FROM chain_heads
            WHERE player_id = :playerId
            """;

    private static final String UPDATE_HEAD = """
            UPDATE chain_heads
            SET seq = :seq, last_hash = :hash
            WHERE player_id = :playerId
            """;

    @Inject
    EntityManager em;

    @Inject
    ObjectMapper mapper;

    /** Result of {@link #store}: {@code created} is false when the encounter had already been stored. */
    public record StoreResult(boolean created, RecordSummary summary) {
    }

    /**
     * Inserts the record into the player's append-only hash chain unless one with the same {@code encounter_id}
     * already exists. Same-player retries return idempotent 200; cross-player {@code encounter_id} collisions are
     * rejected with 409 {@code ENCOUNTER_CONFLICT}.
     */
    @Transactional
    public StoreResult store(EncounterRecordRequest r) {
        if (r.endedAt().isBefore(r.startedAt())) {
            throw invalid("ended_at", "ENDED_BEFORE_STARTED", "ended_at must not be before started_at.");
        }
        Long seed = parseSeed(r.rngSeed());
        Session session = em.unwrap(Session.class);

        StoreResult existing = findExisting(session, r.encounterId(), r.playerId());
        if (existing != null) {
            return existing;
        }

        // Lock (or create) the player's chain head so concurrent inserts for the same player serialize cleanly.
        session.createNativeMutationQuery(ENSURE_HEAD)
                .setParameter("playerId", r.playerId())
                .setParameter("genesis", HashChain.GENESIS_HASH)
                .executeUpdate();
        Object[] head = session.createNativeQuery(LOCK_HEAD, Object[].class)
                .setParameter("playerId", r.playerId())
                .getSingleResult();
        long currentSeq = ((Number) head[0]).longValue();
        String prevHash = ((String) head[1]).trim();

        // Re-check under the player lock to handle concurrent duplicate inserts idempotently.
        StoreResult underLock = findExisting(session, r.encounterId(), r.playerId());
        if (underLock != null) {
            return underLock;
        }

        Instant startedAt = HashChain.normalizeTimestamp(r.startedAt());
        Instant endedAt = HashChain.normalizeTimestamp(r.endedAt());
        JsonNode wildNode = mapper.valueToTree(r.wild());
        JsonNode teamNode = listOrEmpty(r.teamSnapshot());
        JsonNode rewardNode = r.reward() == null || r.reward().isNull() ? null : r.reward();
        JsonNode turnsNode = listOrEmpty(r.turns());
        int xpGained = r.xpGained() == null ? 0 : r.xpGained();

        long nextSeq = currentSeq + 1;
        HashChain.Payload payload = new HashChain.Payload(
                r.encounterId(), r.playerId(), r.runId(),
                r.template().templateId(), r.template().name(), r.template().element(),
                r.template().stage(), r.template().isBoss(),
                wildNode, teamNode, r.outcome().wire(), rewardNode, xpGained, turnsNode,
                seed, r.engineVersion(), startedAt, endedAt);
        String hash = HashChain.computeHash(prevHash, nextSeq, payload);

        int inserted = session.createNativeMutationQuery(INSERT)
                .setParameter("encounterId", r.encounterId())
                .setParameter("playerId", r.playerId())
                .setParameter("runId", r.runId())
                .setParameter("templateId", r.template().templateId())
                .setParameter("templateName", r.template().name())
                .setParameter("templateElement", r.template().element())
                .setParameter("stage", r.template().stage())
                .setParameter("isBoss", r.template().isBoss())
                .setParameter("wild", json(wildNode), String.class)
                .setParameter("teamSnapshot", json(teamNode), String.class)
                .setParameter("outcome", r.outcome().wire())
                .setParameter("reward", rewardNode == null ? null : json(rewardNode), String.class)
                .setParameter("xpGained", xpGained)
                .setParameter("turns", json(turnsNode), String.class)
                .setParameter("rngSeed", seed, Long.class)
                .setParameter("engineVersion", r.engineVersion())
                .setParameter("startedAt", startedAt)
                .setParameter("endedAt", endedAt)
                .setParameter("seq", nextSeq)
                .setParameter("prevHash", prevHash)
                .setParameter("hash", hash)
                .executeUpdate();

        if (inserted == 0) {
            // Concurrent insert by another transaction won on encounter_id: do not advance chain_heads.
            StoreResult winner = findExisting(session, r.encounterId(), r.playerId());
            if (winner != null) {
                return winner;
            }
            throw new ApiException(409, ErrorCodes.ENCOUNTER_CONFLICT, "Encounter already exists.");
        }

        session.createNativeMutationQuery(UPDATE_HEAD)
                .setParameter("seq", nextSeq)
                .setParameter("hash", hash)
                .setParameter("playerId", r.playerId())
                .executeUpdate();

        RecordSummary summary = new RecordSummary(
                r.encounterId(), r.runId(), r.outcome().wire(),
                new TemplateInfo(r.template().templateId(), r.template().name(), r.template().stage(),
                        r.template().element(), r.template().isBoss()),
                wildNode, rewardNode, xpGained, startedAt, endedAt);
        return new StoreResult(true, summary);
    }

    /**
     * Recomputes the player's entire SHA-256 chain from {@link HashChain#GENESIS_HASH} and checks it against
     * {@code chain_heads} (detecting edits, deletions, truncations and sequence gaps).
     */
    @Transactional
    public VerifyHistoryResponse verify(UUID playerId) {
        Session session = em.unwrap(Session.class);
        List<Object[]> headRows = session.createNativeQuery(READ_HEAD, Object[].class)
                .setParameter("playerId", playerId)
                .getResultList();
        Long headSeq = headRows.isEmpty() ? null : ((Number) headRows.get(0)[0]).longValue();
        String headHash = headRows.isEmpty() ? null : ((String) headRows.get(0)[1]).trim();

        List<EncounterRecord> records = EncounterRecord.<EncounterRecord>list(
                "playerId = ?1 order by seq asc nulls last, recordedAt asc, encounterId asc", playerId);

        if (records.isEmpty()) {
            if (headSeq == null || headSeq == 0L) {
                String currentHead = headHash != null ? headHash : HashChain.GENESIS_HASH;
                return new VerifyHistoryResponse(true, 0, currentHead, null, null);
            }
            // Chain head expects records, but none exist (truncation / deletion).
            return new VerifyHistoryResponse(false, 0, headHash, null, 1L);
        }

        long expectedSeq = 1L;
        String expectedPrev = HashChain.GENESIS_HASH;
        for (EncounterRecord e : records) {
            if (e.seq == null || e.seq != expectedSeq) {
                return new VerifyHistoryResponse(false, records.size(),
                        headHash != null ? headHash : expectedPrev, e.encounterId, expectedSeq);
            }
            String storedPrev = e.prevHash == null ? null : e.prevHash.trim();
            String storedHash = e.hash == null ? null : e.hash.trim();
            if (!expectedPrev.equals(storedPrev)) {
                return new VerifyHistoryResponse(false, records.size(),
                        headHash != null ? headHash : expectedPrev, e.encounterId, expectedSeq);
            }
            String recomputed = HashChain.computeHash(expectedPrev, expectedSeq, HashChain.fromRecord(e));
            if (!recomputed.equals(storedHash)) {
                return new VerifyHistoryResponse(false, records.size(),
                        headHash != null ? headHash : expectedPrev, e.encounterId, expectedSeq);
            }
            expectedPrev = recomputed;
            expectedSeq++;
        }

        if (headSeq == null || headSeq != records.size() || !expectedPrev.equals(headHash)) {
            return new VerifyHistoryResponse(false, records.size(),
                    headHash != null ? headHash : expectedPrev, null, expectedSeq);
        }
        return new VerifyHistoryResponse(true, records.size(), expectedPrev, null, null);
    }

    private StoreResult findExisting(Session session, UUID encounterId, UUID playerId) {
        List<UUID> owners = session.createNativeQuery(
                        "SELECT player_id FROM encounter_records WHERE encounter_id = :encounterId", UUID.class)
                .setParameter("encounterId", encounterId)
                .getResultList();
        if (owners.isEmpty()) {
            return null;
        }
        if (!owners.get(0).equals(playerId)) {
            throw new ApiException(409, ErrorCodes.ENCOUNTER_CONFLICT,
                    "Encounter already exists for another player.");
        }
        RecordSummary summary = EncounterRecord
                .find("encounterId = ?1", encounterId)
                .project(RecordSummaryRow.class)
                .firstResult()
                .toSummary();
        return new StoreResult(false, summary);
    }

    /** Summaries of the player's encounters, newest first. */
    public PageResponse<RecordSummary> list(UUID playerId, String runId, String outcome, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_PAGE_SIZE : size;
        if (p < 0 || s < 1 || s > MAX_PAGE_SIZE) {
            throw invalid("pagination", "OUT_OF_RANGE",
                    "Page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        StringBuilder where = new StringBuilder("playerId = :playerId");
        Map<String, Object> params = new HashMap<>();
        params.put("playerId", playerId);
        if (runId != null) {
            try {
                params.put("runId", UUID.fromString(runId));
            } catch (IllegalArgumentException e) {
                throw invalid("run_id", "INVALID_UUID", "run_id must be a UUID.");
            }
            where.append(" and runId = :runId");
        }
        if (outcome != null) {
            Outcome parsed = Outcome.parse(outcome).orElseThrow(() -> invalid("outcome", "INVALID_OUTCOME",
                    "outcome must be one of victory, defeat, fled, expired, interrupted, abandoned."));
            params.put("outcome", parsed.wire());
            where.append(" and outcome = :outcome");
        }
        Sort newestFirst = Sort.descending("endedAt", "encounterId");
        List<RecordSummary> items = EncounterRecord.find(where.toString(), newestFirst, params)
                .project(RecordSummaryRow.class)
                .page(Page.of(p, s))
                .list().stream()
                .map(RecordSummaryRow::toSummary)
                .toList();
        long total = EncounterRecord.count(where.toString(), params);
        return PageResponse.of(items, p, s, total);
    }

    /** Full record; not found and not owned are indistinguishable (404). */
    public EncounterRecordResponse detail(UUID playerId, UUID encounterId) {
        EncounterRecord e = EncounterRecord
                .<EncounterRecord>find("encounterId = ?1 and playerId = ?2", encounterId, playerId)
                .firstResult();
        if (e == null) {
            throw ApiException.notFound("Encounter not found.");
        }
        return new EncounterRecordResponse(e.encounterId, e.runId,
                new TemplateInfo(e.templateId, e.templateName, e.stage, e.templateElement, e.isBoss),
                e.wild, e.teamSnapshot, e.outcome, e.reward, e.xpGained, e.turns,
                e.rngSeed == null ? null : e.rngSeed.toString(), e.engineVersion, e.startedAt, e.endedAt);
    }

    /** Row read for the list: the heavy JSON columns (team snapshot, turns) are never loaded. */
    public record RecordSummaryRow(UUID encounterId, UUID runId, String outcome, Integer templateId,
                                   String templateName, String templateElement, Integer stage, Boolean isBoss,
                                   JsonNode wild, JsonNode reward, Integer xpGained, Instant startedAt,
                                   Instant endedAt) {
        RecordSummary toSummary() {
            return new RecordSummary(encounterId, runId, outcome,
                    new TemplateInfo(templateId, templateName, stage, templateElement, isBoss),
                    wild, reward, xpGained, startedAt, endedAt);
        }
    }

    private JsonNode listOrEmpty(List<JsonNode> nodes) {
        return mapper.valueToTree(nodes == null ? List.of() : nodes);
    }

    private String json(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw invalid("body", "NOT_SERIALIZABLE", "Malformed request body.");
        }
    }

    private static Long parseSeed(String seed) {
        if (seed == null) {
            return null;
        }
        try {
            return Long.parseLong(seed);
        } catch (NumberFormatException e) {
            throw invalid("rng_seed", "OUT_OF_RANGE", "rng_seed must fit a signed 64-bit integer.");
        }
    }

    private static ApiException invalid(String field, String code, String message) {
        return new ApiException(400, ErrorCodes.VALIDATION_ERROR, message, List.of(new ErrorDetail(field, code)));
    }
}
