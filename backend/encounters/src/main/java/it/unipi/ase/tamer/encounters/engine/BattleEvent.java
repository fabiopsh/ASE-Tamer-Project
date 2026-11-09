package it.unipi.ase.tamer.encounters.engine;

import java.util.List;

/**
 * One event of a round. A single flat record so that it serialises directly to the JSON of the plan: fields that
 * do not belong to the event type are {@code null} (serialise with {@code NON_NULL} inclusion).
 *
 * <ul>
 *   <li>{@code attack}: side (of the attacker), attacker, target (names), damage (HP actually removed),
 *       effectiveness ({@code strong|neutral|weak}, after ignore_disadvantage), dodged, abilities (names of the
 *       abilities that changed this attack);</li>
 *   <li>{@code switch}: side {@code team}, from, to (positions);</li>
 *   <li>{@code auto_switch}: to (position);</li>
 *   <li>{@code knockout}: side, position (team only);</li>
 *   <li>{@code flee}: success;</li>
 *   <li>{@code end}: outcome ({@code victory|defeat|fled}).</li>
 * </ul>
 */
public record BattleEvent(String type, String side, String attacker, String target, Integer damage,
                          String effectiveness, Boolean dodged, List<String> abilities, Integer position,
                          Integer from, Integer to, Boolean success, String outcome) {

    public static final String ATTACK = "attack";
    public static final String SWITCH = "switch";
    public static final String AUTO_SWITCH = "auto_switch";
    public static final String KNOCKOUT = "knockout";
    public static final String FLEE = "flee";
    public static final String END = "end";

    public static final String SIDE_TEAM = "team";
    public static final String SIDE_WILD = "wild";

    public BattleEvent {
        abilities = abilities == null ? null : List.copyOf(abilities);
    }

    public static BattleEvent attack(String side, String attacker, String target, int damage, String effectiveness,
                                     boolean dodged, List<String> abilities) {
        return new BattleEvent(ATTACK, side, attacker, target, damage, effectiveness, dodged, abilities,
                null, null, null, null, null);
    }

    public static BattleEvent switchMember(int from, int to) {
        return new BattleEvent(SWITCH, SIDE_TEAM, null, null, null, null, null, null, null, from, to, null, null);
    }

    public static BattleEvent autoSwitch(int to) {
        return new BattleEvent(AUTO_SWITCH, null, null, null, null, null, null, null, null, null, to, null, null);
    }

    public static BattleEvent knockout(String side, Integer position) {
        return new BattleEvent(KNOCKOUT, side, null, null, null, null, null, null, position, null, null, null, null);
    }

    public static BattleEvent flee(boolean success) {
        return new BattleEvent(FLEE, null, null, null, null, null, null, null, null, null, null, success, null);
    }

    public static BattleEvent end(Outcome outcome) {
        return new BattleEvent(END, null, null, null, null, null, null, null, null, null, null, null,
                outcome.wireName());
    }
}
