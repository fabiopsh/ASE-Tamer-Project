package it.unipi.ase.tamer.encounters.engine;

import java.util.Locale;
import java.util.Objects;

/**
 * A player action. {@code slot} is the team position (1-based) and is set only for {@link Type#SWITCH}.
 */
public record Action(Type type, Integer slot) {

    public enum Type {
        ATTACK, SWITCH, FLEE;

        /** Lowercase name used on the wire ({@code attack}, {@code switch}, {@code flee}). */
        public String wireName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Parses a wire name; unknown → {@link InvalidActionException}. */
        public static Type fromWire(String value) {
            if (value != null) {
                for (Type t : values()) {
                    if (t.wireName().equals(value)) {
                        return t;
                    }
                }
            }
            throw new InvalidActionException("Unknown action type: " + value + ".");
        }
    }

    public Action {
        Objects.requireNonNull(type, "type");
        if (type == Type.SWITCH && slot == null) {
            throw new InvalidActionException("A switch needs a slot.");
        }
        if (type != Type.SWITCH && slot != null) {
            throw new InvalidActionException("Only a switch takes a slot.");
        }
    }

    public static Action attack() {
        return new Action(Type.ATTACK, null);
    }

    public static Action switchTo(int slot) {
        return new Action(Type.SWITCH, slot);
    }

    public static Action flee() {
        return new Action(Type.FLEE, null);
    }
}
