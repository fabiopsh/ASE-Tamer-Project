package it.unipi.ase.tamer.encounters.engine;

import java.util.Map;

/**
 * Ability logic: one {@link EffectHandler} per {@code effect_type}, plus trigger evaluation.
 *
 * <p>P1 handlers: {@code damage_boost}, {@code damage_reduction}, {@code speed_boost}, {@code dodge},
 * {@code weaken}, {@code ignore_disadvantage}. P2 types ({@code heal}, {@code reflect}, {@code stun},
 * {@code revive}, {@code random_boost}, {@code copy_ability}, {@code invert_advantage}) are registered as
 * no-ops; unknown types are no-ops too.
 *
 * <p>Triggers (evaluated on the ability holder, at the moment of use):
 * <ul>
 *   <li>{@code always} – always;</li>
 *   <li>{@code first_turn} – the round in which the holder is first on the field in this encounter
 *       (for {@code dodge}: only the first attack it receives);</li>
 *   <li>{@code hp_below_50} / {@code hp_above_50} – {@code hp < 50%} / {@code hp > 50%} of max HP;</li>
 *   <li>{@code vs_faster} – opponent's {@code speed_eff} &gt; own (raw speeds when the effect is
 *       {@code speed_boost}, to avoid a circular definition);</li>
 *   <li>{@code on_hit}, {@code on_attack}, {@code once} and anything unknown – never active in engine v1.</li>
 * </ul>
 */
public final class AbilityEngine {

    /** How an effect changes the battle. Every method defaults to "no change". */
    public interface EffectHandler {
        /** Multiplier of the holder's speed. */
        default double speedFactor(int value) {
            return 1.0;
        }

        /** Multiplier of the damage the holder deals. */
        default double outgoingDamageFactor(int value) {
            return 1.0;
        }

        /** Multiplier of the damage the holder receives. */
        default double incomingDamageFactor(int value) {
            return 1.0;
        }

        /** Multiplier of the opponent's attack stat. */
        default double opponentAttackFactor(int value) {
            return 1.0;
        }

        /** Chance in [0, 1] to take no damage from an attack. */
        default double dodgeChance(int value) {
            return 0.0;
        }

        /** When attacking, a weak (0.67) match-up counts as neutral. */
        default boolean ignoresDisadvantage() {
            return false;
        }

        /** False for placeholders (P2 effects in engine v1). */
        default boolean implemented() {
            return true;
        }
    }

    /** Placeholder for effects not implemented in this engine version. */
    public static final EffectHandler NO_OP = new EffectHandler() {
        @Override
        public boolean implemented() {
            return false;
        }
    };

    private static final Map<String, EffectHandler> HANDLERS = Map.ofEntries(
            Map.entry("damage_boost", new EffectHandler() {
                @Override
                public double outgoingDamageFactor(int v) {
                    return 1 + v / 100.0;
                }
            }),
            Map.entry("damage_reduction", new EffectHandler() {
                @Override
                public double incomingDamageFactor(int v) {
                    return 1 - v / 100.0;
                }
            }),
            Map.entry("speed_boost", new EffectHandler() {
                @Override
                public double speedFactor(int v) {
                    return 1 + v / 100.0;
                }
            }),
            Map.entry("dodge", new EffectHandler() {
                @Override
                public double dodgeChance(int v) {
                    return Math.max(0, Math.min(1, v / 100.0));
                }
            }),
            Map.entry("weaken", new EffectHandler() {
                @Override
                public double opponentAttackFactor(int v) {
                    return 1 - v / 100.0;
                }
            }),
            Map.entry("ignore_disadvantage", new EffectHandler() {
                @Override
                public boolean ignoresDisadvantage() {
                    return true;
                }
            }),
            Map.entry("heal", NO_OP),
            Map.entry("reflect", NO_OP),
            Map.entry("stun", NO_OP),
            Map.entry("revive", NO_OP),
            Map.entry("random_boost", NO_OP),
            Map.entry("copy_ability", NO_OP),
            Map.entry("invert_advantage", NO_OP));

    /**
     * Modifiers of one attack.
     *
     * @param attackFactor       Π weaken factors of the defender, applied to the attacker's attack
     * @param boost              Π damage_boost factors of the attacker
     * @param reduction          Π damage_reduction factors of the defender
     * @param dodgeChance        defender's dodge chance (0 = no dodge roll)
     * @param ignoreDisadvantage attacker ignores a weak match-up
     */
    record AttackModifiers(double attackFactor, double boost, double reduction, double dodgeChance,
                           boolean ignoreDisadvantage) {
    }

    public EffectHandler handler(String effectType) {
        return HANDLERS.getOrDefault(effectType, NO_OP);
    }

    /** Speed after the holder's active speed_boost effects. */
    double speedEff(Fighter self, Fighter opponent, int turn) {
        double speed = self.combatant.speed();
        Ability a = self.ability();
        if (a != null) {
            double factor = handler(a.effectType()).speedFactor(a.effectValue());
            // Only speed effects are evaluated here; their vs_faster uses raw speeds, so there is no recursion.
            if (factor != 1.0 && triggerHolds(a, self, opponent, turn)) {
                speed *= factor;
            }
        }
        return speed;
    }

    AttackModifiers attackModifiers(Fighter attacker, Fighter defender, int turn) {
        double attackFactor = 1;
        double boost = 1;
        double reduction = 1;
        double dodge = 0;
        boolean ignore = false;
        Ability own = attacker.ability();
        if (own != null && triggerHolds(own, attacker, defender, turn)) {
            EffectHandler h = handler(own.effectType());
            boost *= h.outgoingDamageFactor(own.effectValue());
            ignore = h.ignoresDisadvantage();
        }
        Ability other = defender.ability();
        if (other != null && triggerHolds(other, defender, attacker, turn)) {
            EffectHandler h = handler(other.effectType());
            attackFactor *= h.opponentAttackFactor(other.effectValue());
            reduction *= h.incomingDamageFactor(other.effectValue());
            dodge = h.dodgeChance(other.effectValue());
        }
        return new AttackModifiers(attackFactor, boost, reduction, dodge, ignore);
    }

    boolean triggerHolds(Ability ability, Fighter self, Fighter opponent, int turn) {
        return switch (ability.trigger()) {
            case "always" -> true;
            case "first_turn" -> "dodge".equals(ability.effectType())
                    ? self.attacksReceived == 0
                    : self.firstRound == turn;
            case "hp_below_50" -> self.hp * 2 < self.maxHp;
            case "hp_above_50" -> self.hp * 2 > self.maxHp;
            case "vs_faster" -> "speed_boost".equals(ability.effectType())
                    ? opponent.combatant.speed() > self.combatant.speed()
                    : speedEff(opponent, self, turn) > speedEff(self, opponent, turn);
            default -> false;
        };
    }
}
