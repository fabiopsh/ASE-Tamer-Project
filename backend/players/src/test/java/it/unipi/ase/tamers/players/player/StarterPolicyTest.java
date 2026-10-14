package it.unipi.ase.tamers.players.player;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StarterPolicyTest {
    private static final List<Integer> COMMONS = IntStream.rangeClosed(1, 60).boxed().toList();
    private static final List<Integer> POOL = List.of(3, 8, 12, 15, 20, 21, 30, 41, 50, 59);

    @Test
    void drawsTenDistinctIdsFromTheCandidates() {
        List<Integer> pool = StarterPolicy.drawPool(COMMONS, new java.util.Random(1));
        assertEquals(10, pool.size());
        assertEquals(10, new HashSet<>(pool).size());
        assertTrue(COMMONS.containsAll(pool));
    }

    @Test
    void sameSeedGivesSamePoolDifferentSeedsGiveDifferentPools() {
        List<Integer> a = StarterPolicy.drawPool(COMMONS, new java.util.Random(42));
        List<Integer> b = StarterPolicy.drawPool(COMMONS, new java.util.Random(42));
        List<Integer> c = StarterPolicy.drawPool(COMMONS, new java.util.Random(43));
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void exactlyTenCandidatesAreAllReturned() {
        List<Integer> ten = IntStream.rangeClosed(1, 10).boxed().toList();
        assertEquals(new HashSet<>(ten), new HashSet<>(StarterPolicy.drawPool(ten, new java.util.Random(7))));
    }

    @Test
    void duplicatesInTheCandidateListAreCollapsed() {
        List<Integer> withDuplicates = new ArrayList<>(IntStream.rangeClosed(1, 9).boxed().toList());
        withDuplicates.addAll(List.of(1, 2, 3, 4));
        assertThrows(IllegalArgumentException.class,
                () -> StarterPolicy.drawPool(withDuplicates, new java.util.Random(1)));
    }

    @Test
    void tooFewCandidatesIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> StarterPolicy.drawPool(List.of(1, 2, 3), RandomGenerator.getDefault()));
    }

    @Test
    void drawDoesNotModifyTheInput() {
        List<Integer> input = new ArrayList<>(COMMONS);
        StarterPolicy.drawPool(input, new java.util.Random(5));
        assertEquals(COMMONS, input);
    }

    @Test
    void validChoiceHasNoErrors() {
        assertTrue(StarterPolicy.validate(List.of(3, 20, 59), POOL, false).isEmpty());
    }

    @Test
    void wrongNumberOfStartersIsRejected() {
        assertEquals(1, StarterPolicy.validate(List.of(3, 8), POOL, false).size());
        assertEquals(1, StarterPolicy.validate(List.of(3, 8, 12, 15), POOL, false).size());
        assertEquals(1, StarterPolicy.validate(List.of(), POOL, false).size());
        assertEquals(1, StarterPolicy.validate(null, POOL, false).size());
        assertTrue(StarterPolicy.validate(List.of(3, 8), POOL, false).get(0).contains("exactly 3"));
    }

    @Test
    void duplicatesAreRejected() {
        List<String> errors = StarterPolicy.validate(List.of(3, 3, 8), POOL, false);
        assertEquals(List.of("duplicate monster 3"), errors);
    }

    @Test
    void tripleDuplicateIsReportedOnce() {
        assertEquals(List.of("duplicate monster 3"), StarterPolicy.validate(List.of(3, 3, 3), POOL, false));
    }

    @Test
    void monstersOutsideThePoolAreRejected() {
        List<String> errors = StarterPolicy.validate(List.of(3, 99, 100), POOL, false);
        assertEquals(2, errors.size());
        assertTrue(errors.contains("monster 99 is not in your starter pool"));
        assertTrue(errors.contains("monster 100 is not in your starter pool"));
    }

    @Test
    void errorsAreCumulative() {
        List<String> errors = StarterPolicy.validate(List.of(99, 99, 3), POOL, false);
        assertEquals(2, errors.size());
    }

    @Test
    void nullElementIsRejected() {
        List<Integer> withNull = new ArrayList<>(List.of(3, 8));
        withNull.add(null);
        assertEquals(1, StarterPolicy.validate(withNull, POOL, false).size());
    }

    @Test
    void alreadyChosenIsReportedFirst() {
        assertEquals(List.of(StarterPolicy.ALREADY_CHOSEN), StarterPolicy.validate(List.of(3, 8, 12), POOL, true));
        assertEquals(List.of(StarterPolicy.ALREADY_CHOSEN), StarterPolicy.validate(List.of(), POOL, true));
    }
}
