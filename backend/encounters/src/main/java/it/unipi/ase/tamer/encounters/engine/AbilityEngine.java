package it.unipi.ase.tamer.encounters.engine;

import java.util.Map;

/**
 * Ability logic: one {@link EffectHandler} per {@code effect_type}, plus trigger evaluation.
 *
 * <p>Engine v2 implements all 13 catalogue effect types:
 * <ul>
 *   <li>P1: {@code damage_boost}, {@code damage_reduction}, {@code speed_boost}, {@code dodge}, {@code weaken},
 *       {@code ignore_disadvantage};</li>
 *   <li>P2: {@code heal}, {@code reflect}, {@code stun}, {@code revive}, {@code random_boost}, {@code copy_ability},
 *       {@code invert_advantage}.</li>
 * </ul>
 * Unknown types remain no-ops.
 *
 * <p>Triggers (evaluated on the ability holder, at the moment of use):
 * <ul>
 *   <li>{@code always} – always;</li>
 *   <li>{@code first_turn} – the round in which the holder is first on the field in this encounter
 *       (for {@code dodge}: only the first attack it receives);</li>
 *   <li>{@code hp_below_50} / {@code hp_above_50} – {@code hp < 50%} / {@code hp > 50%} of max HP;</li>
 *   <li>{@code vs_faster} – opponent's {@code speed_eff} &gt; own (raw speeds when the effect is
 *       {@code speed_boost}, to avoid a circular definition);</li>
 *   <li>{@code on_hit} – evaluated when taking damage ({@code reflect});</li>
 *   <li>{@code on_attack} – evaluated after dealing damage ({@code stun});</li>
 *   <li>{@code once} – evaluated once per encounter when KO'd ({@code revive}).</li>
 * </ul>
 *
 * <p>When both active combatants carry {@code invert_advantage}, double inversion cancels out (XOR).
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

        /** Percentage of max HP restored when {@code heal} triggers (0 = not a heal effect). */
        default int healPercent(int value) {
            return 0;
        }

        /** Percentage of damage dealt returned to the attacker on hit (0 = not a reflect effect). */
        default int reflectPercent(int value) {
            return 0;
        }

        /** Chance in [0, 1] to stun the target after dealing damage. */
        default double stunChance(int value) {
            return 0.0;
        }

        /** Percentage of max HP restored when {@code revive} prevents a KO (0 = not a revive effect). */
        default int revivePercent(int value) {
            return 0;
        }

        /** Stat multiplier for {@code random_boost} (1.0 = not a random_boost effect). */
        default double randomBoostFactor(int value) {
            return 1.0;
        }

        /** Copies the opponent's active ability on {@code first_turn}. */
        default boolean copiesAbility() {
            return false;
        }

        /** Swaps elemental 1.5 ↔ 0.67 match-ups while on the field. */
        default boolean invertsAdvantage() {
            return false;
        }

        /** False for unknown / unimplemented effect types. */
        default boolean implemented() {
            return true;
        }
    }

    /** Placeholder for unknown effect types. */
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
            Map.entry("heal", new EffectHandler() {
                @Override
                public int healPercent(int v) {
                    return Math.max(0, v);
                }
            }),
            Map.entry("reflect", new EffectHandler() {
                @Override
                public int reflectPercent(int v) {
                    return Math.max(0, v);
                }
            }),
            Map.entry("stun", new EffectHandler() {
                @Override
                public double stunChance(int v) {
                    return Math.max(0, Math.min(1, v / 100.0));
                }
            }),
            Map.entry("revive", new EffectHandler() {
                @Override
                public int revivePercent(int v) {
                    return Math.max(0, v);
                }
            }),
            Map.entry("random_boost", new EffectHandler() {
                @Override
                public double randomBoostFactor(int v) {
                    return 1 + v / 100.0;
                }
            }),
            Map.entry("copy_ability", new EffectHandler() {
                @Override
                public boolean copiesAbility() {
                    return true;
                }
            }),
            Map.entry("invert_advantage", new EffectHandler() {
                @Override
                public boolean invertsAdvantage() {
                    return true;
                }
            }));

    /**
     * Modifiers of one attack.
     *
     * @param attackFactor         Π weaken factors of the defender, applied to the attacker's attack
     * @param attackerAttackFactor attacker's own attack stat multiplier (e.g. {@code random_boost} on attack)
     * @param defenseFactor        defender's defense stat multiplier (e.g. {@code random_boost} on defense)
     * @param boost                Π damage_boost factors of the attacker
     * @param reduction            Π damage_reduction factors of the defender
     * @param dodgeChance          defender's dodge chance (0 = no dodge roll)
     * @param ignoreDisadvantage   attacker ignores a weak match-up
     * @param attackerInverts      attacker's {@code invert_advantage} is active
     * @param defenderInverts      defender's {@code invert_advantage} is active
     */
    record AttackModifiers(double attackFactor, double attackerAttackFactor, double defenseFactor,
                           double boost, double reduction, double dodgeChance, boolean ignoreDisadvantage,
                           boolean attackerInverts, boolean defenderInverts) {

        /** Net inversion: double inversion cancels out cleanly (XOR). */
        boolean invertAdvantage() {
            return attackerInverts ^ defenderInverts;
        }
    }

    public EffectHandler handler(String effectType) {
        return HANDLERS.getOrDefault(effectType, NO_OP);
    }

    /** Speed after the holder's active {@code speed_boost} and round {@code random_boost} effects. */
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
        if ("speed".equals(self.randomBoostStat)) {
            speed *= self.randomBoostFactor;
        }
        return speed;
    }

    AttackModifiers attackModifiers(Fighter attacker, Fighter defender, int turn) {
        double attackFactor = 1;
        double attackerAttackFactor = "attack".equals(attacker.randomBoostStat) ? attacker.randomBoostFactor : 1.0;
        double defenseFactor = "defense".equals(defender.randomBoostStat) ? defender.randomBoostFactor : 1.0;
        double boost = 1;
        double reduction = 1;
        double dodge = 0;
        boolean ignore = false;
        boolean attackerInverts = false;
        boolean defenderInverts = false;
        Ability own = attacker.ability();
        if (own != null && triggerHolds(own, attacker, defender, turn)) {
            EffectHandler h = handler(own.effectType());
            boost *= h.outgoingDamageFactor(own.effectValue());
            ignore = h.ignoresDisadvantage();
            attackerInverts = h.invertsAdvantage();
        }
        Ability other = defender.ability();
        if (other != null && triggerHolds(other, defender, attacker, turn)) {
            EffectHandler h = handler(other.effectType());
            attackFactor *= h.opponentAttackFactor(other.effectValue());
            reduction *= h.incomingDamageFactor(other.effectValue());
            dodge = h.dodgeChance(other.effectValue());
            defenderInverts = h.invertsAdvantage();
        }
        return new AttackModifiers(attackFactor, attackerAttackFactor, defenseFactor, boost, reduction, dodge,
                ignore, attackerInverts, defenderInverts);
    }

    int healPercent(Ability ability, Fighter self) {
        int pct = handler(ability.effectType()).healPercent(ability.effectValue());
        if (pct <= 0) {
            return 0;
        }
        return switch (ability.trigger()) {
            case "hp_below_50" -> self.hp > 0 && self.hp * 2 < self.maxHp ? pct : 0;
            case "always" -> self.hp > 0 && self.hp < self.maxHp ? pct : 0;
            default -> 0;
        };
    }

    int reflectPercent(Ability ability) {
        int pct = handler(ability.effectType()).reflectPercent(ability.effectValue());
        if (pct <= 0) {
            return 0;
        }
        return "on_hit".equals(ability.trigger()) || "always".equals(ability.trigger()) ? pct : 0;
    }

    double stunChance(Ability ability) {
        double chance = handler(ability.effectType()).stunChance(ability.effectValue());
        if (chance <= 0) {
            return 0;
        }
        return "on_attack".equals(ability.trigger()) || "always".equals(ability.trigger()) ? chance : 0;
    }

    int revivePercent(Ability ability) {
        int pct = handler(ability.effectType()).revivePercent(ability.effectValue());
        if (pct <= 0) {
            return 0;
        }
        return "once".equals(ability.trigger()) || "always".equals(ability.trigger()) ? pct : 0;
    }

    boolean isRandomBoostActive(Fighter self, Fighter opponent, int turn) {
        Ability a = self.ability();
        return a != null && handler(a.effectType()).randomBoostFactor(a.effectValue()) != 1.0
                && triggerHolds(a, self, opponent, turn);
    }

    boolean shouldCopyAbility(Ability ability, Fighter self, Fighter opponent, int turn) {
        return handler(ability.effectType()).copiesAbility() && triggerHolds(ability, self, opponent, turn);
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
