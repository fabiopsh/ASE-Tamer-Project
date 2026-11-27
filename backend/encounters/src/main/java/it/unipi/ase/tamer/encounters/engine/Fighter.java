package it.unipi.ase.tamer.encounters.engine;

/** Mutable per-battle state of one combatant (engine-internal). */
final class Fighter {

    static final String TEAM = "team";
    static final String WILD = "wild";

    final Combatant combatant;
    final String side;
    /** Team position (1-based); 0 for the wild monster. */
    final int position;
    final int maxHp;
    int hp;
    /** Round in which it was first on the field during the attack phase; 0 = never yet. */
    int firstRound;
    /** Attacks received so far (dodged ones included). */
    int attacksReceived;
    /** Whether one-time {@code heal} ({@code hp_below_50}) has already triggered in this battle. */
    boolean healed;
    /** Whether one-time {@code revive} ({@code once}) has already triggered in this battle. */
    boolean revived;
    /** Set by opponent's {@code stun}; consumed on this fighter's next attack attempt. */
    boolean stunned;
    /** Name of the ability that inflicted {@link #stunned}. */
    String stunnedBy;
    /** Active ability copied via {@code copy_ability} on {@code first_turn}, if any. */
    Ability copiedAbility;
    /** Stat boosted this round by {@code random_boost} ({@code attack|defense|speed}), or {@code null}. */
    String randomBoostStat;
    /** Multiplier for {@link #randomBoostStat} in the current round. */
    double randomBoostFactor = 1.0;

    Fighter(Combatant combatant, String side, int position, int maxHp) {
        this.combatant = combatant;
        this.side = side;
        this.position = position;
        this.maxHp = maxHp;
        this.hp = maxHp;
    }

    boolean alive() {
        return hp > 0;
    }

    Ability ability() {
        return copiedAbility != null ? copiedAbility : combatant.ability();
    }

    void clearRandomBoost() {
        randomBoostStat = null;
        randomBoostFactor = 1.0;
    }
}
