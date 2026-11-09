package it.unipi.ase.tamer.encounters.engine;

/** Attack order: higher {@code speed_eff} first; a tie is broken with one RNG call. */
public final class TurnOrder {

    private TurnOrder() {
    }

    /** True if the team's active monster attacks first. Consumes one RNG call only on a tie. */
    public static boolean teamFirst(double teamSpeedEff, double wildSpeedEff, BattleRandom rng) {
        if (teamSpeedEff != wildSpeedEff) {
            return teamSpeedEff > wildSpeedEff;
        }
        return rng.nextBoolean();
    }
}
