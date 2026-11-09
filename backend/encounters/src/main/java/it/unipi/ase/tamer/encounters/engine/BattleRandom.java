package it.unipi.ase.tamer.encounters.engine;

import java.util.SplittableRandom;

/**
 * The single random source of a battle: one {@link SplittableRandom} built from the seed, used first by
 * {@link WildSelector} and then by the {@link Battle}. Not thread-safe (the service serialises actions per battle).
 */
public final class BattleRandom {

    private final long seed;
    private final SplittableRandom random;

    public BattleRandom(long seed) {
        this.seed = seed;
        this.random = new SplittableRandom(seed);
    }

    public long seed() {
        return seed;
    }

    /** Uniform in [0, bound). */
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    /** Uniform in [0, 1). */
    public double nextDouble() {
        return random.nextDouble();
    }

    public boolean nextBoolean() {
        return random.nextBoolean();
    }
}
