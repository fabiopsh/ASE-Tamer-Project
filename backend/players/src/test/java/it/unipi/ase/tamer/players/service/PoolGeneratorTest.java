package it.unipi.ase.tamer.players.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unipi.ase.tamer.players.client.MonsterCandidate;
import it.unipi.ase.tamer.players.error.ApiException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PoolGeneratorTest {

    @Test
    void picksTenDistinctCommonsWithSeededRandom() {
        List<MonsterCandidate> catalogue = new ArrayList<>();
        for (int id = 1; id <= 60; id++) {
            catalogue.add(new MonsterCandidate(id, "dream", "common"));
        }
        catalogue.add(new MonsterCandidate(99, "chaos", "legendary"));
        var pool = PoolGenerator.select(catalogue, new Random(1234));
        assertEquals(10, pool.size());
        assertEquals(10, new HashSet<>(pool).size());
        assertTrue(pool.stream().allMatch(id -> id >= 1 && id <= 60));
        assertEquals(pool, PoolGenerator.select(catalogue, new Random(1234)));
    }

    @Test
    void rejectsIncompleteCatalogue() {
        List<MonsterCandidate> catalogue = List.of(new MonsterCandidate(1, "dream", "common"));
        ApiException error = assertThrows(ApiException.class,
                () -> PoolGenerator.select(catalogue, new Random(1)));
        assertEquals(503, error.status());
    }
}
