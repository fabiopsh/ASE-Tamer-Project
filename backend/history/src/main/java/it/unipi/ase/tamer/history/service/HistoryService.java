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
                started_at, ended_at)
            VALUES (
                :encounterId, :playerId, :runId, :templateId, :templateName, :templateElement, :stage, :isBoss,
                CAST(:wild AS jsonb), CAST(:teamSnapshot AS jsonb), :outcome, CAST(:reward AS jsonb), :xpGained,
                CAST(:turns AS jsonb), :rngSeed, :engineVersion, :startedAt, :endedAt)
            ON CONFLICT (encounter_id) DO NOTHING
            """;

    @Inject
    EntityManager em;

    @Inject
    ObjectMapper mapper;

    /** Result of {@link #store}: {@code created} is false when the encounter had already been stored. */
    public record StoreResult(boolean created, RecordSummary summary) {
    }

    /**
     * Inserts the record unless one with the same {@code encounter_id} exists. The first write wins:
     * a repeated call changes nothing and returns the stored summary.
     */
    @Transactional
    public StoreResult store(EncounterRecordRequest r) {
        if (r.endedAt().isBefore(r.startedAt())) {
            throw invalid("ended_at", "ENDED_BEFORE_STARTED", "ended_at must not be before started_at.");
        }
        Long seed = parseSeed(r.rngSeed());
        int inserted = em.unwrap(Session.class).createNativeMutationQuery(INSERT)
                .setParameter("encounterId", r.encounterId())
                .setParameter("playerId", r.playerId())
                .setParameter("runId", r.runId())
                .setParameter("templateId", r.template().templateId())
                .setParameter("templateName", r.template().name())
                .setParameter("templateElement", r.template().element())
                .setParameter("stage", r.template().stage())
                .setParameter("isBoss", r.template().isBoss())
                .setParameter("wild", json(mapper.valueToTree(r.wild())), String.class)
                .setParameter("teamSnapshot", json(listOrEmpty(r.teamSnapshot())), String.class)
                .setParameter("outcome", r.outcome().wire())
                .setParameter("reward", r.reward() == null || r.reward().isNull() ? null : json(r.reward()),
                        String.class)
                .setParameter("xpGained", r.xpGained() == null ? 0 : r.xpGained())
                .setParameter("turns", json(listOrEmpty(r.turns())), String.class)
                .setParameter("rngSeed", seed, Long.class)
                .setParameter("engineVersion", r.engineVersion())
                .setParameter("startedAt", r.startedAt())
                .setParameter("endedAt", r.endedAt())
                .executeUpdate();
        RecordSummary summary = EncounterRecord
                .find("encounterId = ?1", r.encounterId())
                .project(RecordSummaryRow.class)
                .firstResult()
                .toSummary();
        return new StoreResult(inserted == 1, summary);
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
