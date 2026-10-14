package it.unipi.ase.tamers.encounters.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Generation rules for the encounter board of a stage. */
public final class BoardGenerator {
    public static final int MAX_STAGE = 10;
    public static final int WILD_PER_BOARD = 3;
    /**
     * Battle HP multipliers of the enemies (applied on top of {@code 2 * hp + 10}).
     *
     * <p>DEVIATION from the contract (guardian x2.5, wild x1): with the contract damage formula a level-5
     * rare guardian with x2.5 HP is unbeatable for a team of three level-1 commons (below 1% wins even for the
     * weakest rare), and level 1-3 wild commons are beaten 99-100% of the time. These two constants were
     * tuned with BalanceTest (see README): wild 94% and guardian ~40% win rate for that team.
     */
    public static final double WILD_HP_MULTIPLIER = 1.5;
    public static final double GUARDIAN_HP_MULTIPLIER = 1.4;

    /**
     * Fixed guardian element for stages 1..10 (stages 1-5 rare, 6-10 legendary); the species is the one
     * of that rarity and element with the lowest base attack (ties: lowest monster_id), so that early
     * guardians are tough walls rather than glass cannons.
     */
    private static final List<String> GUARDIAN_ELEMENTS = List.of(
            "silence", "echo", "memory", "time", "chaos",
            "gravity", "dream", "silence", "echo", "memory");

    /** Minimal catalog view needed to generate offers. */
    public record CatalogEntry(int monsterId, String rarity, String element, int baseAttack) {}

    /** A monster species at a given level. */
    public record Spawn(int monsterId, int level) {}

    private BoardGenerator() {}

    /** Stage used for the board: after the last stage the board keeps offering stage-10 wild monsters. */
    public static int boardStage(int playerStage) {
        return Math.min(Math.max(playerStage, 1), MAX_STAGE);
    }

    public static int minWildLevel(int stage) { return 3 * stage - 2; }
    public static int maxWildLevel(int stage) { return 3 * stage; }
    public static int guardianLevel(int stage) { return 3 * stage + 2; }

    /** Probability that a wild monster of this stage is rare (otherwise common). */
    public static double rareChance(int stage) {
        if (stage <= 3) {
            return 0.0;
        }
        return stage <= 6 ? 0.30 : 0.70;
    }

    public static String guardianRarity(int stage) {
        return stage <= 5 ? "rare" : "legendary";
    }

    public static String guardianElement(int stage) {
        return GUARDIAN_ELEMENTS.get(boardStage(stage) - 1);
    }

    /** difficulty: guardian = boss; wild level 3s-2 easy, 3s-1 normal, 3s hard. */
    public static String difficulty(boolean guardian, int stage, int level) {
        if (guardian) {
            return "boss";
        }
        int offset = level - minWildLevel(stage);
        return offset <= 0 ? "easy" : offset == 1 ? "normal" : "hard";
    }

    /** The guardian of a stage: same for every player. */
    public static CatalogEntry guardian(List<CatalogEntry> catalog, int stage) {
        int s = boardStage(stage);
        String rarity = guardianRarity(s);
        String element = guardianElement(s);
        return catalog.stream()
                .filter(c -> rarity.equals(c.rarity()) && element.equals(c.element()))
                .min(Comparator.comparingInt(CatalogEntry::baseAttack).thenComparingInt(CatalogEntry::monsterId))
                .orElseThrow(() -> new IllegalStateException(
                        "no " + rarity + " " + element + " monster in catalog for stage " + s));
    }

    /**
     * One wild offer. Species in {@code exclude} are never picked (distinct species on a board);
     * species in {@code avoid} are skipped when possible (prefer not to repeat recent ones).
     */
    public static Spawn wild(List<CatalogEntry> catalog, int stage, Set<Integer> exclude, Set<Integer> avoid,
                             RandomGenerator rnd) {
        int s = boardStage(stage);
        String rarity = rnd.nextDouble() < rareChance(s) ? "rare" : "common";
        Optional<CatalogEntry> pick = pick(catalog, rarity, exclude, avoid, rnd);
        if (pick.isEmpty()) {
            pick = pick(catalog, "common", exclude, avoid, rnd);
        }
        CatalogEntry species = pick.orElseThrow(() -> new IllegalStateException("catalog has no wild species"));
        int level = minWildLevel(s) + rnd.nextInt(maxWildLevel(s) - minWildLevel(s) + 1);
        return new Spawn(species.monsterId(), level);
    }

    /** A whole wild board with distinct species (used by tests and the first generation). */
    public static List<Spawn> wildBoard(List<CatalogEntry> catalog, int stage, int count, Set<Integer> exclude,
                                        Set<Integer> avoid, RandomGenerator rnd) {
        Set<Integer> taken = new HashSet<>(exclude);
        List<Spawn> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Spawn spawn = wild(catalog, stage, taken, avoid, rnd);
            taken.add(spawn.monsterId());
            out.add(spawn);
        }
        return out;
    }

    private static Optional<CatalogEntry> pick(List<CatalogEntry> catalog, String rarity, Set<Integer> exclude,
                                               Set<Integer> avoid, RandomGenerator rnd) {
        List<CatalogEntry> pool = catalog.stream()
                .filter(c -> rarity.equals(c.rarity()) && !exclude.contains(c.monsterId()))
                .sorted(Comparator.comparingInt(CatalogEntry::monsterId))
                .toList();
        List<CatalogEntry> preferred = pool.stream().filter(c -> !avoid.contains(c.monsterId())).toList();
        List<CatalogEntry> use = preferred.isEmpty() ? pool : preferred;
        return use.isEmpty() ? Optional.empty() : Optional.of(use.get(rnd.nextInt(use.size())));
    }
}
