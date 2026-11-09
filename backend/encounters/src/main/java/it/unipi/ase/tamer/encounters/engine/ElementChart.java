package it.unipi.ase.tamer.encounters.engine;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Element advantages, in the shape of {@code elements.json}: {@code strong_against[a] = b} means {@code a} is
 * strong against {@code b}; {@code weak_against[a] = b} means {@code a} is weak against {@code b}.
 */
public final class ElementChart {

    /** Result of an element match-up, with its damage multiplier and wire name. */
    public enum Effectiveness {
        STRONG(1.5), NEUTRAL(1.0), WEAK(0.67);

        private final double multiplier;

        Effectiveness(double multiplier) {
            this.multiplier = multiplier;
        }

        public double multiplier() {
            return multiplier;
        }

        /** {@code strong}, {@code neutral}, {@code weak}. */
        public String wireName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final ElementChart DEFAULTS = new ElementChart(
            Map.of("silence", "echo", "echo", "memory", "memory", "time", "time", "chaos",
                    "chaos", "gravity", "gravity", "dream", "dream", "silence"),
            Map.of("silence", "dream", "echo", "silence", "memory", "echo", "time", "memory",
                    "chaos", "time", "gravity", "chaos", "dream", "gravity"));

    private final Map<String, String> strongAgainst;
    private final Map<String, String> weakAgainst;

    public ElementChart(Map<String, String> strongAgainst, Map<String, String> weakAgainst) {
        this.strongAgainst = Map.copyOf(Objects.requireNonNull(strongAgainst, "strongAgainst"));
        this.weakAgainst = Map.copyOf(Objects.requireNonNull(weakAgainst, "weakAgainst"));
    }

    /** The chart of {@code backend/monsters/seed/elements.json} (cycle silence → echo → memory → time → chaos → gravity → dream → silence). */
    public static ElementChart defaults() {
        return DEFAULTS;
    }

    public Effectiveness effectiveness(String attackerElement, String defenderElement) {
        if (defenderElement != null && defenderElement.equals(strongAgainst.get(attackerElement))) {
            return Effectiveness.STRONG;
        }
        if (defenderElement != null && defenderElement.equals(weakAgainst.get(attackerElement))) {
            return Effectiveness.WEAK;
        }
        return Effectiveness.NEUTRAL;
    }

    public Map<String, String> strongAgainst() {
        return strongAgainst;
    }

    public Map<String, String> weakAgainst() {
        return weakAgainst;
    }
}
