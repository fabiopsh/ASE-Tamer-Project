package it.unipi.ase.tamers.encounters.engine;

import static it.unipi.ase.tamers.encounters.engine.Ability.*;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Pure, deterministic battle simulator: the same team, enemy and seed always produce the same log.
 *
 * <p>The team fights in order against a single enemy. Each round the faster combatant acts first
 * (ties: the player's side). A replacement entering after a KO acts from the next round on.
 */
public final class BattleEngine {
    public static final int MAX_ROUNDS = 100;
    public static final double CRIT_CHANCE = 0.0625;
    public static final double CRIT_MULTIPLIER = 1.5;
    public static final double VARIANCE_MIN = 0.85;
    public static final double VARIANCE_MAX = 1.0;

    private final ElementChart chart;
    private final SplittableRandom rnd;
    private final List<Combatant> team = new ArrayList<>();
    private final Combatant enemy;
    private final List<BattleEvent> log = new ArrayList<>();
    private int round = 1;
    private int current = 0;
    private Outcome result;

    private BattleEngine(List<Combatant> team, Combatant enemy, long seed, ElementChart chart) {
        for (Combatant c : team) {
            this.team.add(c.fresh());
        }
        this.enemy = enemy.fresh();
        this.rnd = new SplittableRandom(seed);
        this.chart = chart;
    }

    public static BattleResult simulate(List<Combatant> team, Combatant enemy, long seed) {
        return simulate(team, enemy, seed, ElementChart.DEFAULT);
    }

    public static BattleResult simulate(List<Combatant> team, Combatant enemy, long seed, ElementChart chart) {
        if (team == null || team.isEmpty()) {
            throw new IllegalArgumentException("team must not be empty");
        }
        return new BattleEngine(team, enemy, seed, chart).run();
    }

    // ------------------------------------------------------------------ main loop

    private BattleResult run() {
        Combatant first = team.get(0);
        emit("enter", first, enemy, null, null, null, null, first.name() + " enters the battle!");
        emit("enter", enemy, first, null, null, null, null, enemy.name() + " appears!");
        Ability firstAbility = first.ability;
        Ability enemyAbility = enemy.ability;
        copyIfAble(first, enemyAbility);
        copyIfAble(enemy, firstAbility);
        applyWeaken(first, enemy);
        applyWeaken(enemy, first);

        for (round = 1; round <= MAX_ROUNDS; round++) {
            int idx = current;
            boolean playerFirst = speed(team.get(idx)) >= speed(enemy);
            turn(playerFirst, idx);
            if (result == null) {
                turn(!playerFirst, idx);
            }
            if (result != null) {
                return new BattleResult(result, round, log);
            }
        }
        round = MAX_ROUNDS;
        result = Outcome.DEFEAT;
        emit("defeat", enemy, team.get(current), null, null, null, null,
                "The battle drags on for too long: the team gives up.");
        return new BattleResult(result, MAX_ROUNDS, log);
    }

    private void turn(boolean player, int startIdx) {
        if (player) {
            if (current != startIdx || !team.get(current).alive()) {
                return;
            }
            act(team.get(current), enemy);
        } else {
            if (!enemy.alive()) {
                return;
            }
            act(enemy, team.get(current));
        }
    }

    // ------------------------------------------------------------------ one action

    private void act(Combatant atk, Combatant def) {
        if (atk.stunned) {
            atk.stunned = false;
            emit("skip", atk, def, null, null, null, null, atk.name() + " is asleep and skips the turn.");
            return;
        }
        Ability aa = atk.ability;
        Ability da = def.ability;

        // dodge
        if (is(da, DODGE) && triggerOk(da, def, atk, true) && rnd.nextDouble() * 100 < da.effectValue()) {
            def.hitsReceived++;
            emit("dodge", atk, def, null, null, null, da.name(), def.name() + " dodges " + atk.name() + "'s attack!");
            endOfAction(atk);
            atk.actions++;
            return;
        }

        // damage
        double atkStat = atk.stats().attack() * atk.attackMod;
        double defStat = def.stats().defense();
        double base = atkStat * atkStat / (atkStat + defStat);
        double variance = VARIANCE_MIN + rnd.nextDouble() * (VARIANCE_MAX - VARIANCE_MIN);
        double elem = elementMultiplier(atk, def);
        double mod = 1.0;
        if (is(aa, DAMAGE_BOOST) && triggerOk(aa, atk, def, false)) {
            mod *= 1 + aa.effectValue() / 100.0;
        }
        if (is(aa, RANDOM_BOOST) && triggerOk(aa, atk, def, false)) {
            double lo = 1 - aa.effectValue() / 100.0;
            double hi = 1 + 2 * aa.effectValue() / 100.0;
            mod *= lo + rnd.nextDouble() * (hi - lo);
        }
        if (is(da, DAMAGE_REDUCTION) && triggerOk(da, def, atk, true)) {
            mod *= 1 - da.effectValue() / 100.0;
        }
        boolean crit = rnd.nextDouble() < CRIT_CHANCE;
        if (crit) {
            mod *= CRIT_MULTIPLIER;
        }
        int dmg = (int) Math.max(1, Math.round(base * variance * elem * mod));
        def.hitsReceived++;
        def.hp = Math.max(0, def.hp - dmg);

        String eff = ElementChart.label(elem);
        StringBuilder text = new StringBuilder(atk.name() + " hits " + def.name() + " for " + dmg + "!");
        if (crit) {
            text.append(" Critical hit!");
        }
        if (elem > 1) {
            text.append(" It's super effective!");
        } else if (elem < 1) {
            text.append(" It's not very effective...");
        }
        emit("attack", atk, def, dmg, crit, eff, null, text.toString());

        if (!def.alive()) {
            faint(def);
            if (result != null) {
                return;
            }
        } else {
            // reflect
            if (is(da, REFLECT) && triggerOk(da, def, atk, true)) {
                int back = (int) Math.max(1, Math.round(dmg * da.effectValue() / 100.0));
                atk.hp = Math.max(0, atk.hp - back);
                emit("ability", def, atk, back, null, null, da.name(),
                        def.name() + " reflects " + back + " damage back to " + atk.name() + "!");
                if (!atk.alive()) {
                    faint(atk);
                    return;
                }
                postDamage(atk);
            }
            // stun
            if (is(aa, STUN) && triggerOk(aa, atk, def, false) && rnd.nextDouble() * 100 < aa.effectValue()) {
                def.stunned = true;
                emit("stun", atk, def, null, null, null, aa.name(),
                        atk.name() + " puts " + def.name() + " to sleep for one action!");
            }
            postDamage(def);
        }
        if (atk.alive()) {
            endOfAction(atk);
        }
        atk.actions++;
    }

    // ------------------------------------------------------------------ KO, heal, entry

    /** Handles a combatant at 0 HP: revive if possible, otherwise faint and advance the battle. */
    private void faint(Combatant c) {
        Ability a = c.ability;
        if (is(a, REVIVE) && !c.reviveUsed) {
            c.reviveUsed = true;
            c.hp = Math.max(1, (int) Math.round(c.maxHp() * a.effectValue() / 100.0));
            emit("revive", c, null, null, null, null, a.name(),
                    c.name() + " turns back time and returns with " + c.hp() + " HP!");
            return;
        }
        c.hp = 0;
        emit("faint", c, null, null, null, null, null, c.name() + " is knocked out!");
        if (c == enemy) {
            result = Outcome.VICTORY;
            emit("victory", team.get(current), enemy, null, null, null, null, "The team wins!");
        } else if (current + 1 < team.size()) {
            current++;
            enterReplacement(team.get(current));
        } else {
            result = Outcome.DEFEAT;
            emit("defeat", enemy, c, null, null, null, null, "The whole team is knocked out...");
        }
    }

    private void enterReplacement(Combatant c) {
        enemy.attackMod = 1.0; // the previous weakening ended with the previous opponent
        emit("enter", c, enemy, null, null, null, null, c.name() + " enters the battle!");
        copyIfAble(c, enemy.ability);
        applyWeaken(c, enemy);
        applyWeaken(enemy, c);
    }

    private void copyIfAble(Combatant c, Ability opponentAbility) {
        if (is(c.ability, COPY_ABILITY) && opponentAbility != null && !opponentAbility.is(COPY_ABILITY)) {
            String via = c.ability.name();
            c.ability = opponentAbility;
            Combatant opp = c == enemy ? team.get(current) : enemy;
            emit("ability", c, opp, null, null, null, via,
                    c.name() + " copies " + opponentAbility.name() + "!");
        }
    }

    private void applyWeaken(Combatant holder, Combatant target) {
        Ability a = holder.ability;
        if (is(a, WEAKEN)) {
            target.attackMod *= 1 - a.effectValue() / 100.0;
            emit("ability", holder, target, null, null, null, a.name(),
                    holder.name() + " weakens " + target.name() + ": attack -" + a.effectValue() + "%.");
        }
    }

    /** Heal that triggers when HP drops below half (once per battle). */
    private void postDamage(Combatant c) {
        Ability a = c.ability;
        if (c.alive() && is(a, HEAL) && HP_BELOW_50.equals(a.trigger()) && !c.healUsed
                && c.hp * 2 < c.maxHp()) {
            c.healUsed = true;
            heal(c, a);
        }
    }

    /** Heal with any other trigger (e.g. always): at the end of each own action. */
    private void endOfAction(Combatant c) {
        Ability a = c.ability;
        if (c.alive() && is(a, HEAL) && !HP_BELOW_50.equals(a.trigger())
                && triggerOk(a, c, null, false) && c.hp < c.maxHp()) {
            heal(c, a);
        }
    }

    private void heal(Combatant c, Ability a) {
        int amount = Math.max(1, (int) Math.round(c.maxHp() * a.effectValue() / 100.0));
        c.hp = Math.min(c.maxHp(), c.hp + amount);
        emit("heal", c, null, null, null, null, a.name(), c.name() + " recovers HP with " + a.name() + "!");
    }

    // ------------------------------------------------------------------ rules helpers

    private boolean triggerOk(Ability a, Combatant self, Combatant opp, boolean defending) {
        String t = a.trigger();
        if (t == null) {
            return true;
        }
        return switch (t) {
            case FIRST_TURN -> defending ? self.hitsReceived == 0 : self.actions == 0;
            case HP_ABOVE_50 -> self.hp * 2 > self.maxHp();
            case HP_BELOW_50 -> self.hp * 2 < self.maxHp();
            case VS_FASTER -> opp != null && speed(opp) > speed(self);
            default -> true; // always, on_hit, on_attack, once
        };
    }

    /** Speed including speed_boost (a vs_faster boost is meaningless and ignored). */
    private double speed(Combatant c) {
        double s = c.stats().speed();
        Ability a = c.ability;
        if (is(a, SPEED_BOOST) && !VS_FASTER.equals(a.trigger()) && triggerOk(a, c, null, false)) {
            s *= 1 + a.effectValue() / 100.0;
        }
        return s;
    }

    private double elementMultiplier(Combatant atk, Combatant def) {
        double m = chart.multiplier(atk.element(), def.element());
        boolean invert = is(atk.ability, INVERT_ADVANTAGE) ^ is(def.ability, INVERT_ADVANTAGE);
        if (invert && m != 1.0) {
            m = m > 1.0 ? ElementChart.WEAK : ElementChart.STRONG;
        }
        if (m < 1.0 && is(atk.ability, IGNORE_DISADVANTAGE)) {
            m = 1.0;
        }
        return m;
    }

    private String ref(Combatant c) {
        return c == enemy ? "wild" : "team:" + team.indexOf(c);
    }

    private void emit(String type, Combatant actor, Combatant target, Integer damage, Boolean critical,
                      String effectiveness, String ability, String text) {
        log.add(new BattleEvent(round, type, ref(actor), target == null ? null : ref(target), damage, critical,
                effectiveness, ability, actor.hp(), target == null ? null : target.hp(), text));
    }
}
