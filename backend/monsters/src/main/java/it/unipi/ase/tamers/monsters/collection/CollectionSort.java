package it.unipi.ase.tamers.monsters.collection;

import it.unipi.ase.tamers.monsters.catalog.Filters;
import java.time.Instant;
import java.util.Comparator;

/** Sort orders of {@code GET /collection}. */
public enum CollectionSort {
    /** Highest level first (default), then most XP. */
    LEVEL(Comparator.<OwnedMonster>comparingInt(o -> -o.level)
            .thenComparingInt(o -> -o.xp)
            .thenComparingInt(o -> o.monsterId)
            .thenComparing(o -> o.obtainedAt, Comparator.nullsLast(Comparator.<Instant>naturalOrder()))),
    /** Most recently obtained first. */
    RECENT(Comparator.<OwnedMonster, Instant>comparing(o -> o.obtainedAt,
                    Comparator.nullsLast(Comparator.<Instant>reverseOrder()))
            .thenComparingInt(o -> -o.level)),
    /** By species id, then highest level first. */
    SPECIES(Comparator.<OwnedMonster>comparingInt(o -> o.monsterId)
            .thenComparingInt(o -> -o.level)
            .thenComparingInt(o -> -o.xp));

    private final Comparator<OwnedMonster> comparator;

    CollectionSort(Comparator<OwnedMonster> comparator) {
        this.comparator = comparator;
    }

    public Comparator<OwnedMonster> comparator() {
        return comparator;
    }

    public static CollectionSort parse(String raw) {
        CollectionSort sort = Filters.parseEnum(CollectionSort.class, "sort", raw);
        return sort == null ? LEVEL : sort;
    }
}
