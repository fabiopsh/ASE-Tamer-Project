package it.unipi.ase.tamers.encounters.engine;

import static org.junit.jupiter.api.Assertions.*;

import it.unipi.ase.tamers.encounters.engine.BoardGenerator.CatalogEntry;
import it.unipi.ase.tamers.encounters.engine.BoardGenerator.Spawn;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class BoardGeneratorTest {
    private static final List<CatalogEntry> CATALOG = catalog();

    /** 60 common, 30 rare, 10 legendary spread over the 7 elements, ids 1..100. */
    private static List<CatalogEntry> catalog() {
        List<CatalogEntry> out = new ArrayList<>();
        List<String> cycle = ElementChart.DEFAULT_CYCLE;
        for (int id = 1; id <= 100; id++) {
            String rarity = id <= 60 ? "common" : id <= 90 ? "rare" : "legendary";
            out.add(new CatalogEntry(id, rarity, cycle.get(id % cycle.size()), 20 + (id * 7) % 50));
        }
        return out;
    }

    private static String rarityOf(int monsterId) {
        return CATALOG.stream().filter(c -> c.monsterId() == monsterId).findFirst().orElseThrow().rarity();
    }

    @Test
    void levelRangeAndDifficulty() {
        assertEquals(1, BoardGenerator.minWildLevel(1));
        assertEquals(3, BoardGenerator.maxWildLevel(1));
        assertEquals(5, BoardGenerator.guardianLevel(1));
        assertEquals(28, BoardGenerator.minWildLevel(10));
        assertEquals(30, BoardGenerator.maxWildLevel(10));
        assertEquals(32, BoardGenerator.guardianLevel(10));
        assertEquals("easy", BoardGenerator.difficulty(false, 4, 10));
        assertEquals("normal", BoardGenerator.difficulty(false, 4, 11));
        assertEquals("hard", BoardGenerator.difficulty(false, 4, 12));
        assertEquals("boss", BoardGenerator.difficulty(true, 4, 14));
    }

    @Test
    void stage1BoardIsCommonOnlyWithLevels1To3AndDistinctSpecies() {
        SplittableRandom rnd = new SplittableRandom(1);
        for (int i = 0; i < 200; i++) {
            List<Spawn> board = BoardGenerator.wildBoard(CATALOG, 1, 3, Set.of(), Set.of(), rnd);
            assertEquals(3, board.size());
            assertEquals(3, board.stream().map(Spawn::monsterId).distinct().count());
            for (Spawn s : board) {
                assertEquals("common", rarityOf(s.monsterId()));
                assertTrue(s.level() >= 1 && s.level() <= 3);
            }
        }
    }

    @Test
    void stage5MixesCommonAndRareAndNeverLegendary() {
        SplittableRandom rnd = new SplittableRandom(2);
        int rare = 0, total = 0;
        for (int i = 0; i < 1000; i++) {
            for (Spawn s : BoardGenerator.wildBoard(CATALOG, 5, 3, Set.of(), Set.of(), rnd)) {
                total++;
                String r = rarityOf(s.monsterId());
                assertNotEquals("legendary", r);
                if (r.equals("rare")) rare++;
                assertTrue(s.level() >= 13 && s.level() <= 15);
            }
        }
        double share = rare / (double) total;
        assertTrue(share > 0.25 && share < 0.35, "rare share " + share);
    }

    @Test
    void stage10IsMostlyRareAndStage11KeepsStage10Rules() {
        SplittableRandom rnd = new SplittableRandom(3);
        assertEquals(10, BoardGenerator.boardStage(11));
        int rare = 0, total = 0;
        for (int i = 0; i < 1000; i++) {
            for (Spawn s : BoardGenerator.wildBoard(CATALOG, 11, 3, Set.of(), Set.of(), rnd)) {
                total++;
                String r = rarityOf(s.monsterId());
                assertNotEquals("legendary", r);
                if (r.equals("rare")) rare++;
                assertTrue(s.level() >= 28 && s.level() <= 30);
            }
        }
        double share = rare / (double) total;
        assertTrue(share > 0.64 && share < 0.76, "rare share " + share);
    }

    @Test
    void rareChanceTable() {
        for (int s = 1; s <= 3; s++) assertEquals(0.0, BoardGenerator.rareChance(s));
        for (int s = 4; s <= 6; s++) assertEquals(0.30, BoardGenerator.rareChance(s));
        for (int s = 7; s <= 10; s++) assertEquals(0.70, BoardGenerator.rareChance(s));
    }

    @Test
    void excludedSpeciesAreNeverPickedAndAvoidedOnesAreSkippedWhenPossible() {
        SplittableRandom rnd = new SplittableRandom(4);
        Set<Integer> exclude = new HashSet<>();
        for (int i = 1; i <= 58; i++) exclude.add(i); // only 59 and 60 left among commons
        for (int i = 0; i < 100; i++) {
            Spawn s = BoardGenerator.wild(CATALOG, 1, exclude, Set.of(), rnd);
            assertTrue(s.monsterId() == 59 || s.monsterId() == 60);
        }
        for (int i = 0; i < 100; i++) {
            assertEquals(60, BoardGenerator.wild(CATALOG, 1, exclude, Set.of(59), rnd).monsterId());
        }
        // if everything left is avoided, avoiding is dropped rather than failing
        int fallback = BoardGenerator.wild(CATALOG, 1, exclude, Set.of(59, 60), new SplittableRandom(5)).monsterId();
        assertTrue(fallback == 59 || fallback == 60);
    }

    @Test
    void sameSeedSameBoard() {
        List<Spawn> a = BoardGenerator.wildBoard(CATALOG, 7, 3, Set.of(), Set.of(), new SplittableRandom(99));
        List<Spawn> b = BoardGenerator.wildBoard(CATALOG, 7, 3, Set.of(), Set.of(), new SplittableRandom(99));
        assertEquals(a, b);
    }

    @Test
    void guardianTableIsFixedRareThenLegendaryWithRotatingElements() {
        List<String> elements = new ArrayList<>();
        for (int stage = 1; stage <= 10; stage++) {
            CatalogEntry g = BoardGenerator.guardian(CATALOG, stage);
            assertEquals(stage <= 5 ? "rare" : "legendary", g.rarity(), "stage " + stage);
            assertEquals(BoardGenerator.guardianElement(stage), g.element());
            elements.add(g.element());
            // deterministic: lowest base attack of that rarity + element, ties by id
            CatalogEntry expected = CATALOG.stream()
                    .filter(c -> c.rarity().equals(g.rarity()) && c.element().equals(g.element()))
                    .min(java.util.Comparator.comparingInt(CatalogEntry::baseAttack)
                            .thenComparingInt(CatalogEntry::monsterId))
                    .orElseThrow();
            assertEquals(expected, g);
            assertEquals(g, BoardGenerator.guardian(CATALOG, stage));
        }
        assertEquals(5, elements.subList(0, 5).stream().distinct().count(), "first five stages use different elements");
        assertEquals(5, elements.subList(5, 10).stream().distinct().count(), "last five stages use different elements");
        assertEquals(BoardGenerator.guardian(CATALOG, 10), BoardGenerator.guardian(CATALOG, 11));
    }
}
