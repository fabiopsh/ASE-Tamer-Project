package it.unipi.ase.tamers.encounters.engine;

import java.util.random.RandomGenerator;

/** XP and capture rules. */
public final class Rewards {
    public static final int XP_PER_ENEMY_LEVEL = 10;
    public static final int GUARDIAN_XP_FACTOR = 2;
    public static final double DEFEAT_XP_FACTOR = 0.30;
    public static final double CAPTURE_COMMON = 0.60;
    public static final double CAPTURE_RARE = 0.35;

    private Rewards() {}

    /** XP given to every team member on victory: 10 x enemy level (guardian x2). */
    public static int victoryXp(int enemyLevel, boolean guardian) {
        return XP_PER_ENEMY_LEVEL * enemyLevel * (guardian ? GUARDIAN_XP_FACTOR : 1);
    }

    /** XP given to every team member on defeat: 30% of the victory XP, rounded (at least 1). */
    public static int defeatXp(int enemyLevel, boolean guardian) {
        return Math.max(1, (int) Math.round(victoryXp(enemyLevel, guardian) * DEFEAT_XP_FACTOR));
    }

    public static int xp(boolean victory, int enemyLevel, boolean guardian) {
        return victory ? victoryXp(enemyLevel, guardian) : defeatXp(enemyLevel, guardian);
    }

    /** Capture probability on victory; the guardian is always captured. */
    public static double captureChance(String rarity, boolean guardian) {
        if (guardian) {
            return 1.0;
        }
        return switch (rarity == null ? "" : rarity) {
            case "common" -> CAPTURE_COMMON;
            case "rare" -> CAPTURE_RARE;
            default -> 0.0;
        };
    }

    public static boolean rollCapture(double chance, RandomGenerator rnd) {
        return chance >= 1.0 || (chance > 0 && rnd.nextDouble() < chance);
    }
}
