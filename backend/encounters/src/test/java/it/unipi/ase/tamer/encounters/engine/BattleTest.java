package it.unipi.ase.tamer.encounters.engine;

import static it.unipi.ase.tamer.encounters.engine.Fixtures.attackUntilEnd;
import static it.unipi.ase.tamer.encounters.engine.Fixtures.battle;
import static it.unipi.ase.tamer.encounters.engine.Fixtures.firstAttack;
import static it.unipi.ase.tamer.encounters.engine.Fixtures.mon;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BattleTest {

    private static final Combatant STRONG = mon("Titan", "time", 100, 100, 100, 100);
    private static final Combatant WEAKLING = mon("Mote", "dream", 10, 10, 10, 10);

    @Test
    void victoryIsPossible() {
        Battle b = attackUntilEnd(battle(1, WEAKLING, STRONG));
        assertEquals(Outcome.VICTORY, b.outcome());
        assertTrue(b.isFinished());
        BattleView v = b.view();
        assertEquals("finished", v.status());
        assertEquals("victory", v.outcome());
        assertEquals(0, v.wild().hp());
        assertEquals(50, b.xpGained()); // level 5, common
        assertEquals(50, v.xpGained());
        List<BattleEvent> last = b.lastRound().events();
        assertEquals(BattleEvent.KNOCKOUT, last.get(last.size() - 2).type());
        assertEquals("wild", last.get(last.size() - 2).side());
        assertEquals(BattleEvent.end(Outcome.VICTORY), last.get(last.size() - 1));
    }

    @Test
    void defeatIsPossible() {
        Combatant frail = mon("Frail", "time", 5, 1, 1, 1);
        Battle b = attackUntilEnd(battle(1, STRONG, frail, frail, frail));
        assertEquals(Outcome.DEFEAT, b.outcome());
        assertEquals(0, b.xpGained());
        BattleView v = b.view();
        assertTrue(v.team().stream().allMatch(BattleView.TeamMemberView::knockedOut));
        assertTrue(v.team().stream().noneMatch(BattleView.TeamMemberView::active));
        assertEquals("defeat", v.outcome());
        assertEquals(BattleEvent.end(Outcome.DEFEAT), b.lastRound().events().get(b.lastRound().events().size() - 1));
    }

    @Test
    void initialStateAndHp() {
        Battle b = battle(1, WEAKLING, STRONG);
        BattleView v = b.view();
        assertEquals("in_progress", v.status());
        assertEquals(0, v.turn());
        assertNull(v.lastRound());
        assertNull(v.outcome());
        assertEquals(210, v.team().get(0).maxHp());
        assertEquals(210, v.team().get(0).hp());
        assertEquals(45, v.wild().maxHp());
        assertTrue(v.team().get(0).active());
        assertEquals("Titan-id", v.team().get(0).ownedId());
        assertEquals(1, v.team().get(0).position());

        Battle boss = new Battle(1, new EncounterSpec(Fixtures.BOSS, WEAKLING), List.of(STRONG), ElementChart.defaults());
        assertEquals(HpCalculator.wildMaxHp(10, true), boss.view().wild().maxHp());
        assertTrue(boss.spec().isBoss());
    }

    @Test
    void elementShownInAttackEvents() {
        Combatant silence = mon("Hush", "silence", 200, 50, 50, 100);
        Combatant echo = mon("Ring", "echo", 200, 50, 50, 10);
        RoundResult r = battle(3, echo, silence).apply(Action.attack());
        assertEquals("strong", firstAttack(r, "team").effectiveness());
        assertEquals("weak", firstAttack(r, "wild").effectiveness());
        RoundResult n = battle(3, mon("Tick", "time", 200, 50, 50, 10), silence).apply(Action.attack());
        assertEquals("neutral", firstAttack(n, "team").effectiveness());
    }

    @Test
    void ignoreDisadvantage() {
        Ability imprint = Fixtures.ability("Imprint", "ignore_disadvantage", 100, "always");
        Combatant echoWithImprint = mon("Ring", "echo", 200, 50, 50, 100, imprint);
        Combatant echoPlain = mon("Ring", "echo", 200, 50, 50, 100);
        Combatant silence = mon("Hush", "silence", 200, 50, 50, 10);
        BattleEvent with = firstAttack(battle(5, silence, echoWithImprint).apply(Action.attack()), "team");
        BattleEvent without = firstAttack(battle(5, silence, echoPlain).apply(Action.attack()), "team");
        assertEquals("neutral", with.effectiveness());
        assertEquals(List.of("Imprint"), with.abilities());
        assertEquals("weak", without.effectiveness());
        assertEquals(List.of(), without.abilities());
        assertTrue(with.damage() > without.damage());
        // Not listed when there was no disadvantage to ignore.
        BattleEvent neutral = firstAttack(battle(5, mon("Tick", "time", 200, 50, 50, 10), echoWithImprint)
                .apply(Action.attack()), "team");
        assertEquals(List.of(), neutral.abilities());
    }

    @Test
    void fasterAttacksFirst() {
        Combatant fast = mon("Fast", "time", 200, 30, 50, 50);
        Combatant slow = mon("Slow", "time", 200, 30, 50, 40);
        assertEquals("team", battle(1, slow, fast).apply(Action.attack()).events().get(0).side());
        assertEquals("wild", battle(1, fast, slow).apply(Action.attack()).events().get(0).side());
    }

    @Test
    void speedTieBrokenByRng() {
        Combatant a = mon("A", "time", 200, 30, 50, 50);
        boolean sawTeam = false;
        boolean sawWild = false;
        for (long seed = 0; seed < 40; seed++) {
            String first = battle(seed, a, a).apply(Action.attack()).events().get(0).side();
            // Tie-break is the first RNG call of the round.
            assertEquals(new BattleRandom(seed).nextBoolean() ? "team" : "wild", first);
            sawTeam |= first.equals("team");
            sawWild |= first.equals("wild");
        }
        assertTrue(sawTeam && sawWild);
    }

    @Test
    void autoSwitchToFirstAliveByPosition() {
        Combatant sturdy = mon("Sturdy", "time", 100, 10, 100, 1);
        Combatant glass = mon("Glass", "time", 1, 10, 1, 1);
        Combatant wild = mon("Brute", "time", 300, 50, 100, 100);
        Battle b = battle(7, wild, sturdy, sturdy, glass);
        RoundResult r = b.apply(Action.switchTo(3));
        assertEquals(List.of(BattleEvent.SWITCH, BattleEvent.ATTACK, BattleEvent.KNOCKOUT, BattleEvent.AUTO_SWITCH),
                r.events().stream().map(BattleEvent::type).toList());
        assertEquals(BattleEvent.switchMember(1, 3), r.events().get(0));
        assertEquals("wild", r.events().get(1).side());
        assertEquals(BattleEvent.knockout("team", 3), r.events().get(2));
        assertEquals(BattleEvent.autoSwitch(1), r.events().get(3));
        assertEquals(1, b.activePosition());
        assertTrue(b.view().team().get(2).knockedOut());
        assertTrue(b.view().team().get(0).active());
        assertFalse(b.isFinished());
    }

    @Test
    void koMonsterDoesNotActLaterInTheRound() {
        Combatant glass = mon("Glass", "time", 1, 100, 1, 1);
        Combatant brute = mon("Brute", "time", 300, 50, 100, 100);
        RoundResult r = battle(2, brute, glass, glass).apply(Action.attack());
        assertEquals(1, r.events().stream().filter(e -> BattleEvent.ATTACK.equals(e.type())).count());
        assertEquals(BattleEvent.autoSwitch(2), r.events().get(r.events().size() - 1));
    }

    @Test
    void switchedInMonsterDoesNotAttack() {
        Combatant a = mon("A", "time", 200, 30, 50, 100);
        Battle b = battle(1, mon("W", "time", 200, 30, 50, 1), a, a);
        RoundResult r = b.apply(Action.switchTo(2));
        assertEquals(List.of(BattleEvent.SWITCH, BattleEvent.ATTACK), r.events().stream().map(BattleEvent::type).toList());
        assertEquals("wild", r.events().get(1).side());
        assertEquals(2, b.activePosition());
    }

    @Test
    void invalidSwitchesRejectedWithoutChangingState() {
        Combatant sturdy = mon("Sturdy", "time", 100, 10, 100, 1);
        Combatant glass = mon("Glass", "time", 1, 10, 1, 1);
        Battle b = battle(7, mon("Brute", "time", 300, 50, 100, 100), sturdy, sturdy, glass);
        b.apply(Action.switchTo(3)); // glass is KO'd, back to 1
        BattleView before = b.view();
        assertThrows(InvalidActionException.class, () -> b.apply(Action.switchTo(3))); // KO'd
        assertThrows(InvalidActionException.class, () -> b.apply(Action.switchTo(1))); // active
        assertThrows(InvalidActionException.class, () -> b.apply(Action.switchTo(4))); // out of range
        assertThrows(InvalidActionException.class, () -> b.apply(Action.switchTo(0)));
        assertEquals(before, b.view());
        assertEquals(1, b.actions().size());

        Battle small = battle(1, WEAKLING, STRONG);
        assertThrows(InvalidActionException.class, () -> small.apply(Action.switchTo(2)));
        assertThrows(InvalidActionException.class, () -> new Action(Action.Type.SWITCH, null));
        assertThrows(InvalidActionException.class, () -> new Action(Action.Type.ATTACK, 1));
        assertThrows(InvalidActionException.class, () -> Action.Type.fromWire("win"));
        assertEquals(Action.Type.FLEE, Action.Type.fromWire("flee"));
    }

    @Test
    void actingAfterTheEndIsRejected() {
        Battle b = attackUntilEnd(battle(1, WEAKLING, STRONG));
        int turn = b.turn();
        assertThrows(InvalidActionException.class, () -> b.apply(Action.attack()));
        assertThrows(InvalidActionException.class, () -> b.apply(Action.flee()));
        assertEquals(turn, b.turn());
    }

    @Test
    void fleeSuccessAndFailure() {
        Combatant a = mon("A", "time", 200, 30, 50, 50);
        Combatant w = mon("W", "time", 200, 30, 50, 50); // p_flee = 0.75
        boolean sawSuccess = false;
        boolean sawFailure = false;
        for (long seed = 0; seed < 40; seed++) {
            Battle b = battle(seed, w, a);
            RoundResult r = b.apply(Action.flee());
            boolean expected = new BattleRandom(seed).nextDouble() < 0.75; // flee roll is the first call
            if (expected) {
                sawSuccess = true;
                assertEquals(List.of(BattleEvent.flee(true), BattleEvent.end(Outcome.FLED)), r.events());
                assertEquals(Outcome.FLED, b.outcome());
                assertEquals("fled", b.view().outcome());
                assertEquals(0, b.xpGained());
                assertThrows(InvalidActionException.class, () -> b.apply(Action.attack()));
            } else {
                sawFailure = true;
                assertEquals(2, r.events().size());
                assertEquals(BattleEvent.flee(false), r.events().get(0));
                assertEquals(BattleEvent.ATTACK, r.events().get(1).type());
                assertEquals("wild", r.events().get(1).side());
                assertTrue(b.view().team().get(0).hp() < b.view().team().get(0).maxHp());
                assertFalse(b.isFinished());
            }
        }
        assertTrue(sawSuccess && sawFailure);
    }

    @Test
    void sameSeedSameActionsSameLog() {
        Ability dodge = Fixtures.ability("Quick Reflexes", "dodge", 10, "always");
        Ability boost = Fixtures.ability("Wild Surge", "damage_boost", 40, "hp_below_50");
        Combatant w = new Combatant(null, 9, "Wild", "echo", "rare", 6, 60, 40, 30, 40, dodge);
        Combatant a = mon("A", "silence", 30, 30, 30, 40, boost);
        Combatant c = mon("C", "dream", 30, 35, 25, 45);
        List<Action> script = List.of(Action.attack(), Action.switchTo(2), Action.flee(), Action.attack(),
                Action.attack(), Action.attack(), Action.attack(), Action.attack(), Action.attack());
        for (long seed : new long[] {0, 42, -7, 123456789L}) {
            Battle b1 = play(seed, w, a, c, script);
            Battle b2 = play(seed, w, a, c, script);
            assertEquals(b1.log(), b2.log());
            assertEquals(b1.view(), b2.view());
            assertEquals(b1.actions(), b2.actions());
            assertEquals(seed, b1.seed());
        }
        assertNotEquals(play(1, w, a, c, script).log(), play(2, w, a, c, script).log());
    }

    private static Battle play(long seed, Combatant w, Combatant a, Combatant c, List<Action> script) {
        Battle b = battle(seed, w, a, c);
        for (Action action : script) {
            if (b.isFinished()) {
                break;
            }
            try {
                b.apply(action);
            } catch (InvalidActionException e) {
                b.apply(Action.attack());
            }
        }
        return b;
    }

    @Test
    void turnLogAccumulates() {
        Battle b = battle(1, mon("W", "time", 300, 10, 100, 1), STRONG);
        b.apply(Action.attack());
        b.apply(Action.attack());
        assertEquals(2, b.log().size());
        assertEquals(1, b.log().get(0).turn());
        assertEquals(2, b.log().get(1).turn());
        assertEquals(b.log().get(1), b.view().lastRound());
        assertEquals(2, b.view().turn());
    }
}
