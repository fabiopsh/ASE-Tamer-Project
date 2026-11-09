package it.unipi.ase.tamer.encounters.engine;

import static it.unipi.ase.tamer.encounters.engine.Fixtures.ability;
import static it.unipi.ase.tamer.encounters.engine.Fixtures.battle;
import static it.unipi.ase.tamer.encounters.engine.Fixtures.fighter;
import static it.unipi.ase.tamer.encounters.engine.Fixtures.firstAttack;
import static it.unipi.ase.tamer.encounters.engine.Fixtures.mon;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AbilityEngineTest {

    private final AbilityEngine engine = new AbilityEngine();

    private static Fighter holder(Ability a, int speed) {
        Fighter f = fighter(mon("Holder", "time", 100, 50, 50, speed, a), Fighter.TEAM);
        f.firstRound = 1;
        return f;
    }

    private static Fighter opponent(int speed) {
        Fighter f = fighter(mon("Opp", "time", 100, 50, 50, speed), Fighter.WILD);
        f.firstRound = 1;
        return f;
    }

    // ---- damage_boost ------------------------------------------------------------------------------------------

    @Test
    void damageBoostAlways() {
        Fighter h = holder(ability("Resonance", "damage_boost", 15, "always"), 50);
        assertEquals(1.15, engine.attackModifiers(h, opponent(50), 1).boost(), 1e-9);
        assertEquals(1.15, engine.attackModifiers(h, opponent(50), 9).boost(), 1e-9);
    }

    @Test
    void damageBoostFirstTurnInBattle() {
        Ability opening = ability("Opening Strike", "damage_boost", 20, "first_turn");
        Combatant w = mon("W", "time", 500, 50, 100, 1);
        Battle with = battle(11, w, mon("A", "time", 200, 100, 100, 50, opening));
        Battle without = battle(11, w, mon("A", "time", 200, 100, 100, 50));
        BattleEvent r1With = firstAttack(with.apply(Action.attack()), "team");
        BattleEvent r1Without = firstAttack(without.apply(Action.attack()), "team");
        assertTrue(r1With.damage() > r1Without.damage());
        assertEquals(List.of("Opening Strike"), r1With.abilities());
        BattleEvent r2With = firstAttack(with.apply(Action.attack()), "team");
        BattleEvent r2Without = firstAttack(without.apply(Action.attack()), "team");
        assertEquals(r2Without.damage(), r2With.damage());
        assertEquals(List.of(), r2With.abilities());
    }

    @Test
    void firstTurnIsTheFirstRoundOnTheFieldOfASwitchedInMonster() {
        Ability opening = ability("Opening Strike", "damage_boost", 20, "first_turn");
        Battle b = battle(3, mon("W", "time", 500, 10, 100, 1), mon("A", "time", 200, 50, 50, 50),
                mon("B", "time", 200, 50, 50, 50, opening));
        b.apply(Action.attack());
        b.apply(Action.attack());
        b.apply(Action.switchTo(2)); // round 3: B on the field, does not attack, its first_turn is spent
        BattleEvent r4 = firstAttack(b.apply(Action.attack()), "team");
        assertEquals(List.of(), r4.abilities());
    }

    @Test
    void damageBoostHpBelow50() {
        Fighter h = holder(ability("Recall", "damage_boost", 20, "hp_below_50"), 50);
        assertEquals(1.0, engine.attackModifiers(h, opponent(50), 1).boost(), 1e-9);
        h.hp = h.maxHp / 2; // exactly 50% is not below
        assertEquals(1.0, engine.attackModifiers(h, opponent(50), 1).boost(), 1e-9);
        h.hp = h.maxHp / 2 - 1;
        assertEquals(1.2, engine.attackModifiers(h, opponent(50), 1).boost(), 1e-9);
    }

    @Test
    void damageBoostVsFaster() {
        Fighter h = holder(ability("Crushing Pull", "damage_boost", 25, "vs_faster"), 50);
        assertEquals(1.25, engine.attackModifiers(h, opponent(51), 1).boost(), 1e-9);
        assertEquals(1.0, engine.attackModifiers(h, opponent(50), 1).boost(), 1e-9);
        assertEquals(1.0, engine.attackModifiers(h, opponent(10), 1).boost(), 1e-9);
        // vs_faster uses speed_eff: an opponent with Momentum (×1.15) becomes faster.
        Fighter momentum = fighter(mon("Opp", "time", 100, 50, 50, 45,
                ability("Momentum", "speed_boost", 15, "always")), Fighter.WILD);
        assertEquals(1.25, engine.attackModifiers(h, momentum, 1).boost(), 1e-9);
    }

    // ---- damage_reduction --------------------------------------------------------------------------------------

    @Test
    void damageReductionAlwaysInBattle() {
        Ability hide = ability("Thick Hide", "damage_reduction", 10, "always");
        Combatant w = mon("W", "time", 500, 100, 100, 100);
        BattleEvent with = firstAttack(battle(4, w, mon("A", "time", 300, 50, 100, 1, hide)).apply(Action.attack()), "wild");
        BattleEvent without = firstAttack(battle(4, w, mon("A", "time", 300, 50, 100, 1)).apply(Action.attack()), "wild");
        assertTrue(with.damage() < without.damage());
        assertEquals(List.of("Thick Hide"), with.abilities());
    }

    @Test
    void damageReductionHpBelow50() {
        Fighter h = holder(ability("Stubborn", "damage_reduction", 20, "hp_below_50"), 50);
        assertEquals(1.0, engine.attackModifiers(opponent(50), h, 1).reduction(), 1e-9);
        h.hp = 10;
        assertEquals(0.8, engine.attackModifiers(opponent(50), h, 1).reduction(), 1e-9);
    }

    // ---- speed_boost -------------------------------------------------------------------------------------------

    @Test
    void speedBoostAlwaysChangesOrder() {
        Ability momentum = ability("Momentum", "speed_boost", 15, "always");
        Combatant w = mon("W", "time", 300, 30, 100, 110);
        assertEquals("wild", battle(1, w, mon("A", "time", 300, 30, 100, 100)).apply(Action.attack()).events().get(0).side());
        assertEquals("team", battle(1, w, mon("A", "time", 300, 30, 100, 100, momentum)).apply(Action.attack()).events().get(0).side());
    }

    @Test
    void speedBoostFirstTurnOnlyInFirstRound() {
        Ability haste = ability("Haste", "speed_boost", 25, "first_turn");
        Battle b = battle(1, mon("W", "time", 300, 30, 100, 110), mon("A", "time", 300, 30, 100, 100, haste));
        assertEquals("team", b.apply(Action.attack()).events().get(0).side());
        assertEquals("wild", b.apply(Action.attack()).events().get(0).side());
        Fighter h = holder(haste, 100);
        assertEquals(125, engine.speedEff(h, opponent(1), 1), 1e-9);
        assertEquals(100, engine.speedEff(h, opponent(1), 2), 1e-9);
    }

    // ---- dodge -------------------------------------------------------------------------------------------------

    @Test
    void dodgeAlways() {
        Ability reflexes = ability("Quick Reflexes", "dodge", 10, "always");
        Combatant w = mon("W", "time", 500, 10, 100, 1, reflexes);
        int dodged = 0;
        for (long seed = 0; seed < 300; seed++) {
            BattleEvent e = firstAttack(battle(seed, w, mon("A", "time", 300, 50, 50, 50)).apply(Action.attack()), "team");
            // The dodge roll is the first RNG call of the round (no tie): dodged iff roll < 0.10.
            assertEquals(new BattleRandom(seed).nextDouble() < 0.10, e.dodged());
            if (e.dodged()) {
                dodged++;
                assertEquals(0, e.damage());
                assertEquals(List.of("Quick Reflexes"), e.abilities());
            } else {
                assertTrue(e.damage() > 0);
                assertEquals(List.of(), e.abilities());
            }
        }
        assertTrue(dodged > 10 && dodged < 60, "dodged " + dodged);
    }

    @Test
    void dodgeFirstTurnOnlyOnFirstAttackReceived() {
        Ability unseen = ability("Unseen", "dodge", 25, "first_turn");
        Combatant w = mon("W", "time", 500, 10, 100, 1, unseen);
        int firstDodged = 0;
        for (long seed = 0; seed < 200; seed++) {
            Battle b = battle(seed, w, mon("A", "time", 300, 50, 50, 50));
            if (firstAttack(b.apply(Action.attack()), "team").dodged()) {
                firstDodged++;
            }
            for (int round = 2; round <= 4; round++) {
                assertFalse(firstAttack(b.apply(Action.attack()), "team").dodged());
            }
        }
        assertTrue(firstDodged > 20, "dodged " + firstDodged);
    }

    @Test
    void dodgeHpAbove50() {
        Fighter h = holder(ability("Lucid Shift", "dodge", 20, "hp_above_50"), 50);
        assertEquals(0.2, engine.attackModifiers(opponent(50), h, 1).dodgeChance(), 1e-9);
        h.hp = h.maxHp / 2;
        assertEquals(0.0, engine.attackModifiers(opponent(50), h, 1).dodgeChance(), 1e-9);
    }

    // ---- weaken ------------------------------------------------------------------------------------------------

    @Test
    void weakenAlwaysInBattle() {
        Ability quiet = ability("Absolute Quiet", "weaken", 35, "always");
        Combatant w = mon("W", "time", 500, 100, 100, 100);
        BattleEvent with = firstAttack(battle(4, w, mon("A", "time", 300, 50, 100, 1, quiet)).apply(Action.attack()), "wild");
        BattleEvent without = firstAttack(battle(4, w, mon("A", "time", 300, 50, 100, 1)).apply(Action.attack()), "wild");
        assertTrue(with.damage() < without.damage());
        assertEquals(List.of("Absolute Quiet"), with.abilities());
        assertEquals(0.65, engine.attackModifiers(opponent(50), holder(quiet, 50), 1).attackFactor(), 1e-9);
    }

    @Test
    void weakenFirstTurn() {
        Fighter h = holder(ability("Muffle", "weaken", 15, "first_turn"), 50);
        assertEquals(0.85, engine.attackModifiers(opponent(50), h, 1).attackFactor(), 1e-9);
        assertEquals(1.0, engine.attackModifiers(opponent(50), h, 2).attackFactor(), 1e-9);
        // The holder's own attack is not weakened.
        assertEquals(1.0, engine.attackModifiers(h, opponent(50), 1).attackFactor(), 1e-9);
    }

    // ---- ignore_disadvantage is covered in BattleTest; P2 and unknown --------------------------------------------

    @Test
    void p2EffectsAndUnknownTriggersAreNoOps() {
        for (String type : List.of("heal", "reflect", "stun", "revive", "random_boost", "copy_ability",
                "invert_advantage", "something_new")) {
            assertFalse(engine.handler(type).implemented(), type);
        }
        for (String type : List.of("damage_boost", "damage_reduction", "speed_boost", "dodge", "weaken",
                "ignore_disadvantage")) {
            assertTrue(engine.handler(type).implemented(), type);
        }
        // A P1 effect with a P2 trigger is never active.
        Fighter h = holder(ability("Odd", "damage_boost", 50, "on_hit"), 50);
        assertEquals(1.0, engine.attackModifiers(h, opponent(50), 1).boost(), 1e-9);

        Combatant w = mon("W", "time", 60, 30, 40, 40, ability("Hourglass Reversal", "revive", 50, "once"));
        Combatant a = mon("A", "time", 40, 30, 40, 41, ability("Lullaby", "stun", 15, "on_attack"));
        Combatant c = mon("C", "time", 40, 30, 40, 39, ability("Spiteful Thorns", "reflect", 10, "on_hit"));
        Combatant d = mon("D", "time", 40, 30, 40, 38, ability("Mystery", "mystery", 10, "sometimes"));
        Battle withAbilities = Fixtures.attackUntilEnd(battle(9, w, a, c, d));
        Battle plain = Fixtures.attackUntilEnd(battle(9, strip(w), strip(a), strip(c), strip(d)));
        assertTrue(withAbilities.isFinished());
        assertEquals(plain.log(), withAbilities.log());
    }

    private static Combatant strip(Combatant c) {
        return new Combatant(c.id(), c.monsterId(), c.name(), c.element(), c.rarity(), c.level(), c.hp(), c.attack(),
                c.defense(), c.speed(), null);
    }
}
