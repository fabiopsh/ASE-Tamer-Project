package it.unipi.ase.tamers.players.player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Pure rules for the starter pool (random draw) and for the starter choice (validation). */
public final class StarterPolicy {
    public static final int POOL_SIZE = 10;
    public static final int CHOICE_SIZE = 3;
    public static final String ALREADY_CHOSEN = "starters have already been chosen";

    private StarterPolicy() {}

    /** Draws {@link #POOL_SIZE} distinct ids at random (partial Fisher-Yates) from the given common species. */
    public static List<Integer> drawPool(List<Integer> commonIds, RandomGenerator random) {
        List<Integer> candidates = new ArrayList<>(new LinkedHashSet<>(commonIds));
        if (candidates.size() < POOL_SIZE) {
            throw new IllegalArgumentException(
                    "need at least " + POOL_SIZE + " distinct common monsters, got " + candidates.size());
        }
        for (int i = 0; i < POOL_SIZE; i++) {
            int j = i + random.nextInt(candidates.size() - i);
            Integer tmp = candidates.get(i);
            candidates.set(i, candidates.get(j));
            candidates.set(j, tmp);
        }
        return new ArrayList<>(candidates.subList(0, POOL_SIZE));
    }

    /** Returns the list of problems with the choice; empty means valid. */
    public static List<String> validate(List<Integer> choice, List<Integer> pool, boolean alreadyChosen) {
        if (alreadyChosen) {
            return List.of(ALREADY_CHOSEN);
        }
        if (choice == null || choice.size() != CHOICE_SIZE) {
            return List.of("exactly " + CHOICE_SIZE + " starters must be chosen");
        }
        if (choice.stream().anyMatch(java.util.Objects::isNull)) {
            return List.of("monster ids must not be null");
        }
        List<String> errors = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        Set<Integer> reported = new LinkedHashSet<>();
        for (Integer id : choice) {
            if (!seen.add(id) && reported.add(id)) {
                errors.add("duplicate monster " + id);
            }
        }
        for (Integer id : seen) {
            if (!pool.contains(id)) {
                errors.add("monster " + id + " is not in your starter pool");
            }
        }
        return errors;
    }
}
