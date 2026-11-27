package it.unipi.ase.tamer.encounters.engine;

import static it.unipi.ase.tamer.encounters.engine.Fixtures.ability;
import static it.unipi.ase.tamer.encounters.engine.Fixtures.battle;
import static it.unipi.ase.tamer.encounters.engine.Fixtures.fighter;
import static it.unipi.ase.tamer.encounters.engine.Fixtures.firstAttack;
import static it.unipi.ase.tamer.encounters.engine.Fixtures.mon;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import it.unipi.ase.tamer.encounters.service.JsonSupport;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    // ---- P2 effects (engine v2) and unknown types --------------------------------------------------------------

    @Test
    void allCatalogEffectsImplementedAndUnknownTypesAreNoOps() {
        for (String type : List.of("damage_boost", "damage_reduction", "speed_boost", "dodge", "weaken",
                "ignore_disadvantage", "heal", "reflect", "stun", "revive", "random_boost", "copy_ability",
                "invert_advantage")) {
            assertTrue(engine.handler(type).implemented(), type);
        }
        for (String type : List.of("something_new", "mystery")) {
            assertFalse(engine.handler(type).implemented(), type);
        }
        // A passive P1 effect with an event trigger is never active.
        Fighter h = holder(ability("Odd", "damage_boost", 50, "on_hit"), 50);
        assertEquals(1.0, engine.attackModifiers(h, opponent(50), 1).boost(), 1e-9);

        // Unknown effect types and unknown triggers are complete no-ops.
        Combatant w = mon("W", "time", 60, 30, 40, 40, ability("Unknown", "mystery", 10, "sometimes"));
        Combatant a = mon("A", "time", 40, 30, 40, 41, ability("Other", "something_new", 25, "always"));
        Battle withUnknown = Fixtures.attackUntilEnd(battle(9, w, a));
        Battle plain = Fixtures.attackUntilEnd(battle(9, strip(w), strip(a)));
        assertTrue(withUnknown.isFinished());
        assertEquals(plain.log(), withUnknown.log());
    }

    @Test
    void healTriggersOnceBelowHalfHpAndNotOnKo() {
        Ability rewind = ability("Rewind", "heal", 25, "hp_below_50");
        // Team maxHp = 2*45 + 10 = 100. Wild hits for ~55-65 damage -> drops below 50 while alive -> heals 25.
        Combatant teamMon = mon("Chronos", "time", 45, 1, 20, 1, rewind);
        Combatant wildMon = mon("Hitter", "time", 500, 120, 100, 100);
        Battle b = battle(7, wildMon, teamMon);
        RoundResult r1 = b.apply(Action.attack());
        List<BattleEvent> heals1 = r1.events().stream()
                .filter(e -> BattleEvent.ABILITY.equals(e.type()) && "heal".equals(e.effect())).toList();
        assertEquals(1, heals1.size());
        assertEquals("team", heals1.get(0).side());
        assertEquals("Rewind", heals1.get(0).ability());
        assertEquals("Chronos", heals1.get(0).actor());
        assertEquals(25, heals1.get(0).amount());

        // Subsequent hits below 50% must NOT heal a second time.
        RoundResult r2 = b.apply(Action.attack());
        assertTrue(r2.events().stream().noneMatch(e -> BattleEvent.ABILITY.equals(e.type()) && "heal".equals(e.effect())));

        // One-shot KO directly to 0 HP must NOT trigger heal.
        Combatant glass = mon("Glass", "time", 1, 1, 1, 1, rewind);
        Combatant nuke = mon("Nuke", "time", 500, 500, 100, 100);
        RoundResult koRound = battle(1, nuke, glass).apply(Action.attack());
        assertTrue(koRound.events().stream().noneMatch(e -> BattleEvent.ABILITY.equals(e.type()) && "heal".equals(e.effect())));
        assertEquals(BattleEvent.KNOCKOUT, koRound.events().get(koRound.events().size() - 2).type());
    }

    @Test
    void reflectDamagesAttackerAndCanKnockOutAttacker() {
        Ability chorus = ability("Endless Chorus", "reflect", 40, "on_hit");
        // Frail glass-cannon attacker (maxHp = 12) hits high-HP wild with 40% reflect -> dies to reflect!
        Combatant glassCannon = mon("Cannon", "time", 1, 100, 1, 100);
        Combatant thornyWild = mon("Choraleth", "time", 500, 1, 10, 1, chorus);
        Battle b = battle(5, thornyWild, glassCannon);
        RoundResult r = b.apply(Action.attack());

        List<BattleEvent> reflects = r.events().stream()
                .filter(e -> BattleEvent.ABILITY.equals(e.type()) && "reflect".equals(e.effect())).toList();
        assertEquals(1, reflects.size());
        assertEquals("wild", reflects.get(0).side());
        assertEquals("Endless Chorus", reflects.get(0).ability());
        assertTrue(reflects.get(0).amount() >= 1);
        assertEquals(Outcome.DEFEAT, b.outcome());
        assertTrue(r.events().stream().anyMatch(e -> BattleEvent.KNOCKOUT.equals(e.type()) && "team".equals(e.side())));
    }

    @Test
    void stunSkipsNextActionAndIsDeterministic() {
        Ability dreamweaver = ability("Dreamweaver", "stun", 100, "on_attack");
        Combatant teamMon = mon("Dreamer", "time", 300, 40, 50, 100, dreamweaver);
        Combatant wildMon = mon("Target", "time", 300, 40, 50, 10);
        Battle b1 = battle(42, wildMon, teamMon);
        Battle b2 = battle(42, wildMon, teamMon);

        // Round 1: team hits first and stuns wild (100% chance) -> wild skips its round-1 attack!
        RoundResult r1 = b1.apply(Action.attack());
        assertEquals(r1, b2.apply(Action.attack()));
        List<BattleEvent> attacks = r1.events().stream().filter(e -> BattleEvent.ATTACK.equals(e.type())).toList();
        assertEquals(1, attacks.size(), "stunned wild must skip its attack");
        assertEquals("team", attacks.get(0).side());
        List<BattleEvent> stunEvents = r1.events().stream()
                .filter(e -> BattleEvent.ABILITY.equals(e.type()) && "stun".equals(e.effect())).toList();
        assertEquals(2, stunEvents.size(), "one stun infliction + one stun skip");
        assertNull(stunEvents.get(0).amount());
        assertNull(stunEvents.get(1).amount());
        assertTrue(stunEvents.get(1).description().contains("skips"));

        // Probabilistic stun (35%): verify both stun and non-stun occur across seeds.
        Ability probStun = ability("Dreamweaver", "stun", 35, "on_attack");
        Combatant probTeam = mon("Dreamer", "time", 300, 40, 50, 100, probStun);
        int stunned = 0;
        for (long seed = 0; seed < 100; seed++) {
            RoundResult r = battle(seed, wildMon, probTeam).apply(Action.attack());
            if (r.events().stream().anyMatch(e -> BattleEvent.ABILITY.equals(e.type()) && "stun".equals(e.effect()))) {
                stunned++;
            }
        }
        assertTrue(stunned > 15 && stunned < 60, "expected ~35% stun rate, got " + stunned);
    }

    @Test
    void revivePreventsKoOnceOnly() {
        Ability hourglass = ability("Hourglass Reversal", "revive", 50, "once");
        // Wild HP = round((2*10+10)*1.5) = 45 -> 50% revive = floor(45*50/100) = 22.
        Combatant wildMon = mon("Chronarch", "time", 10, 1, 1, 1, hourglass);
        Combatant titan = mon("Titan", "time", 200, 500, 100, 100);
        Battle b = battle(3, wildMon, titan);

        RoundResult r1 = b.apply(Action.attack());
        assertFalse(b.isFinished(), "revive should prevent KO on first lethal hit");
        assertEquals(22, b.view().wild().hp());
        List<BattleEvent> revives = r1.events().stream()
                .filter(e -> BattleEvent.ABILITY.equals(e.type()) && "revive".equals(e.effect())).toList();
        assertEquals(1, revives.size());
        assertEquals(22, revives.get(0).amount());
        assertTrue(r1.events().stream().noneMatch(e -> BattleEvent.KNOCKOUT.equals(e.type())));

        // Second lethal hit MUST KO normally (no second revive).
        RoundResult r2 = b.apply(Action.attack());
        assertEquals(Outcome.VICTORY, b.outcome());
        assertTrue(r2.events().stream().noneMatch(e -> BattleEvent.ABILITY.equals(e.type()) && "revive".equals(e.effect())));
        assertTrue(r2.events().stream().anyMatch(e -> BattleEvent.KNOCKOUT.equals(e.type()) && "wild".equals(e.side())));
    }

    @Test
    void randomBoostSelectsAllThreeStatsDeterministically() {
        Ability entropy = ability("Entropy", "random_boost", 30, "always");
        Combatant teamMon = mon("Chaos", "time", 300, 50, 50, 50, entropy);
        Combatant wildMon = mon("Wild", "time", 300, 50, 50, 55);
        Set<String> seenStats = new HashSet<>();
        for (long seed = 0; seed < 40; seed++) {
            Battle b1 = battle(seed, wildMon, teamMon);
            Battle b2 = battle(seed, wildMon, teamMon);
            RoundResult r1 = b1.apply(Action.attack());
            assertEquals(r1, b2.apply(Action.attack()));
            BattleEvent boost = r1.events().stream()
                    .filter(e -> BattleEvent.ABILITY.equals(e.type()) && "random_boost".equals(e.effect()))
                    .findFirst().orElseThrow();
            assertEquals(30, boost.amount());
            if (boost.description().contains("attack")) {
                seenStats.add("attack");
            } else if (boost.description().contains("defense")) {
                seenStats.add("defense");
            } else if (boost.description().contains("speed")) {
                seenStats.add("speed");
                // 50 * 1.3 = 65 > 55 -> team must act first when speed is boosted!
                BattleEvent firstAtk = r1.events().stream().filter(e -> BattleEvent.ATTACK.equals(e.type()))
                        .findFirst().orElseThrow();
                assertEquals("team", firstAtk.side());
            }
        }
        assertEquals(Set.of("attack", "defense", "speed"), seenStats);
    }

    @Test
    void copyAbilityCopiesOpponentOnFirstTurnAndAvoidsRecursion() {
        Ability archive = ability("Eternal Archive", "copy_ability", 100, "first_turn");
        Ability resonance = ability("Resonance", "damage_boost", 15, "always");
        Combatant copier = mon("Archivist", "time", 300, 60, 50, 100, archive);
        Combatant target = mon("Echo", "time", 300, 40, 50, 10, resonance);

        Battle b = battle(11, target, copier);
        RoundResult r1 = b.apply(Action.attack());
        List<BattleEvent> copyEvents = r1.events().stream()
                .filter(e -> BattleEvent.ABILITY.equals(e.type()) && "copy_ability".equals(e.effect())).toList();
        assertEquals(1, copyEvents.size());
        assertEquals("Eternal Archive", copyEvents.get(0).ability());
        // Copied Resonance applies immediately on round 1 AND persists on round 2.
        assertEquals(List.of("Resonance"), firstAttack(r1, "team").abilities());
        RoundResult r2 = b.apply(Action.attack());
        assertTrue(r2.events().stream().noneMatch(e -> BattleEvent.ABILITY.equals(e.type()) && "copy_ability".equals(e.effect())));
        assertEquals(List.of("Resonance"), firstAttack(r2, "team").abilities());

        // Mutual copy_ability does NOT recurse or fail.
        Combatant copier2 = mon("Mirror", "time", 300, 40, 50, 10, archive);
        Battle mirror = battle(11, copier2, copier);
        RoundResult mr = mirror.apply(Action.attack());
        assertTrue(mr.events().stream().noneMatch(e -> BattleEvent.ABILITY.equals(e.type()) && "copy_ability".equals(e.effect())));
    }

    @Test
    void invertAdvantageSwapsMatchupsRespectsIgnoreDisadvantageAndCancelsDouble() {
        Ability paradox = ability("Paradox Engine", "invert_advantage", 100, "always");
        Ability imprint = ability("Imprint", "ignore_disadvantage", 100, "always");
        // Normally silence is STRONG vs echo, and echo is WEAK vs silence.
        Combatant silencePlain = mon("Hush", "silence", 300, 50, 50, 100);
        Combatant echoParadox = mon("Void", "echo", 300, 50, 50, 10, paradox);
        RoundResult inv = battle(5, echoParadox, silencePlain).apply(Action.attack());
        assertEquals("weak", firstAttack(inv, "team").effectiveness());
        assertEquals(List.of("Paradox Engine"), firstAttack(inv, "team").abilities());
        assertEquals("strong", firstAttack(inv, "wild").effectiveness());
        assertEquals(List.of("Paradox Engine"), firstAttack(inv, "wild").abilities());

        // Attacker with ignore_disadvantage turns inverted strong->weak into neutral!
        Combatant silenceImprint = mon("Sage", "silence", 300, 50, 50, 100, imprint);
        RoundResult invAndIgnore = battle(5, echoParadox, silenceImprint).apply(Action.attack());
        assertEquals("neutral", firstAttack(invAndIgnore, "team").effectiveness());
        assertEquals(List.of("Imprint", "Paradox Engine"), firstAttack(invAndIgnore, "team").abilities());

        // Both sides having invert_advantage cancels out cleanly (back to normal strong/weak).
        Combatant silenceParadox = mon("HushVoid", "silence", 300, 50, 50, 100, paradox);
        RoundResult doubleInv = battle(5, echoParadox, silenceParadox).apply(Action.attack());
        assertEquals("strong", firstAttack(doubleInv, "team").effectiveness());
        assertEquals("weak", firstAttack(doubleInv, "wild").effectiveness());
        assertEquals(List.of(), firstAttack(doubleInv, "team").abilities());
    }

    @Test
    void abilityEventSerializesCleanlyWithNonNullAmount() {
        JsonNode withAmount = JsonSupport.standalone().valueToTree(
                BattleEvent.ability("team", "Second Wind", "heal", "Hero", "Restored 15 HP.", 15));
        assertEquals("ability", withAmount.get("type").asText());
        assertEquals("team", withAmount.get("side").asText());
        assertEquals("Second Wind", withAmount.get("ability").asText());
        assertEquals("heal", withAmount.get("effect").asText());
        assertEquals("Hero", withAmount.get("actor").asText());
        assertEquals("Restored 15 HP.", withAmount.get("description").asText());
        assertEquals(15, withAmount.get("amount").asInt());
        assertFalse(withAmount.has("damage"));

        JsonNode withoutAmount = JsonSupport.standalone().valueToTree(
                BattleEvent.ability("wild", "Lullaby", "stun", "Wild", "Stunned Hero.", null));
        assertNotNull(withoutAmount.get("description"));
        assertFalse(withoutAmount.has("amount"), "null amount must be omitted");
    }

    private static Combatant strip(Combatant c) {
        return new Combatant(c.id(), c.monsterId(), c.name(), c.element(), c.rarity(), c.level(), c.hp(), c.attack(),
                c.defense(), c.speed(), null);
    }
}
