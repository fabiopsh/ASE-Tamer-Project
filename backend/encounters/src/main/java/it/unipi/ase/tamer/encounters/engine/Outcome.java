package it.unipi.ase.tamer.encounters.engine;

import java.util.Locale;

/** Outcome as decided by the engine. Abandon / expiry / interruption are decided by the service, not here. */
public enum Outcome {
    IN_PROGRESS, VICTORY, DEFEAT, FLED;

    /** Lowercase wire name: {@code in_progress}, {@code victory}, {@code defeat}, {@code fled}. */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
