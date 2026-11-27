package it.unipi.ase.tamer.encounters.engine;

/** Battle HP from the effective HP stat. */
public final class HpCalculator {

    public static final double WILD_HP_MULTIPLIER = 1.5;
    /** Tuned for engine v2 (EN-6): keeps stages 4–7 bosses fair with P2 legendary abilities active. */
    public static final double BOSS_HP_MULTIPLIER = 1.25;

    private HpCalculator() {
    }

    /** {@code 2 × hp + 10}. */
    public static int teamMaxHp(int hp) {
        return 2 * hp + 10;
    }

    /** {@code round((2 × hp + 10) × 1.5)}; boss also {@code × 1.5} (plan: 2.5, tuned with BalanceSimulation). */
    public static int wildMaxHp(int hp, boolean boss) {
        return (int) Math.round(teamMaxHp(hp) * (boss ? BOSS_HP_MULTIPLIER : WILD_HP_MULTIPLIER));
    }
}
