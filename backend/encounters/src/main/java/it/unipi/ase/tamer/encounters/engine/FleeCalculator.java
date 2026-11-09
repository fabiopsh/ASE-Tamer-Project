package it.unipi.ase.tamer.encounters.engine;

/** {@code p_flee = min(0.95, speed_eff(active) / (speed_eff(active) + speed_eff(wild)) + 0.25)}. */
public final class FleeCalculator {

    public static final double FLEE_BONUS = 0.25;
    public static final double FLEE_MAX = 0.95;

    private FleeCalculator() {
    }

    public static double probability(double activeSpeedEff, double wildSpeedEff) {
        double sum = activeSpeedEff + wildSpeedEff;
        double ratio = sum <= 0 ? 0.5 : activeSpeedEff / sum;
        return Math.min(FLEE_MAX, ratio + FLEE_BONUS);
    }

    /** One RNG call: success if the roll is below {@code p}. */
    public static boolean roll(double p, BattleRandom rng) {
        return rng.nextDouble() < p;
    }
}
