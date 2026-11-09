package it.unipi.ase.tamer.monsters.service;

/** Pure level/XP formula (maximum level 50). */
public final class Leveling {

    public static final int MAX_LEVEL = 50;

    private Leveling() {
    }

    public record State(int level, int xp) {
    }

    public static int xpToNextLevel(int level) {
        if (level < 1 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("Level out of range.");
        }
        return level == MAX_LEVEL ? 0 : 15 * level;
    }

    public static State grant(int level, int currentXp, int earnedXp) {
        if (level < 1 || level > MAX_LEVEL || currentXp < 0 || earnedXp < 0) {
            throw new IllegalArgumentException("Invalid level or XP.");
        }
        if (level == MAX_LEVEL) {
            return new State(MAX_LEVEL, 0);
        }
        long xp = (long) currentXp + earnedXp;
        while (level < MAX_LEVEL && xp >= xpToNextLevel(level)) {
            xp -= xpToNextLevel(level);
            level++;
        }
        return new State(level, level == MAX_LEVEL ? 0 : (int) xp);
    }
}
