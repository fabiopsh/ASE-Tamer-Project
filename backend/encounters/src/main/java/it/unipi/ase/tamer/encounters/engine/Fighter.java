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
        return combatant.ability();
    }
}
