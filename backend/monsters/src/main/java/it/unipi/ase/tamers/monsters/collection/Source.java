package it.unipi.ase.tamers.monsters.collection;

import java.util.Locale;

/** Where an owned monster comes from. */
public enum Source {
    STARTER, ENCOUNTER;

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
