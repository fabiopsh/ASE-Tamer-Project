package it.unipi.ase.tamers.encounters.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Element advantages: strong 1.5, weak 0.67, neutral 1. */
public final class ElementChart {
    public static final double STRONG = 1.5;
    public static final double WEAK = 0.67;

    /** Same order as elements.json "cycle". */
    public static final List<String> DEFAULT_CYCLE =
            List.of("silence", "echo", "memory", "time", "chaos", "gravity", "dream");

    public static final ElementChart DEFAULT = fromCycle(DEFAULT_CYCLE);

    private final Map<String, String> strongAgainst;
    private final Map<String, String> weakAgainst;

    public ElementChart(Map<String, String> strongAgainst, Map<String, String> weakAgainst) {
        this.strongAgainst = Map.copyOf(strongAgainst);
        this.weakAgainst = Map.copyOf(weakAgainst);
    }

    /** Each element is strong against the next one in the cycle and weak against the previous one. */
    public static ElementChart fromCycle(List<String> cycle) {
        Map<String, String> strong = new HashMap<>();
        Map<String, String> weak = new HashMap<>();
        int n = cycle.size();
        for (int i = 0; i < n; i++) {
            strong.put(cycle.get(i), cycle.get((i + 1) % n));
            weak.put(cycle.get(i), cycle.get((i + n - 1) % n));
        }
        return new ElementChart(strong, weak);
    }

    /** Raw multiplier of an attacker element against a defender element. */
    public double multiplier(String attacker, String defender) {
        if (attacker == null || defender == null) {
            return 1.0;
        }
        if (defender.equals(strongAgainst.get(attacker))) {
            return STRONG;
        }
        if (defender.equals(weakAgainst.get(attacker))) {
            return WEAK;
        }
        return 1.0;
    }

    public static String label(double multiplier) {
        if (multiplier > 1.0) {
            return "strong";
        }
        if (multiplier < 1.0) {
            return "weak";
        }
        return "neutral";
    }
}
