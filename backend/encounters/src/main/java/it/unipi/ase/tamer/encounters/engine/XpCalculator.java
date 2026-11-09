package it.unipi.ase.tamer.encounters.engine;

/** {@code xp_victory = round(10 × wild_level × {common 1, rare 1.5, legendary 2})}, given to every team member. */
public final class XpCalculator {

    public static final int XP_PER_LEVEL = 10;

    private XpCalculator() {
    }

    public static double rarityMultiplier(String rarity) {
        if ("legendary".equals(rarity)) {
            return 2.0;
        }
        if ("rare".equals(rarity)) {
            return 1.5;
        }
        return 1.0;
    }

    public static int victoryXp(int wildLevel, String wildRarity) {
        return (int) Math.round(XP_PER_LEVEL * wildLevel * rarityMultiplier(wildRarity));
    }
}
