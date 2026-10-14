package it.unipi.ase.tamers.encounters.engine;

/**
 * A monster in battle. Identity fields are immutable, the rest is battle state owned by
 * {@link BattleEngine} (which always works on a copy, so inputs can be reused).
 */
public final class Combatant {
    private final String name;
    private final String element;
    private final int level;
    private final Stats stats;
    private final Ability baseAbility;
    private final double hpMultiplier;
    private final int maxHp;

    // battle state (package-private: touched only by the engine)
    int hp;
    Ability ability;
    double attackMod = 1.0;
    boolean stunned;
    int actions;
    int hitsReceived;
    boolean healUsed;
    boolean reviveUsed;

    private Combatant(String name, String element, int level, Stats stats, Ability ability, double hpMultiplier) {
        this.name = name;
        this.element = element;
        this.level = level;
        this.stats = stats;
        this.baseAbility = ability;
        this.hpMultiplier = hpMultiplier;
        this.maxHp = maxHpFor(stats.hp(), hpMultiplier);
        this.hp = maxHp;
        this.ability = ability;
    }

    /** Battle HP: {@code 2 * hp + 10}, then multiplied (guardian: 2.5). */
    public static int maxHpFor(int hpStat, double multiplier) {
        return (int) Math.round((2 * hpStat + 10) * multiplier);
    }

    public static Combatant of(String name, String element, int level, Stats stats, Ability ability) {
        return new Combatant(name, element, level, stats, ability, 1.0);
    }

    public static Combatant of(String name, String element, int level, Stats stats, Ability ability,
                               double hpMultiplier) {
        return new Combatant(name, element, level, stats, ability, hpMultiplier);
    }

    /** Fresh copy with full HP and no battle state. */
    public Combatant fresh() {
        return new Combatant(name, element, level, stats, baseAbility, hpMultiplier);
    }

    public String name() { return name; }
    public String element() { return element; }
    public int level() { return level; }
    public Stats stats() { return stats; }
    public int maxHp() { return maxHp; }
    public int hp() { return hp; }
    public Ability ability() { return ability; }
    public boolean alive() { return hp > 0; }
}
