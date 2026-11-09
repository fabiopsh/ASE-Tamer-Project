package it.unipi.ase.tamer.encounters.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unipi.ase.tamer.encounters.engine.ElementChart.Effectiveness;
import org.junit.jupiter.api.Test;

class CalculatorsTest {

    private final ElementChart chart = ElementChart.defaults();

    @Test
    void chartMatchesElementsJson() {
        assertEquals(Effectiveness.STRONG, chart.effectiveness("silence", "echo"));
        assertEquals(Effectiveness.STRONG, chart.effectiveness("dream", "silence"));
        assertEquals(Effectiveness.WEAK, chart.effectiveness("silence", "dream"));
        assertEquals(Effectiveness.WEAK, chart.effectiveness("echo", "silence"));
        assertEquals(Effectiveness.NEUTRAL, chart.effectiveness("silence", "time"));
        assertEquals(Effectiveness.NEUTRAL, chart.effectiveness("chaos", "chaos"));
        assertEquals(7, chart.strongAgainst().size());
        assertEquals(7, chart.weakAgainst().size());
    }

    @Test
    void multipliers() {
        assertEquals(1.5, Effectiveness.STRONG.multiplier());
        assertEquals(0.67, Effectiveness.WEAK.multiplier());
        assertEquals(1.0, Effectiveness.NEUTRAL.multiplier());
        // 100² / 200 × 0.6 = 30
        assertEquals(30, DamageCalculator.damage(100, 100, 1.0, 1.0, 1.0, 1.0));
        assertEquals(45, DamageCalculator.damage(100, 100, 1.5, 1.0, 1.0, 1.0));
        assertEquals(20, DamageCalculator.damage(100, 100, 0.67, 1.0, 1.0, 1.0));
        assertEquals(33, DamageCalculator.damage(100, 100, 1.0, 1.1, 1.0, 1.0));
        assertEquals(36, DamageCalculator.damage(100, 100, 1.0, 1.0, 1.2, 1.0));
        assertEquals(24, DamageCalculator.damage(100, 100, 1.0, 1.0, 1.0, 0.8));
        assertEquals(1, DamageCalculator.damage(1, 500, 0.67, 0.9, 1.0, 0.6));
    }

    @Test
    void ignoreDisadvantageOnlyTurnsWeakIntoNeutral() {
        assertEquals(Effectiveness.NEUTRAL, DamageCalculator.effectiveness(chart, "echo", "silence", true));
        assertEquals(Effectiveness.WEAK, DamageCalculator.effectiveness(chart, "echo", "silence", false));
        assertEquals(Effectiveness.STRONG, DamageCalculator.effectiveness(chart, "echo", "memory", true));
    }

    @Test
    void varianceInRange() {
        BattleRandom rng = new BattleRandom(1);
        for (int i = 0; i < 10_000; i++) {
            double v = DamageCalculator.rollVariance(rng);
            assertTrue(v >= 0.9 && v < 1.1);
        }
    }

    @Test
    void hpFormulas() {
        assertEquals(30, HpCalculator.teamMaxHp(10));
        assertEquals(45, HpCalculator.wildMaxHp(10, false));
        assertEquals((int) Math.round(30 * HpCalculator.BOSS_HP_MULTIPLIER), HpCalculator.wildMaxHp(10, true));
    }

    @Test
    void fleeProbability() {
        assertEquals(0.75, FleeCalculator.probability(50, 50), 1e-9);
        assertEquals(0.95, FleeCalculator.probability(100, 1), 1e-9);
        assertEquals(0.25 + 10.0 / 110, FleeCalculator.probability(10, 100), 1e-9);
    }

    @Test
    void xp() {
        assertEquals(20, XpCalculator.victoryXp(2, "common"));
        assertEquals(75, XpCalculator.victoryXp(5, "rare"));
        assertEquals(520, XpCalculator.victoryXp(26, "legendary"));
    }

    @Test
    void turnOrderTieUsesRng() {
        assertTrue(TurnOrder.teamFirst(11, 10, new BattleRandom(0)));
        assertFalse(TurnOrder.teamFirst(10, 11, new BattleRandom(0)));
        boolean sawTeam = false;
        boolean sawWild = false;
        for (long seed = 0; seed < 50; seed++) {
            boolean first = TurnOrder.teamFirst(10, 10, new BattleRandom(seed));
            assertEquals(first, new BattleRandom(seed).nextBoolean());
            sawTeam |= first;
            sawWild |= !first;
        }
        assertTrue(sawTeam && sawWild);
    }
}
