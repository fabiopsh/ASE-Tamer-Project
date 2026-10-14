package it.unipi.ase.tamers.encounters.engine;

/** Effective stats of a monster at a given level. */
public record Stats(int hp, int attack, int defense, int speed) {

    /** {@code round(base + growth * (level - 1))}, as defined by the monsters service. */
    public static int grow(int base, double growth, int level) {
        return (int) Math.round(base + growth * (level - 1));
    }
}
