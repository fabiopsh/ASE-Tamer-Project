package it.unipi.ase.tamer.encounters.engine;

/**
 * {@code damage = max(1, round(atk² / (atk + def) × 0.6 × elem × var × boost × reduction))}.
 */
public final class DamageCalculator {

    public static final double DAMAGE_SCALE = 0.6;
    public static final double VARIANCE_MIN = 0.9;
    public static final double VARIANCE_MAX = 1.1;

    private DamageCalculator() {
    }

    /**
     * @param atk       attack after weaken factors
     * @param def       defender's defense
     * @param elem      element multiplier (1.5 / 1 / 0.67)
     * @param variance  the {@code var} roll
     * @param boost     product of the attacker's damage_boost factors
     * @param reduction product of the defender's damage_reduction factors
     */
    public static int damage(double atk, double def, double elem, double variance, double boost, double reduction) {
        if (atk + def <= 0) {
            return 1;
        }
        double raw = atk * atk / (atk + def) * DAMAGE_SCALE * elem * variance * boost * reduction;
        return (int) Math.max(1, Math.round(raw));
    }

    /** Element multiplier with {@code ignore_disadvantage}: weak (0.67) becomes 1. */
    public static ElementChart.Effectiveness effectiveness(ElementChart chart, String attackerElement,
                                                           String defenderElement, boolean ignoreDisadvantage) {
        ElementChart.Effectiveness e = chart.effectiveness(attackerElement, defenderElement);
        return e == ElementChart.Effectiveness.WEAK && ignoreDisadvantage ? ElementChart.Effectiveness.NEUTRAL : e;
    }

    /** One RNG call: uniform in [0.9, 1.1). */
    public static double rollVariance(BattleRandom rng) {
        return VARIANCE_MIN + rng.nextDouble() * (VARIANCE_MAX - VARIANCE_MIN);
    }
}
