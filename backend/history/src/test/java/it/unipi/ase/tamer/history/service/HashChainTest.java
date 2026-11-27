package it.unipi.ase.tamer.history.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link HashChain} canonicalization, microsecond normalization and SHA-256 chaining. */
class HashChainTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID ENC = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PLAYER = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID RUN = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private static HashChain.Payload payload(JsonNode wild, JsonNode turns, String outcome, int xp, Instant start,
                                             Instant end) {
        return new HashChain.Payload(ENC, PLAYER, RUN, 12, "Whispering Caves", "echo", 1, false,
                wild, MAPPER.createArrayNode(), outcome, null, xp, turns, 42L, 2, start, end);
    }

    @Test
    void hashIsDeterministic64HexAndKeyOrderInvariant() throws Exception {
        JsonNode wildA = MAPPER.readTree("{\"monster_id\":51,\"name\":\"Mossling\",\"nested\":{\"z\":1,\"a\":2}}");
        JsonNode wildB = MAPPER.readTree("{\"nested\":{\"a\":2,\"z\":1},\"name\":\"Mossling\",\"monster_id\":51}");
        JsonNode turnsA = MAPPER.readTree("[{\"turn\":1,\"events\":[{\"b\":2,\"a\":1}]}]");
        JsonNode turnsB = MAPPER.readTree("[{\"events\":[{\"a\":1,\"b\":2}],\"turn\":1}]");
        Instant t0 = Instant.parse("2026-11-06T10:00:00.123456Z");
        Instant t1 = Instant.parse("2026-11-06T10:01:00.654321Z");

        String h1 = HashChain.computeHash(HashChain.GENESIS_HASH, 1L, payload(wildA, turnsA, "victory", 20, t0, t1));
        String h2 = HashChain.computeHash(HashChain.GENESIS_HASH, 1L, payload(wildB, turnsB, "victory", 20, t0, t1));

        assertEquals(h1, h2);
        assertEquals(64, h1.length());
        assertTrue(h1.matches("[0-9a-f]{64}"));
    }

    @Test
    void timestampsTruncatedToMicrosProduceSameHashAsPostgres() throws Exception {
        JsonNode wild = MAPPER.readTree("{\"monster_id\":51}");
        JsonNode turns = MAPPER.createArrayNode();
        Instant nanosStart = Instant.parse("2026-11-06T10:00:00.123456789Z");
        Instant microsStart = Instant.parse("2026-11-06T10:00:00.123456Z");
        Instant nanosEnd = Instant.parse("2026-11-06T10:01:00.987654321Z");
        Instant microsEnd = Instant.parse("2026-11-06T10:01:00.987654Z");

        String hNanos = HashChain.computeHash(HashChain.GENESIS_HASH, 1L,
                payload(wild, turns, "victory", 20, nanosStart, nanosEnd));
        String hMicros = HashChain.computeHash(HashChain.GENESIS_HASH, 1L,
                payload(wild, turns, "victory", 20, microsStart, microsEnd));
        assertEquals(hNanos, hMicros);
    }

    @Test
    void hashChangesWhenPayloadSeqOrPrevHashChanges() throws Exception {
        JsonNode wild = MAPPER.readTree("{\"monster_id\":51}");
        JsonNode turns = MAPPER.createArrayNode();
        Instant t0 = Instant.parse("2026-11-06T10:00:00Z");
        Instant t1 = Instant.parse("2026-11-06T10:01:00Z");

        HashChain.Payload base = payload(wild, turns, "victory", 20, t0, t1);
        String hBase = HashChain.computeHash(HashChain.GENESIS_HASH, 1L, base);

        assertNotEquals(hBase, HashChain.computeHash(HashChain.GENESIS_HASH, 2L, base));
        assertNotEquals(hBase, HashChain.computeHash("1".repeat(64), 1L, base));
        assertNotEquals(hBase, HashChain.computeHash(HashChain.GENESIS_HASH, 1L,
                payload(wild, turns, "defeat", 20, t0, t1)));
        assertNotEquals(hBase, HashChain.computeHash(HashChain.GENESIS_HASH, 1L,
                payload(wild, turns, "victory", 21, t0, t1)));
    }
}
