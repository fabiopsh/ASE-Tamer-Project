package it.unipi.ase.tamers.monsters.catalog;

import java.util.Locale;

public enum Rarity {
    COMMON, RARE, LEGENDARY;

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
