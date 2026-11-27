package it.unipi.ase.tamer.history.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.unipi.ase.tamer.history.domain.EncounterRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure SHA-256 hash-chain utilities for append-only encounter history (US-25, HI-2).
 *
 * <p>Every record hash is {@code sha256Hex(prevHash + "|" + seq + "|" + canonicalJson)}, where
 * {@code canonicalJson} contains all payload fields (excluding {@code seq}, {@code prev_hash}, {@code hash} and
 * DB-generated {@code recorded_at}) with recursively sorted object keys and timestamps truncated to
 * {@link ChronoUnit#MICROS} (matching PostgreSQL {@code TIMESTAMPTZ} precision).
 */
public final class HashChain {

    public static final String GENESIS_HASH = "0".repeat(64);

    private static final ObjectMapper CANONICAL_MAPPER = JsonMapper.builder()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .build();

    private HashChain() {
    }

    /** Immutable payload hashed into each chain link. */
    public record Payload(
            UUID encounterId,
            UUID playerId,
            UUID runId,
            int templateId,
            String templateName,
            String templateElement,
            int stage,
            boolean isBoss,
            JsonNode wild,
            JsonNode teamSnapshot,
            String outcome,
            JsonNode reward,
            int xpGained,
            JsonNode turns,
            Long rngSeed,
            int engineVersion,
            Instant startedAt,
            Instant endedAt) {

        public Payload {
            Objects.requireNonNull(encounterId, "encounterId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(runId, "runId");
            Objects.requireNonNull(templateName, "templateName");
            Objects.requireNonNull(templateElement, "templateElement");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(startedAt, "startedAt");
            Objects.requireNonNull(endedAt, "endedAt");
            startedAt = normalizeTimestamp(startedAt);
            endedAt = normalizeTimestamp(endedAt);
        }
    }

    /** Truncates timestamps to microsecond precision to match PostgreSQL {@code TIMESTAMPTZ}. */
    public static Instant normalizeTimestamp(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MICROS);
    }

    public static Payload fromRecord(EncounterRecord e) {
        return new Payload(
                e.encounterId,
                e.playerId,
                e.runId,
                e.templateId,
                e.templateName,
                e.templateElement,
                e.stage,
                e.isBoss,
                e.wild,
                e.teamSnapshot,
                e.outcome,
                e.reward,
                e.xpGained,
                e.turns,
                e.rngSeed,
                e.engineVersion,
                e.startedAt,
                e.endedAt);
    }

    /** Serializes {@code payload} deterministically with alphabetically sorted keys at every nesting level. */
    public static String canonicalJson(Payload payload) {
        ObjectNode root = CANONICAL_MAPPER.createObjectNode();
        root.put("encounter_id", payload.encounterId().toString());
        root.put("ended_at", payload.endedAt().toString());
        root.put("engine_version", payload.engineVersion());
        root.put("is_boss", payload.isBoss());
        root.put("outcome", payload.outcome());
        root.put("player_id", payload.playerId().toString());
        root.set("reward", canonicalize(payload.reward()));
        if (payload.rngSeed() == null) {
            root.putNull("rng_seed");
        } else {
            root.put("rng_seed", payload.rngSeed());
        }
        root.put("run_id", payload.runId().toString());
        root.put("stage", payload.stage());
        root.put("started_at", payload.startedAt().toString());
        root.set("team_snapshot", canonicalize(payload.teamSnapshot() == null
                ? CANONICAL_MAPPER.createArrayNode() : payload.teamSnapshot()));
        root.put("template_element", payload.templateElement());
        root.put("template_id", payload.templateId());
        root.put("template_name", payload.templateName());
        root.set("turns", canonicalize(payload.turns() == null
                ? CANONICAL_MAPPER.createArrayNode() : payload.turns()));
        root.set("wild", canonicalize(payload.wild()));
        root.put("xp_gained", payload.xpGained());
        try {
            return CANONICAL_MAPPER.writeValueAsString(canonicalize(root));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize canonical history payload", e);
        }
    }

    /** Computes {@code sha256Hex(prevHash + "|" + seq + "|" + canonicalJson(payload))}. */
    public static String computeHash(String prevHash, long seq, Payload payload) {
        Objects.requireNonNull(prevHash, "prevHash");
        String input = prevHash + "|" + seq + "|" + canonicalJson(payload);
        return sha256Hex(input);
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return NullNode.getInstance();
        }
        if (node.isObject()) {
            ObjectNode sorted = CANONICAL_MAPPER.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            for (String name : names) {
                sorted.set(name, canonicalize(node.get(name)));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode arr = CANONICAL_MAPPER.createArrayNode();
            for (JsonNode child : node) {
                arr.add(canonicalize(child));
            }
            return arr;
        }
        return node;
    }
}
