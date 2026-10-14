package it.unipi.ase.tamers.encounters.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Outcome {
    VICTORY("victory"),
    DEFEAT("defeat");

    private final String value;

    Outcome(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static Outcome fromValue(String value) {
        for (Outcome o : values()) {
            if (o.value.equals(value)) {
                return o;
            }
        }
        throw new IllegalArgumentException("unknown outcome " + value);
    }
}
