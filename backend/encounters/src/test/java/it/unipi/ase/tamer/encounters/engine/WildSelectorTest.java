package it.unipi.ase.tamer.encounters.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unipi.ase.tamer.encounters.engine.WildSelector.Species;
import it.unipi.ase.tamer.encounters.engine.WildSelector.WildPick;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WildSelectorTest {

    /** Dream species as in the seed: 10 commons (1–10), 5 rares (11–15), legendary Morpheon (16, a boss). */
    private static final List<Species> CATALOGUE = buildCatalogue();
    private static final Set<Integer> BOSSES = Set.of(31, 89, 74, 62, 47, 16, 92, 100);

    private static List<Species> buildCatalogue() {
        List<Species> list = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            list.add(new Species(i, "dream", "common"));
        }
        for (int i = 11; i <= 15; i++) {
            list.add(new Species(i, "dream", "rare"));
        }
        list.add(new Species(16, "dream", "legendary"));
        list.add(new Species(89, "silence", "rare"));
        list.add(new Species(90, "silence", "common"));
        list.add(new Species(92, "silence", "legendary"));
        return list;
    }

    @Test
    void neverReturnsABossAndRenormalisesEmptyRarity() {
        Map<String, Integer> weights = Map.of("common", 50, "rare", 45, "legendary", 5);
        Map<String, Integer> counts = new HashMap<>();
        for (long seed = 0; seed < 5000; seed++) {
            WildPick p = WildSelector.select(new BattleRandom(seed), CATALOGUE, "dream", BOSSES, weights, 16, 18);
            assertTrue(!BOSSES.contains(p.monsterId()), "boss returned");
            assertTrue(p.level() >= 16 && p.level() <= 18);
            counts.merge(p.rarity(), 1, Integer::sum);
        }
        assertEquals(Set.of("common", "rare"), counts.keySet());
        // legendary dropped: common 50/95 ≈ 52.6%
        double common = counts.get("common") / 5000.0;
        assertTrue(Math.abs(common - 50.0 / 95) < 0.03, "common share " + common);
    }

    @Test
    void onlyAvailableRarityIsDrawnAndElementFiltered() {
        // Silence: 89 (rare) and 92 (legendary) are bosses, so only the common 90 is left: its 10 → 100%.
        Map<String, Integer> weights = Map.of("common", 10, "rare", 30, "legendary", 60);
        for (long seed = 0; seed < 500; seed++) {
            WildPick p = WildSelector.select(new BattleRandom(seed), CATALOGUE, "silence", BOSSES, weights, 1, 3);
            assertEquals(new WildPick(90, "common", p.level()), p);
        }
        // Only zero-weight rarities left → configuration error.
        assertThrows(IllegalStateException.class, () -> WildSelector.select(new BattleRandom(1), CATALOGUE,
                "silence", BOSSES, Map.of("common", 0, "rare", 100), 1, 3));
    }

    @Test
    void uniformSpeciesAndLevelsCovered() {
        Map<String, Integer> weights = Map.of("common", 100, "rare", 0, "legendary", 0);
        Set<Integer> species = new HashSet<>();
        Set<Integer> levels = new HashSet<>();
        for (long seed = 0; seed < 1000; seed++) {
            WildPick p = WildSelector.select(new BattleRandom(seed), CATALOGUE, "dream", BOSSES, weights, 1, 3);
            assertEquals("common", p.rarity());
            species.add(p.monsterId());
            levels.add(p.level());
        }
        assertEquals(10, species.size());
        assertEquals(Set.of(1, 2, 3), levels);
    }

    @Test
    void deterministicAndOrderIndependent() {
        Map<String, Integer> weights = Map.of("common", 60, "rare", 40);
        List<Species> reversed = new java.util.ArrayList<>(CATALOGUE);
        java.util.Collections.reverse(reversed);
        for (long seed = 0; seed < 100; seed++) {
            WildPick a = WildSelector.select(new BattleRandom(seed), CATALOGUE, "dream", BOSSES, weights, 4, 6);
            WildPick b = WildSelector.select(new BattleRandom(seed), reversed, "dream", BOSSES, weights, 4, 6);
            assertEquals(a, b);
        }
    }

    @Test
    void noCandidateFails() {
        assertThrows(IllegalStateException.class, () -> WildSelector.select(new BattleRandom(1), CATALOGUE, "chaos",
                BOSSES, Map.of("common", 100), 1, 3));
    }
}
