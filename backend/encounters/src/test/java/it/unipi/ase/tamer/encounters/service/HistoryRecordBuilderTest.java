package it.unipi.ase.tamer.encounters.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.unipi.ase.tamer.encounters.client.AbilityInfo;
import it.unipi.ase.tamer.encounters.client.StatBlock;
import it.unipi.ase.tamer.encounters.client.TeamSnapshot;
import it.unipi.ase.tamer.encounters.engine.Action;
import it.unipi.ase.tamer.encounters.engine.Battle;
import it.unipi.ase.tamer.encounters.engine.BattleEvent;
import it.unipi.ase.tamer.encounters.engine.Combatant;
import it.unipi.ase.tamer.encounters.engine.ElementChart;
import it.unipi.ase.tamer.encounters.engine.EncounterSpec;
import it.unipi.ase.tamer.encounters.engine.RoundResult;
import it.unipi.ase.tamer.encounters.engine.TemplateInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** EN-3: the history record sent to {@code POST /internal/history} has exactly the accepted shape. */
class HistoryRecordBuilderTest {

    static final ObjectMapper MAPPER = JsonSupport.standalone();
    static final TemplateInfo TEMPLATE = new TemplateInfo(12, "Whispering Caves", 1, "echo", false);
    static final Set<String> RECORD_FIELDS = Set.of("encounter_id", "player_id", "run_id", "template", "wild",
            "team_snapshot", "outcome", "reward", "xp_gained", "turns", "rng_seed", "engine_version", "started_at",
            "ended_at");

    static Battle battle() {
        Combatant wild = new Combatant(null, 51, "Mossling", "echo", "common", 2, 10, 5, 5, 5, null);
        Combatant member = new Combatant(UUID.randomUUID().toString(), 3, "Emberling", "dream", "common", 3,
                20, 50, 10, 10, null);
        Battle battle = new Battle(-4512345678901234567L, new EncounterSpec(TEMPLATE, wild), List.of(member),
                ElementChart.defaults());
        while (!battle.isFinished()) {
            battle.apply(Action.attack());
        }
        return battle;
    }

    static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void victoryRecordHasTheHistoryContract() {
        Battle battle = battle();
        UUID encounter = UUID.randomUUID();
        Instant start = Instant.parse("2026-11-09T10:00:00Z");
        TeamSnapshot.Member member = new TeamSnapshot.Member(1, UUID.randomUUID(), 3, "Emberling", 3, "dream",
                "common", "/images/monsters/003.png", new StatBlock(20, 50, 10, 10),
                new AbilityInfo(4, "Spark", "", "damage_boost", 10, "always"));
        ObjectNode record = HistoryRecordBuilder.record(MAPPER, encounter, UUID.randomUUID(), UUID.randomUUID(),
                TEMPLATE, HistoryRecordBuilder.wild(MAPPER, battle.spec().wild()),
                HistoryRecordBuilder.team(MAPPER, List.of(member)), "victory",
                HistoryRecordBuilder.reward(MAPPER, 51, 2), battle.xpGained(), battle.log(), battle.seed(),
                start, start.plusSeconds(30));

        assertEquals(RECORD_FIELDS, Set.copyOf(names(record)));
        assertEquals(encounter.toString(), record.get("encounter_id").asText());
        assertEquals(List.of("template_id", "name", "stage", "element", "is_boss"), names(record.get("template")));
        assertEquals(List.of("monster_id", "name", "level", "rarity", "element"), names(record.get("wild")));
        assertTrue(record.get("rng_seed").isTextual());
        assertEquals("-4512345678901234567", record.get("rng_seed").asText());
        assertEquals(Battle.ENGINE_VERSION, record.get("engine_version").asInt());
        assertEquals(20, record.get("xp_gained").asInt());
        assertEquals("2026-11-09T10:00:00Z", record.get("started_at").asText());
        assertEquals("2026-11-09T10:00:30Z", record.get("ended_at").asText());
        assertTrue(record.get("reward").get("owned_id").isNull());
        JsonNode snapshot = record.get("team_snapshot").get(0);
        assertEquals(50, snapshot.get("stats").get("attack").asInt());
        assertEquals("Spark", snapshot.get("ability").get("name").asText());

        JsonNode turns = record.get("turns");
        assertEquals(battle.log().size(), turns.size());
        assertEquals(1, turns.get(0).get("turn").asInt());
        // events carry only the fields of their type (NON_NULL)
        for (JsonNode turn : turns) {
            for (JsonNode event : turn.get("events")) {
                for (Iterator<JsonNode> it = event.elements(); it.hasNext(); ) {
                    assertFalse(it.next().isNull(), "null field in " + event);
                }
            }
        }
        JsonNode end = turns.get(turns.size() - 1).get("events");
        assertEquals("end", end.get(end.size() - 1).get("type").asText());
        assertEquals("victory", end.get(end.size() - 1).get("outcome").asText());
    }

    @Test
    void minimalRecordAndLimits() {
        Instant start = Instant.parse("2026-11-09T10:00:00Z");
        List<RoundResult> many = new ArrayList<>();
        for (int i = 1; i <= 600; i++) {
            many.add(new RoundResult(i, List.of(BattleEvent.flee(false))));
        }
        ObjectNode wild = MAPPER.createObjectNode().put("monster_id", 51).put("name", "Mossling").put("level", 2)
                .put("rarity", "common").put("element", "echo");
        ObjectNode record = HistoryRecordBuilder.record(MAPPER, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), TEMPLATE, wild, null, "interrupted", null, 0, many, null, start,
                start.minusSeconds(5));
        assertEquals(HistoryRecordBuilder.MAX_TURNS, record.get("turns").size());
        assertTrue(record.get("reward").isNull());
        assertEquals(0, record.get("team_snapshot").size());
        assertFalse(record.has("rng_seed"));
        assertEquals(record.get("started_at"), record.get("ended_at"), "ended_at is never before started_at");
    }
}
