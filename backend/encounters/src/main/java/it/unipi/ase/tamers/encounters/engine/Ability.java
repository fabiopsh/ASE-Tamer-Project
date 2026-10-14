package it.unipi.ase.tamers.encounters.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Ability as published by the monsters service (same fields as abilities.json). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Ability(
        int abilityId,
        String name,
        String scope,
        String element,
        String effectType,
        int effectValue,
        String trigger,
        String description) {

    // effect types
    public static final String DAMAGE_BOOST = "damage_boost";
    public static final String DAMAGE_REDUCTION = "damage_reduction";
    public static final String DODGE = "dodge";
    public static final String HEAL = "heal";
    public static final String SPEED_BOOST = "speed_boost";
    public static final String REFLECT = "reflect";
    public static final String STUN = "stun";
    public static final String WEAKEN = "weaken";
    public static final String IGNORE_DISADVANTAGE = "ignore_disadvantage";
    public static final String REVIVE = "revive";
    public static final String RANDOM_BOOST = "random_boost";
    public static final String INVERT_ADVANTAGE = "invert_advantage";
    public static final String COPY_ABILITY = "copy_ability";

    // triggers
    public static final String ALWAYS = "always";
    public static final String FIRST_TURN = "first_turn";
    public static final String HP_ABOVE_50 = "hp_above_50";
    public static final String HP_BELOW_50 = "hp_below_50";
    public static final String ON_HIT = "on_hit";
    public static final String ON_ATTACK = "on_attack";
    public static final String ONCE = "once";
    public static final String VS_FASTER = "vs_faster";

    public boolean is(String effect) {
        return effectType != null && effectType.equals(effect);
    }

    public static boolean is(Ability a, String effect) {
        return a != null && a.is(effect);
    }
}
