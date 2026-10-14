package it.unipi.ase.tamers.monsters.catalog;

import java.util.Locale;

/** The seven elements of the catalog; the JSON/DB representation is the lowercase name. */
public enum Element {
    DREAM, ECHO, TIME, GRAVITY, SILENCE, MEMORY, CHAOS;

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
