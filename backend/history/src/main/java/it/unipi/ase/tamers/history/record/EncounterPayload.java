package it.unipi.ase.tamers.history.record;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unipi.ase.tamers.history.common.ApiException;
import it.unipi.ase.tamers.history.record.HistoryDtos.CapturedSummary;
import it.unipi.ase.tamers.history.record.HistoryDtos.EnemySummary;
import it.unipi.ase.tamers.history.record.HistoryDtos.HistorySummary;
import it.unipi.ase.tamers.history.record.HistoryDtos.TeamMemberSummary;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reads the EncounterResult JSON stored in history: validates the header fields on append and
 * extracts the summary shown by GET /history. Pure logic, no CDI.
 */
public final class EncounterPayload {
    public static final Set<String> OUTCOMES = Set.of("victory", "defeat");
    /** Plain mapper: the payload is re-serialized with standard Jackson defaults, so it is stable. */
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private EncounterPayload() {}

    /** Header fields of an incoming record (the EncounterResult plus player_id). */
    public record Header(
            UUID playerId,
            UUID runId,
            UUID encounterId,
            String kind,
            int stage,
            String outcome,
            Instant occurredAt) {}

    /** Validates the incoming body; throws a 400 ApiException listing every invalid field. */
    public static Header header(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw ApiException.badRequest("body must be a JSON object");
        }
        List<String> errors = new ArrayList<>();
        UUID playerId = uuid(body, "player_id", errors);
        UUID runId = uuid(body, "run_id", errors);
        UUID encounterId = uuid(body, "encounter_id", errors);
        String kind = text(body, "kind", errors);
        int stage = integer(body, "stage", errors);
        integer(body, "rounds", errors);
        String outcome = text(body, "outcome", errors);
        if (outcome != null && !OUTCOMES.contains(outcome)) {
            errors.add("outcome: must be victory or defeat");
        }
        Instant occurredAt = instant(body, "occurred_at", errors);
        if (!body.path("enemy").isObject()) {
            errors.add("enemy: must be an object");
        }
        if (!body.path("team").isArray()) {
            errors.add("team: must be an array");
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("invalid encounter result", errors);
        }
        return new Header(playerId, runId, encounterId, kind, stage, outcome, occurredAt);
    }

    /** Builds the list summary of a stored EncounterResult; sequence comes from the record column. */
    public static HistorySummary summary(long sequence, JsonNode result) {
        JsonNode enemy = result.path("enemy");
        EnemySummary enemySummary = new EnemySummary(
                enemy.path("monster_id").asInt(),
                str(enemy, "name"),
                str(enemy, "element"),
                str(enemy, "image_url"),
                enemy.path("level").asInt());

        List<TeamMemberSummary> team = new ArrayList<>();
        for (JsonNode member : result.path("team")) {
            team.add(new TeamMemberSummary(
                    member.path("monster_id").asInt(),
                    str(member, "name"),
                    str(member, "image_url"),
                    member.path("level").asInt()));
        }

        JsonNode captured = result.path("rewards").path("captured");
        CapturedSummary capturedSummary = captured.isObject()
                ? new CapturedSummary(captured.path("monster_id").asInt(), str(captured, "name"), str(captured, "image_url"))
                : null;

        return new HistorySummary(
                UUID.fromString(str(result, "run_id")),
                UUID.fromString(str(result, "encounter_id")),
                sequence,
                str(result, "kind"),
                result.path("stage").asInt(),
                str(result, "outcome"),
                result.path("rounds").asInt(),
                enemySummary,
                team,
                capturedSummary,
                Instant.parse(str(result, "occurred_at")));
    }

    public static JsonNode parse(String payload) {
        try {
            return MAPPER.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored history payload is not valid JSON", e);
        }
    }

    public static String serialize(JsonNode body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("body could not be serialized");
        }
    }

    private static String text(JsonNode node, String field, List<String> errors) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            errors.add(field + ": is required");
            return null;
        }
        return value.asText();
    }

    private static UUID uuid(JsonNode node, String field, List<String> errors) {
        String value = text(node, field, errors);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            errors.add(field + ": must be a UUID");
            return null;
        }
    }

    private static int integer(JsonNode node, String field, List<String> errors) {
        JsonNode value = node.get(field);
        if (value == null || !value.isInt()) {
            errors.add(field + ": must be an integer");
            return 0;
        }
        return value.asInt();
    }

    private static Instant instant(JsonNode node, String field, List<String> errors) {
        String value = text(node, field, errors);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            errors.add(field + ": must be an ISO-8601 instant");
            return null;
        }
    }

    private static String str(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
