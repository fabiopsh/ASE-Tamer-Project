package it.unipi.ase.tamer.encounters.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** EN-1: the stage templates seed matches the stage table of docs/plan/07-encounters.md. */
class TemplateSeedTest {

    /** stage → {boss id, boss element}. */
    static final Map<Integer, List<Object>> BOSSES = Map.of(1, List.of(31, "memory"), 2, List.of(89, "silence"),
            3, List.of(74, "gravity"), 4, List.of(62, "echo"), 5, List.of(47, "time"), 6, List.of(16, "dream"),
            7, List.of(92, "silence"), 8, List.of(100, "chaos"));
    static final Map<Integer, List<String>> ELEMENTS = Map.of(1, List.of("dream", "echo", "silence"),
            2, List.of("memory", "time", "gravity"), 3, List.of("dream", "echo", "silence"),
            4, List.of("memory", "time", "gravity"), 5, List.of("dream", "silence", "chaos"),
            6, List.of("echo", "memory", "gravity"), 7, List.of("time", "silence", "chaos"),
            8, List.of("dream", "gravity", "chaos"));
    static final Map<Integer, List<Integer>> WEIGHTS = Map.of(1, List.of(100, 0, 0), 2, List.of(90, 10, 0),
            3, List.of(80, 20, 0), 4, List.of(70, 30, 0), 5, List.of(60, 40, 0), 6, List.of(50, 45, 5),
            7, List.of(40, 50, 10), 8, List.of(30, 55, 15));

    static JsonNode seed() throws Exception {
        try (InputStream in = TemplateSeedTest.class.getClassLoader()
                .getResourceAsStream("seed/encounter_templates.json")) {
            return new ObjectMapper().readTree(in);
        }
    }

    @Test
    void thirtyTwoTemplatesFollowTheStageTable() throws Exception {
        JsonNode seed = seed();
        assertEquals(32, seed.size());
        Set<String> names = new HashSet<>();
        for (JsonNode t : seed) {
            int id = t.get("template_id").asInt();
            int stage = t.get("stage").asInt();
            int k = id - stage * 10;
            assertTrue(k >= 1 && k <= 4, "id " + id);
            String name = t.get("name").asText();
            assertFalse(name.isBlank());
            assertTrue(name.length() <= 40, name);
            assertTrue(names.add(name), "duplicate name " + name);
            if (k == 4) {
                assertTrue(t.get("is_boss").asBoolean());
                assertEquals(BOSSES.get(stage).get(0), t.get("boss_monster_id").asInt());
                assertEquals(BOSSES.get(stage).get(1), t.get("element").asText());
                assertEquals(3 * stage + 2, t.get("level_min").asInt());
                assertEquals(3 * stage + 2, t.get("level_max").asInt());
            } else {
                assertFalse(t.get("is_boss").asBoolean());
                assertTrue(t.get("boss_monster_id").isNull());
                assertEquals(ELEMENTS.get(stage).get(k - 1), t.get("element").asText());
                assertEquals(3 * stage - 2, t.get("level_min").asInt());
                assertEquals(3 * stage, t.get("level_max").asInt());
                JsonNode w = t.get("rarity_weights");
                assertEquals(WEIGHTS.get(stage), List.of(w.get("common").asInt(), w.get("rare").asInt(),
                        w.get("legendary").asInt()));
            }
        }
    }
}
