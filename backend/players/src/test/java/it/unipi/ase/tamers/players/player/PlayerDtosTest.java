package it.unipi.ase.tamers.players.player;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import it.unipi.ase.tamers.players.player.PlayerDtos.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Checks the JSON shape of the contract (snake_case, progress null fields) without starting Quarkus. */
class PlayerDtosTest {
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private Player player(int stage) {
        Player p = new Player();
        p.playerId = UUID.randomUUID();
        p.displayName = "ash";
        p.stage = stage;
        p.starterPool = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        p.createdAt = Instant.parse("2026-10-14T10:00:00Z");
        return p;
    }

    @Test
    void profileUsesContractFieldNames() throws Exception {
        JsonNode json = mapper.valueToTree(PlayerProfile.from(player(1), Progress.unavailable()));
        assertEquals("ash", json.get("display_name").asText());
        assertTrue(json.has("avatar_monster_id") && json.get("avatar_monster_id").isNull());
        assertEquals(10, json.get("max_stage").asInt());
        assertFalse(json.get("game_completed").asBoolean());
        assertFalse(json.get("starters_chosen").asBoolean());
        assertEquals(10, json.get("starter_pool").size());
        assertEquals("2026-10-14T10:00:00Z", json.get("created_at").asText());
    }

    @Test
    void progressFieldsAreExplicitNullWhenUpstreamFailed() {
        JsonNode progress = mapper.valueToTree(PlayerProfile.from(player(1), Progress.unavailable())).get("progress");
        for (String field : List.of("collection_size", "species_owned", "encounters_total", "encounters_won")) {
            assertTrue(progress.has(field), field);
            assertTrue(progress.get(field).isNull(), field);
        }
    }

    @Test
    void progressIsOmittedWhenNotRequested() {
        JsonNode json = mapper.valueToTree(PlayerProfile.from(player(1), null));
        assertFalse(json.has("progress"));
    }

    @Test
    void completedGameIsFlagged() {
        JsonNode json = mapper.valueToTree(PlayerProfile.from(player(11), null));
        assertTrue(json.get("game_completed").asBoolean());
        assertEquals(11, json.get("stage").asInt());
    }

    @Test
    void upstreamPayloadsIgnoreUnknownFields() throws Exception {
        MonsterInfo m = mapper.readValue("{\"monster_id\":5,\"rarity\":\"common\",\"name\":\"x\",\"ability\":{}}",
                MonsterInfo.class);
        assertEquals(5, m.monsterId());
        assertEquals("common", m.rarity());
        CollectionStats s = mapper.readValue("{\"collection_size\":3,\"species_owned\":2}", CollectionStats.class);
        assertEquals(3, s.collectionSize());
        HistoryStats h = mapper.readValue(
                "{\"encounters_total\":5,\"encounters_won\":4,\"encounters_lost\":1}", HistoryStats.class);
        assertEquals(4, h.encountersWon());
    }

    @Test
    void batchRequestMatchesContract() {
        UUID id = UUID.randomUUID();
        JsonNode json = mapper.valueToTree(new BatchRequest(id, List.of(1, 2, 3), "starter"));
        assertEquals(id.toString(), json.get("player_id").asText());
        assertEquals(3, json.get("monster_ids").size());
        assertEquals("starter", json.get("source").asText());
    }
}
