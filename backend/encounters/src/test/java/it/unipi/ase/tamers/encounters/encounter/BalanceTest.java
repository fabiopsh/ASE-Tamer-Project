package it.unipi.ase.tamers.encounters.encounter;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.unipi.ase.tamers.encounters.client.ClientDtos.MonsterDetail;
import it.unipi.ase.tamers.encounters.engine.BattleEngine;
import it.unipi.ase.tamers.encounters.engine.BoardGenerator;
import it.unipi.ase.tamers.encounters.engine.BoardGenerator.CatalogEntry;
import it.unipi.ase.tamers.encounters.engine.BoardGenerator.Spawn;
import it.unipi.ase.tamers.encounters.engine.Combatant;
import it.unipi.ase.tamers.encounters.engine.ElementChart;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Balance check on the real seed data (backend/monsters/seed). Skipped when the seed is not reachable
 * (e.g. when only backend/encounters is mounted in the build container); set ENCOUNTERS_SEED_DIR to override.
 */
class BalanceTest {
    private static final int BATTLES = 500;
    private static List<MonsterDetail> catalog;
    private static Map<Integer, MonsterDetail> byId;
    private static List<CatalogEntry> entries;
    private static ElementChart chart;

    @BeforeAll
    static void load() throws IOException {
        String env = System.getenv("ENCOUNTERS_SEED_DIR");
        Path dir = env != null ? Path.of(env) : Path.of("..", "monsters", "seed");
        assumeTrue(Files.exists(dir.resolve("monsters.json")) && Files.exists(dir.resolve("abilities.json")),
                "seed data not found at " + dir.toAbsolutePath());
        ObjectMapper mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        Map<Integer, JsonNode> abilities = new HashMap<>();
        for (JsonNode a : mapper.readTree(dir.resolve("abilities.json").toFile())) {
            abilities.put(a.get("ability_id").asInt(), a);
        }
        JsonNode elements = mapper.readTree(dir.resolve("elements.json").toFile());
        List<String> cycle = new ArrayList<>();
        elements.get("cycle").forEach(n -> cycle.add(n.asText()));
        chart = ElementChart.fromCycle(cycle);

        catalog = new ArrayList<>();
        for (JsonNode m : mapper.readTree(dir.resolve("monsters.json").toFile())) {
            ObjectNode node = ((ObjectNode) m).deepCopy();
            node.set("ability", abilities.get(m.get("ability_id").asInt()));
            catalog.add(mapper.treeToValue(node, MonsterDetail.class));
        }
        byId = new HashMap<>();
        catalog.forEach(m -> byId.put(m.monsterId(), m));
        entries = catalog.stream().map(m -> new CatalogEntry(m.monsterId(), m.rarity(), m.element(), m.baseAttack())).toList();
    }

    private static Combatant level1(MonsterDetail m) {
        return Combatant.of(m.name(), m.element(), 1, m.statsAt(1), m.ability());
    }

    private static List<Combatant> randomCommonTeam(SplittableRandom rnd) {
        List<MonsterDetail> commons = new ArrayList<>(catalog.stream().filter(m -> m.rarity().equals("common")).toList());
        Collections.shuffle(commons, new java.util.Random(rnd.nextLong()));
        return commons.subList(0, 3).stream().map(BalanceTest::level1).toList();
    }

    private static double winRate(boolean guardian, long masterSeed) {
        SplittableRandom rnd = new SplittableRandom(masterSeed);
        int wins = 0;
        CatalogEntry guardianEntry = BoardGenerator.guardian(entries, 1);
        for (int i = 0; i < BATTLES; i++) {
            List<Combatant> team = randomCommonTeam(rnd);
            Combatant enemy;
            if (guardian) {
                enemy = CombatantFactory.enemy(byId.get(guardianEntry.monsterId()), BoardGenerator.guardianLevel(1), true);
            } else {
                Spawn s = BoardGenerator.wild(entries, 1, Set.of(), Set.of(), rnd);
                enemy = CombatantFactory.enemy(byId.get(s.monsterId()), s.level(), false);
            }
            if (BattleEngine.simulate(team, enemy, rnd.nextLong(), chart).victory()) {
                wins++;
            }
        }
        return wins / (double) BATTLES;
    }

    @Test
    void catalogLoadedFromSeed() {
        assertEquals(100, catalog.size());
        assertTrue(catalog.stream().allMatch(m -> m.ability() != null));
    }

    @Test
    void stage1WildIsBeatableButNotATrivialWalkover() {
        double rate = winRate(false, 20240101L);
        System.out.printf("BALANCE stage-1 wild: team of 3 level-1 commons wins %.1f%% of %d battles%n", rate * 100, BATTLES);
        assertTrue(rate >= 0.75 && rate <= 0.98, "stage-1 wild win rate " + rate);
    }

    @Test
    void stage1GuardianIsAChallenge() {
        double rate = winRate(true, 20240102L);
        System.out.printf("BALANCE stage-1 guardian: team of 3 level-1 commons wins %.1f%% of %d battles%n", rate * 100, BATTLES);
        assertTrue(rate >= 0.25 && rate <= 0.80, "stage-1 guardian win rate " + rate);
    }

}
