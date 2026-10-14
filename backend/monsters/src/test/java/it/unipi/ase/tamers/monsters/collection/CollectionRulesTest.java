package it.unipi.ase.tamers.monsters.collection;

import static org.junit.jupiter.api.Assertions.*;

import it.unipi.ase.tamers.monsters.common.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CollectionRulesTest {
    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private OwnedMonster owned(UUID player, int monsterId, int level, int xp, String obtainedAt) {
        OwnedMonster o = new OwnedMonster();
        o.ownedId = UUID.randomUUID();
        o.playerId = player;
        o.monsterId = monsterId;
        o.level = level;
        o.xp = xp;
        o.obtainedAt = Instant.parse(obtainedAt);
        return o;
    }

    @Test
    void levelSortIsDefaultAndPutsHighestFirst() {
        assertEquals(CollectionSort.LEVEL, CollectionSort.parse(null));
        assertEquals(CollectionSort.LEVEL, CollectionSort.parse(""));
        OwnedMonster a = owned(me, 5, 3, 10, "2026-01-01T00:00:00Z");
        OwnedMonster b = owned(me, 2, 7, 0, "2026-01-02T00:00:00Z");
        OwnedMonster c = owned(me, 9, 3, 40, "2026-01-03T00:00:00Z");
        List<OwnedMonster> sorted = new ArrayList<>(List.of(a, b, c));
        sorted.sort(CollectionSort.LEVEL.comparator());
        assertEquals(List.of(b, c, a), sorted);
    }

    @Test
    void recentSortPutsNewestFirst() {
        OwnedMonster a = owned(me, 1, 1, 0, "2026-01-01T00:00:00Z");
        OwnedMonster b = owned(me, 1, 1, 0, "2026-03-01T00:00:00Z");
        OwnedMonster c = owned(me, 1, 1, 0, "2026-02-01T00:00:00Z");
        List<OwnedMonster> sorted = new ArrayList<>(List.of(a, b, c));
        sorted.sort(CollectionSort.parse("recent").comparator());
        assertEquals(List.of(b, c, a), sorted);
    }

    @Test
    void speciesSortGroupsDuplicatesTogether() {
        OwnedMonster a = owned(me, 9, 1, 0, "2026-01-01T00:00:00Z");
        OwnedMonster b = owned(me, 2, 4, 0, "2026-01-01T00:00:00Z");
        OwnedMonster c = owned(me, 2, 6, 0, "2026-01-01T00:00:00Z");
        List<OwnedMonster> sorted = new ArrayList<>(List.of(a, b, c));
        sorted.sort(CollectionSort.SPECIES.comparator());
        assertEquals(List.of(c, b, a), sorted);
    }

    @Test
    void unknownSortIs400() {
        ApiException e = assertThrows(ApiException.class, () -> CollectionSort.parse("alphabetical"));
        assertEquals(400, e.status());
    }

    @Test
    void ownedKeepsRequestOrderAndSkipsForeignAndMissingIds() {
        OwnedMonster mine1 = owned(me, 1, 1, 0, "2026-01-01T00:00:00Z");
        OwnedMonster mine2 = owned(me, 2, 1, 0, "2026-01-01T00:00:00Z");
        OwnedMonster foreign = owned(other, 3, 1, 0, "2026-01-01T00:00:00Z");
        UUID missing = UUID.randomUUID();

        List<OwnedMonster> result = CollectionRules.ownedInRequestedOrder(me,
                List.of(mine2.ownedId, foreign.ownedId, missing, mine1.ownedId),
                List.of(mine1, mine2, foreign));

        assertEquals(List.of(mine2, mine1), result);
    }

    @Test
    void speciesOwnedCountsDistinctSpecies() {
        List<OwnedMonster> all = List.of(
                owned(me, 1, 1, 0, "2026-01-01T00:00:00Z"),
                owned(me, 1, 5, 0, "2026-01-01T00:00:00Z"),
                owned(me, 7, 1, 0, "2026-01-01T00:00:00Z"));
        assertEquals(2, CollectionRules.speciesOwned(all));
        assertEquals(0, CollectionRules.speciesOwned(List.of()));
    }

    @Test
    void comparatorsAreUsableTogetherWithNullTimestamps() {
        OwnedMonster a = owned(me, 1, 1, 0, "2026-01-01T00:00:00Z");
        OwnedMonster b = owned(me, 1, 1, 0, "2026-01-01T00:00:00Z");
        b.obtainedAt = null;
        Comparator<OwnedMonster> recent = CollectionSort.RECENT.comparator();
        assertTrue(recent.compare(a, b) < 0, "missing timestamp sorts last");
    }
}
