package it.unipi.ase.tamers.monsters.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure growth rules: effective stats, XP thresholds and level-ups. */
public final class Growth {
    public static final int MAX_LEVEL = 50;
    public static final int XP_PER_LEVEL = 25;

    private Growth() {}

    /** {@code round(base + growth * (level - 1))}, rounding half up; computed in decimal to avoid float noise. */
    public static int effectiveStat(int base, double growth, int level) {
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1");
        }
        BigDecimal value = BigDecimal.valueOf(growth)
                .multiply(BigDecimal.valueOf(level - 1L))
                .add(BigDecimal.valueOf(base));
        return value.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    /** XP needed to go from {@code level} to {@code level + 1}. */
    public static int xpToNext(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1");
        }
        return XP_PER_LEVEL * level;
    }

    /** XP still missing for the next level as shown to clients: 0 at {@link #MAX_LEVEL}. */
    public static int xpToNextForDisplay(int level) {
        return level >= MAX_LEVEL ? 0 : xpToNext(level);
    }

    public record Progress(int level, int xp) {}

    /**
     * Adds {@code gained} XP: while the threshold is reached {@code xp -= threshold} and {@code level += 1}
     * (several level-ups at once). At {@link #MAX_LEVEL} the XP is dropped and stays 0.
     */
    public static Progress applyXp(int level, int xp, int gained) {
        if (level < 1 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("level must be between 1 and " + MAX_LEVEL);
        }
        if (xp < 0 || gained < 0) {
            throw new IllegalArgumentException("xp must not be negative");
        }
        long total = (long) xp + gained;
        int current = level;
        while (current < MAX_LEVEL && total >= xpToNext(current)) {
            total -= xpToNext(current);
            current++;
        }
        if (current >= MAX_LEVEL) {
            return new Progress(MAX_LEVEL, 0);
        }
        return new Progress(current, (int) total);
    }
}
