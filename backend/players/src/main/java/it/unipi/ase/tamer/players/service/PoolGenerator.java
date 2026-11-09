package it.unipi.ase.tamer.players.service;

import it.unipi.ase.tamer.players.client.MonsterCandidate;
import it.unipi.ase.tamer.players.error.ApiException;
import it.unipi.ase.tamer.players.error.ErrorCodes;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Picks ten distinct common species; the result is persisted once per run. */
@ApplicationScoped
public class PoolGenerator {

    public List<Integer> generate(List<MonsterCandidate> candidates) {
        return select(candidates, new SecureRandom());
    }

    public static List<Integer> select(List<MonsterCandidate> candidates, Random random) {
        List<Integer> ids = candidates.stream()
                .filter(m -> "common".equals(m.rarity()))
                .map(MonsterCandidate::monsterId)
                .distinct()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (ids.size() < 10) {
            throw new ApiException(503, ErrorCodes.DEPENDENCY_UNAVAILABLE,
                    "The monster catalogue is not ready.");
        }
        Collections.shuffle(ids, random);
        return List.copyOf(ids.subList(0, 10));
    }
}
