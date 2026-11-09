package it.unipi.ase.tamer.history.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

/** How an encounter ended. The wire and database form is the lower-case name. */
public enum Outcome {
    VICTORY("victory"),
    DEFEAT("defeat"),
    FLED("fled"),
    EXPIRED("expired"),
    INTERRUPTED("interrupted"),
    ABANDONED("abandoned");

    private final String wire;

    Outcome(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static Outcome fromWire(String value) {
        return parse(value).orElseThrow(() -> new IllegalArgumentException("Unknown outcome: " + value));
    }

    public static Optional<Outcome> parse(String value) {
        return Arrays.stream(values()).filter(o -> o.wire.equals(value)).findFirst();
    }
}
