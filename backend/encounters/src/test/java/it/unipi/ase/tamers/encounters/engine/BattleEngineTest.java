package it.unipi.ase.tamers.encounters.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BattleEngineTest {

    // ---------------------------------------------------------------- helpers

    private static Ability ab(String effect, int value, String trigger) {
        return new Ability(99, effect + "/" + trigger, "test", null, effect, value, trigger, "");
    }

    /** Combatant with raw stats (battle HP = 2 * hp + 10). */
    private static Combatant mon(String name, String element, int hp, int atk, int def, int spd, Ability ability) {
        return Combatant.of(name, element, 1, new Stats(hp, atk, def, spd), ability);
    }

    /** Neutral-element sparring partner / subject: balanced 100/100 stats. */
    private static Combatant solid(String name, int hp, int spd, Ability ability) {
        return mon(name, "silence", hp, 100, 100, spd, ability);
    }

    private static BattleResult fight(Combatant team, Combatant enemy, long seed) {
        return BattleEngine.simulate(List.of(team), enemy, seed);
    }

    private static List<BattleEvent> of(BattleResult r, String type) {
        return r.log().stream().filter(e -> e.type().equals(type)).toList();
    }

    private static List<BattleEvent> attacks(BattleResult r, String actor) {
        return r.log().stream().filter(e -> e.type().equals("attack") && e.actor().equals(actor)).toList();
    }

    private static List<Integer> plainDamages(BattleResult r, String actor) {
        return attacks(r, actor).stream().filter(e -> !e.critical()).map(BattleEvent::damage).toList();
    }

    private static void assertBetween(List<Integer> values, int lo, int hi) {
        assertFalse(values.isEmpty());
        for (int v : values) {
            assertTrue(v >= lo && v <= hi, "damage " + v + " not in [" + lo + ", " + hi + "]");
        }
    }

    /** Damages with atk=def=100 (base 50, variance 0.85..1.0 -> 42.5..50) scaled by a modifier. */
    private static int lo(double mod) { return (int) Math.floor(42.5 * mod); }
    private static int hi(double mod) { return (int) Math.ceil(50 * mod); }

    // ---------------------------------------------------------------- determinism and shape

    @Test
    void sameSeedSameLog() {
        Combatant a = mon("A", "echo", 40, 50, 40, 45, ab(Ability.DAMAGE_BOOST, 20, Ability.ALWAYS));
        Combatant b = mon("B", "memory", 45, 48, 42, 40, ab(Ability.DODGE, 10, Ability.ALWAYS));
        BattleResult r1 = fight(a, b, 12345L);
        BattleResult r2 = fight(a, b, 12345L);
        assertEquals(r1, r2);
        assertEquals(r1.log(), r2.log());
        boolean differs = false;
        for (long s = 1; s < 20 && !differs; s++) {
            differs = !fight(a, b, s).log().equals(r1.log());
        }
        assertTrue(differs, "different seeds should produce different battles");
    }

    @Test
    void inputsAreNotMutated() {
        Combatant a = solid("A", 50, 50, null);
        Combatant b = solid("B", 50, 40, null);
        fight(a, b, 1);
        assertEquals(a.maxHp(), a.hp());
        assertEquals(b.maxHp(), b.hp());
    }

    @Test
    void hpFormulaAndGuardianMultiplier() {
        assertEquals(90, Combatant.maxHpFor(40, 1.0));
        assertEquals(225, Combatant.maxHpFor(40, 2.5));
        Combatant guardian = Combatant.of("G", "echo", 5, new Stats(40, 50, 50, 50), null, 2.5);
        assertEquals(225, guardian.maxHp());
        BattleResult r = fight(solid("A", 40, 50, null), guardian, 1);
        BattleEvent enter = of(r, "enter").stream().filter(e -> e.actor().equals("wild")).findFirst().orElseThrow();
        assertEquals(225, enter.actorHp());
    }

    @Test
    void enterEventsAndFinalEvent() {
        BattleResult r = fight(solid("A", 100, 60, null), solid("B", 20, 50, null), 7);
        assertEquals("enter", r.log().get(0).type());
        assertEquals("team:0", r.log().get(0).actor());
        assertEquals("enter", r.log().get(1).type());
        assertEquals("wild", r.log().get(1).actor());
        assertEquals(Outcome.VICTORY, r.outcome());
        assertEquals("victory", r.log().get(r.log().size() - 1).type());
        assertTrue(r.log().stream().anyMatch(e -> e.type().equals("faint") && e.actor().equals("wild")));
    }

    @Test
    void eventJsonShape() throws Exception {
        ObjectMapper mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        BattleResult r = fight(solid("A", 100, 60, null), solid("B", 20, 50, null), 7);
        BattleEvent attack = attacks(r, "team:0").get(0);
        JsonNode n = mapper.readTree(mapper.writeValueAsString(attack));
        for (String f : List.of("round", "type", "actor", "target", "damage", "critical", "effectiveness",
                "actor_hp", "target_hp", "text")) {
            assertTrue(n.has(f), "missing " + f);
        }
        assertFalse(n.has("ability"), "null fields must be omitted");
        JsonNode enter = mapper.readTree(mapper.writeValueAsString(r.log().get(0)));
        assertFalse(enter.has("damage"));
        assertFalse(enter.has("critical"));
        assertEquals("enter", enter.get("type").asText());
    }

    // ---------------------------------------------------------------- order, damage, elements

    @Test
    void fasterActsFirstAndTieGoesToPlayer() {
        BattleResult slowPlayer = fight(solid("A", 100, 40, null), solid("B", 100, 50, null), 3);
        assertEquals("wild", firstAction(slowPlayer).actor());
        BattleResult fastPlayer = fight(solid("A", 100, 60, null), solid("B", 100, 50, null), 3);
        assertEquals("team:0", firstAction(fastPlayer).actor());
        BattleResult tie = fight(solid("A", 100, 50, null), solid("B", 100, 50, null), 3);
        assertEquals("team:0", firstAction(tie).actor());
    }

    private static BattleEvent firstAction(BattleResult r) {
        return r.log().stream().filter(e -> !e.type().equals("enter")).findFirst().orElseThrow();
    }

    @Test
    void damageFormulaAndVariance() {
        // atk 100 vs def 100 -> 50 * variance(0.85..1.0)
        BattleResult r = fight(solid("A", 300, 60, null), solid("B", 300, 50, null), 11);
        assertBetween(plainDamages(r, "team:0"), lo(1), hi(1));
        assertBetween(plainDamages(r, "wild"), lo(1), hi(1));
        // atk 60 vs def 40 -> 60*60/100 = 36
        BattleResult r2 = fight(mon("A", "silence", 300, 60, 40, 60, null), mon("B", "silence", 300, 60, 40, 50, null), 5);
        assertBetween(plainDamages(r2, "team:0"), (int) Math.floor(36 * 0.85), 36);
    }

    @Test
    void damageIsAtLeastOne() {
        BattleResult r = fight(mon("A", "silence", 100, 1, 100, 60, null), mon("B", "silence", 100, 1, 1000, 50, null), 1);
        assertTrue(attacks(r, "team:0").stream().allMatch(e -> e.damage() >= 1));
    }

    @Test
    void criticalHitsAroundOneInSixteen() {
        // many fights, count critical share among attack events
        int crits = 0, total = 0;
        double ratioSum = 0;
        int ratioN = 0;
        for (long s = 0; s < 200; s++) {
            BattleResult r = fight(solid("A", 400, 60, null), solid("B", 400, 50, null), s);
            for (BattleEvent e : attacks(r, "team:0")) {
                total++;
                if (e.critical()) {
                    crits++;
                    assertTrue(e.damage() >= (int) Math.floor(42.5 * 1.5) && e.damage() <= 75, "crit " + e.damage());
                }
            }
        }
        double rate = crits / (double) total;
        assertTrue(rate > 0.04 && rate < 0.09, "crit rate " + rate);
    }

    @Test
    void elementMultipliers() {
        // silence is strong against echo and weak against dream (elements.json)
        assertEquals(1.5, ElementChart.DEFAULT.multiplier("silence", "echo"));
        assertEquals(0.67, ElementChart.DEFAULT.multiplier("silence", "dream"));
        assertEquals(1.0, ElementChart.DEFAULT.multiplier("silence", "silence"));
        assertEquals(1.0, ElementChart.DEFAULT.multiplier("silence", "time"));
        assertEquals(1.5, ElementChart.DEFAULT.multiplier("dream", "silence"));
        assertEquals(0.67, ElementChart.DEFAULT.multiplier("echo", "silence"));

        Combatant silence = mon("A", "silence", 300, 100, 100, 60, null);
        BattleResult strong = fight(silence, mon("B", "echo", 300, 100, 100, 50, null), 2);
        assertBetween(plainDamages(strong, "team:0"), lo(1.5), hi(1.5));
        assertTrue(attacks(strong, "team:0").stream().allMatch(e -> e.effectiveness().equals("strong")));
        assertBetween(plainDamages(strong, "wild"), lo(0.67), hi(0.67));
        assertTrue(attacks(strong, "wild").stream().allMatch(e -> e.effectiveness().equals("weak")));

        BattleResult neutral = fight(silence, mon("B", "time", 300, 100, 100, 50, null), 2);
        assertBetween(plainDamages(neutral, "team:0"), lo(1), hi(1));
        assertTrue(attacks(neutral, "team:0").stream().allMatch(e -> e.effectiveness().equals("neutral")));
    }

    // ---------------------------------------------------------------- abilities (one test per effect)

    @Test
    void damageBoost() {
        BattleResult r = fight(solid("A", 300, 60, ab(Ability.DAMAGE_BOOST, 50, Ability.ALWAYS)), solid("B", 300, 50, null), 4);
        assertBetween(plainDamages(r, "team:0"), lo(1.5), hi(1.5));
        assertBetween(plainDamages(r, "wild"), lo(1), hi(1));
    }

    @Test
    void damageReduction() {
        BattleResult r = fight(solid("A", 300, 60, ab(Ability.DAMAGE_REDUCTION, 40, Ability.ALWAYS)), solid("B", 300, 50, null), 4);
        assertBetween(plainDamages(r, "wild"), lo(0.6), hi(0.6));
        assertBetween(plainDamages(r, "team:0"), lo(1), hi(1));
    }

    @Test
    void dodgeAlways() {
        // the enemy dodges every attack: no hit of the team ever lands
        BattleResult r = fight(solid("A", 100, 60, null), solid("B", 100, 50, ab(Ability.DODGE, 100, Ability.ALWAYS)), 9);
        assertTrue(attacks(r, "team:0").isEmpty());
        assertFalse(of(r, "dodge").isEmpty());
        BattleEvent dodge = of(r, "dodge").get(0);
        assertEquals("team:0", dodge.actor());
        assertEquals("wild", dodge.target());
    }

    @Test
    void dodgeHpAbove50AppliesOnlyWhileHealthy() {
        // 100% dodge only above half HP: enemy hits land only once the dodger is already below 50%,
        // but it can never get there by itself -> the enemy never lands a hit
        BattleResult r = fight(solid("A", 100, 60, ab(Ability.DODGE, 100, Ability.HP_ABOVE_50)), solid("B", 100, 50, null), 9);
        assertTrue(attacks(r, "wild").isEmpty());
        assertFalse(of(r, "dodge").isEmpty());
    }

    @Test
    void heal() {
        // hp_below_50: exactly one heal, restoring 25% of max HP
        Combatant healer = solid("A", 100, 60, ab(Ability.HEAL, 25, Ability.HP_BELOW_50)); // maxHp 210
        Combatant punchingBag = mon("B", "silence", 3000, 60, 100, 50, null);
        BattleResult r = fight(healer, punchingBag, 6);
        List<BattleEvent> heals = of(r, "heal");
        assertEquals(1, heals.size());
        BattleEvent before = r.log().get(r.log().indexOf(heals.get(0)) - 1);
        assertEquals("wild", before.actor());
        assertTrue(before.targetHp() * 2 < 210);
        int healed = heals.get(0).actorHp();
        assertTrue(healed > 0 && healed < 210);

        // always: heals at the end of each own action
        Combatant regen = solid("A", 100, 60, ab(Ability.HEAL, 10, Ability.ALWAYS));
        BattleResult r2 = fight(regen, mon("B", "silence", 3000, 60, 100, 50, null), 6);
        assertTrue(of(r2, "heal").size() >= 3);
        assertTrue(of(r2, "heal").stream().allMatch(e -> e.actor().equals("team:0")));
    }

    @Test
    void speedBoost() {
        // 40 speed + 50% = 60 > 50: acts first; without the boost the enemy goes first
        BattleResult boosted = fight(solid("A", 100, 40, ab(Ability.SPEED_BOOST, 50, Ability.ALWAYS)), solid("B", 100, 50, null), 1);
        assertEquals("team:0", firstAction(boosted).actor());
        BattleResult plain = fight(solid("A", 100, 40, null), solid("B", 100, 50, null), 1);
        assertEquals("wild", firstAction(plain).actor());
        // first_turn boost: only the first round is affected
        BattleResult first = fight(solid("A", 300, 40, ab(Ability.SPEED_BOOST, 50, Ability.FIRST_TURN)), solid("B", 300, 50, null), 1);
        List<BattleEvent> acts = first.log().stream().filter(e -> e.type().equals("attack")).toList();
        assertEquals("team:0", acts.get(0).actor());
        assertEquals("wild", acts.get(1).actor());
        assertEquals("wild", acts.get(2).actor()); // round 2: enemy first again
        assertEquals("team:0", acts.get(3).actor());
    }

    @Test
    void reflect() {
        BattleResult r = fight(solid("A", 300, 60, null), solid("B", 300, 50, ab(Ability.REFLECT, 50, Ability.ON_HIT)), 8);
        List<BattleEvent> reflects = of(r, "ability");
        assertFalse(reflects.isEmpty());
        for (BattleEvent e : reflects) {
            assertEquals("wild", e.actor());
            assertEquals("team:0", e.target());
            int idx = r.log().indexOf(e);
            BattleEvent hit = r.log().get(idx - 1);
            assertEquals("attack", hit.type());
            assertEquals((int) Math.max(1, Math.round(hit.damage() * 0.5)), e.damage().intValue());
        }
    }

    @Test
    void stunMakesTheOpponentSkip() {
        BattleResult r = fight(solid("A", 300, 60, ab(Ability.STUN, 100, Ability.ON_ATTACK)), solid("B", 300, 50, null), 8);
        assertFalse(of(r, "stun").isEmpty());
        assertFalse(of(r, "skip").isEmpty());
        assertTrue(attacks(r, "wild").isEmpty(), "a permanently stunned enemy never attacks");
        assertTrue(of(r, "skip").stream().allMatch(e -> e.actor().equals("wild")));
        assertEquals(Outcome.VICTORY, r.outcome());
    }

    @Test
    void weakenAtEntry() {
        BattleResult r = fight(solid("A", 300, 60, ab(Ability.WEAKEN, 50, Ability.FIRST_TURN)), solid("B", 300, 50, null), 3);
        assertEquals("ability", r.log().get(2).type());
        // enemy attack 100 -> 50: 50*50/150 = 16.67 * 0.85..1
        assertBetween(plainDamages(r, "wild"), (int) Math.floor(16.67 * 0.85), (int) Math.ceil(16.67));
        assertBetween(plainDamages(r, "team:0"), lo(1), hi(1));
    }

    @Test
    void ignoreDisadvantage() {
        Combatant carrier = mon("A", "silence", 300, 100, 100, 60, ab(Ability.IGNORE_DISADVANTAGE, 100, Ability.ALWAYS));
        BattleResult r = fight(carrier, mon("B", "dream", 300, 100, 100, 50, null), 2); // silence is weak vs dream
        assertBetween(plainDamages(r, "team:0"), lo(1), hi(1));
        assertTrue(attacks(r, "team:0").stream().allMatch(e -> e.effectiveness().equals("neutral")));
        // the opponent still has its advantage
        assertBetween(plainDamages(r, "wild"), lo(1.5), hi(1.5));
    }

    @Test
    void revive() {
        Combatant phoenix = mon("A", "silence", 20, 100, 100, 60, ab(Ability.REVIVE, 50, Ability.ONCE)); // maxHp 50
        Combatant bruiser = mon("B", "silence", 500, 200, 100, 50, null);
        BattleResult r = fight(phoenix, bruiser, 2);
        List<BattleEvent> revives = of(r, "revive");
        assertEquals(1, revives.size());
        assertEquals(25, revives.get(0).actorHp());
        assertEquals(1, of(r, "faint").stream().filter(e -> e.actor().equals("team:0")).count());
        int reviveIdx = r.log().indexOf(revives.get(0));
        assertTrue(r.log().subList(reviveIdx, r.log().size()).stream().anyMatch(e -> e.type().equals("faint")));
        assertEquals(Outcome.DEFEAT, r.outcome());
    }

    @Test
    void randomBoost() {
        Combatant chaotic = solid("A", 5000, 60, ab(Ability.RANDOM_BOOST, 30, Ability.ALWAYS));
        double min = 1e9, max = 0;
        for (long s = 0; s < 20; s++) {
            for (int d : plainDamages(fight(chaotic, solid("B", 5000, 50, null), s), "team:0")) {
                min = Math.min(min, d);
                max = Math.max(max, d);
            }
        }
        // factor in [0.7, 1.6] on 42.5..50
        assertTrue(min >= Math.floor(42.5 * 0.7) && max <= Math.ceil(50 * 1.6), min + ".." + max);
        assertTrue(min < 42 && max > 50, "random boost should spread damage: " + min + ".." + max);
    }

    @Test
    void invertAdvantage() {
        Combatant rebel = mon("A", "silence", 300, 100, 100, 60, ab(Ability.INVERT_ADVANTAGE, 100, Ability.ALWAYS));
        BattleResult r = fight(rebel, mon("B", "echo", 300, 100, 100, 50, null), 2); // normally strong
        assertTrue(attacks(r, "team:0").stream().allMatch(e -> e.effectiveness().equals("weak")));
        assertBetween(plainDamages(r, "team:0"), lo(0.67), hi(0.67));
        // inverted for the carrier when defending too: echo attacking silence is normally weak -> now strong
        assertTrue(attacks(r, "wild").stream().allMatch(e -> e.effectiveness().equals("strong")));
    }

    @Test
    void copyAbility() {
        Combatant copier = solid("A", 100, 60, ab(Ability.COPY_ABILITY, 100, Ability.FIRST_TURN));
        Combatant enemy = solid("B", 100, 50, ab(Ability.DODGE, 100, Ability.ALWAYS));
        BattleResult r = fight(copier, enemy, 2);
        BattleEvent copy = r.log().stream().filter(e -> e.type().equals("ability")).findFirst().orElseThrow();
        assertEquals("team:0", copy.actor());
        assertTrue(copy.text().contains("copies"));
        // after the copy the team dodges everything too: nobody ever gets hit
        assertTrue(of(r, "attack").isEmpty());
        assertEquals(Outcome.DEFEAT, r.outcome());
        assertEquals(BattleEngine.MAX_ROUNDS, r.rounds());
        assertTrue(of(r, "dodge").stream().anyMatch(e -> e.target().equals("team:0")));
    }

    // ---------------------------------------------------------------- triggers

    private static BattleResult firstTurnDamage(Ability ability, long[] usedSeed) {
        for (long s = 0; s < 100; s++) {
            BattleResult r = fight(solid("A", 500, 60, ability), solid("B", 500, 50, null), s);
            if (!attacks(r, "team:0").get(0).critical()) {
                usedSeed[0] = s;
                return r;
            }
        }
        throw new AssertionError("no seed without a first-attack crit");
    }

    @Test
    void triggerFirstTurn() {
        BattleResult r = firstTurnDamage(ab(Ability.DAMAGE_BOOST, 100, Ability.FIRST_TURN), new long[1]);
        List<BattleEvent> mine = attacks(r, "team:0");
        assertTrue(mine.get(0).damage() >= lo(2) && mine.get(0).damage() <= hi(2));
        assertBetween(mine.stream().skip(1).filter(e -> !e.critical()).map(BattleEvent::damage).toList(), lo(1), hi(1));
    }

    @Test
    void triggerHpBelow50() {
        Combatant subject = solid("A", 100, 60, ab(Ability.DAMAGE_BOOST, 100, Ability.HP_BELOW_50)); // maxHp 210
        Combatant enemy = mon("B", "silence", 3000, 40, 100, 50, null);
        BattleResult r = fight(subject, enemy, 3);
        int healthy = 0, hurt = 0;
        for (BattleEvent e : attacks(r, "team:0")) {
            if (e.critical()) continue;
            if (e.actorHp() * 2 < 210) {
                hurt++;
                assertTrue(e.damage() >= lo(2) && e.damage() <= hi(2), "hurt damage " + e.damage());
            } else {
                healthy++;
                assertTrue(e.damage() >= lo(1) && e.damage() <= hi(1), "healthy damage " + e.damage());
            }
        }
        assertTrue(healthy > 0 && hurt > 0, healthy + "/" + hurt);
    }

    @Test
    void triggerVsFaster() {
        Ability crushing = ab(Ability.DAMAGE_BOOST, 100, Ability.VS_FASTER);
        // enemy faster than the carrier: boosted
        BattleResult slow = fight(solid("A", 300, 40, crushing), solid("B", 300, 50, null), 5);
        assertBetween(plainDamages(slow, "team:0"), lo(2), hi(2));
        // enemy slower: no boost
        BattleResult fast = fight(solid("A", 300, 60, crushing), solid("B", 300, 50, null), 5);
        assertBetween(plainDamages(fast, "team:0"), lo(1), hi(1));
    }

    @Test
    void triggerFirstTurnDodgeCoversOnlyTheFirstAttackReceived() {
        BattleResult r = fight(solid("A", 300, 60, ab(Ability.DODGE, 100, Ability.FIRST_TURN)), solid("B", 300, 50, null), 5);
        List<BattleEvent> dodges = of(r, "dodge");
        assertEquals(1, dodges.size());
        assertEquals("wild", dodges.get(0).actor());
        assertEquals("team:0", dodges.get(0).target());
        assertFalse(attacks(r, "wild").isEmpty());
    }

    @Test
    void triggerOnceRevivesOnlyOnce() {
        Combatant phoenix = mon("A", "silence", 20, 100, 100, 60, ab(Ability.REVIVE, 100, Ability.ONCE));
        BattleResult r = fight(phoenix, mon("B", "silence", 5000, 200, 100, 50, null), 1);
        assertEquals(1, of(r, "revive").size());
        assertEquals(Outcome.DEFEAT, r.outcome());
    }

    // ---------------------------------------------------------------- flow

    @Test
    void stalemateEndsInDefeatAfter100Rounds() {
        Combatant a = solid("A", 100, 60, ab(Ability.DODGE, 100, Ability.ALWAYS));
        Combatant b = solid("B", 100, 50, ab(Ability.DODGE, 100, Ability.ALWAYS));
        BattleResult r = fight(a, b, 1);
        assertEquals(Outcome.DEFEAT, r.outcome());
        assertEquals(100, r.rounds());
        assertEquals("defeat", r.log().get(r.log().size() - 1).type());
        assertEquals(100, r.log().get(r.log().size() - 1).round());
    }

    @Test
    void nextMemberEntersWhenOneIsKnockedOut() {
        Combatant weak = mon("Weak", "silence", 5, 30, 30, 40, null);
        Combatant strong = mon("Strong", "silence", 200, 200, 100, 40, null);
        Combatant enemy = mon("Enemy", "silence", 100, 100, 50, 50, null);
        BattleResult r = BattleEngine.simulate(List.of(weak, strong), enemy, 4);
        List<BattleEvent> enters = of(r, "enter");
        assertEquals(List.of("team:0", "wild", "team:1"), enters.stream().map(BattleEvent::actor).toList());
        assertEquals("team:0", of(r, "faint").get(0).actor());
        assertEquals(Outcome.VICTORY, r.outcome());
        // the replacement acts only after the faint
        int faintIdx = r.log().indexOf(of(r, "faint").get(0));
        assertTrue(r.log().subList(0, faintIdx).stream().noneMatch(e -> "team:1".equals(e.actor())));
    }

    @Test
    void wholeTeamKnockedOutIsDefeat() {
        Combatant a = mon("A", "silence", 5, 30, 30, 40, null);
        Combatant b = mon("B", "silence", 5, 30, 30, 40, null);
        Combatant enemy = mon("Enemy", "silence", 500, 200, 50, 50, null);
        BattleResult r = BattleEngine.simulate(List.of(a, b), enemy, 4);
        assertEquals(Outcome.DEFEAT, r.outcome());
        assertEquals(2, of(r, "faint").size());
        assertEquals("defeat", r.log().get(r.log().size() - 1).type());
    }

    @Test
    void roundsMatchLastEventRound() {
        BattleResult r = fight(solid("A", 100, 60, null), solid("B", 20, 50, null), 7);
        assertEquals(r.log().get(r.log().size() - 1).round(), r.rounds());
        assertTrue(r.rounds() >= 1 && r.rounds() <= 100);
        List<String> names = new ArrayList<>(r.log().stream().map(BattleEvent::type).collect(Collectors.toSet()));
        assertTrue(names.contains("attack"));
    }
}
