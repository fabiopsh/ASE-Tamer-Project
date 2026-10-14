package it.unipi.ase.tamers.monsters.catalog;

import it.unipi.ase.tamers.monsters.common.ApiException;
import java.util.List;

/** Pure paging math. A size above {@link #MAX_SIZE} is clamped; negative pages or sizes below 1 are rejected. */
public record Paging(int page, int size) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public record Page<T>(List<T> items, int page, int size, long total, int totalPages) {}

    public static Paging parse(String rawPage, String rawSize) {
        int page = parseInt("page", rawPage, 0);
        int size = parseInt("size", rawSize, DEFAULT_SIZE);
        if (page < 0) {
            throw ApiException.badRequest("page must be >= 0");
        }
        if (size < 1) {
            throw ApiException.badRequest("size must be >= 1");
        }
        return new Paging(page, Math.min(size, MAX_SIZE));
    }

    private static int parseInt(String name, String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(name + " must be an integer");
        }
    }

    public static int totalPages(long total, int size) {
        return (int) ((total + size - 1) / size);
    }

    public <T> Page<T> slice(List<T> all) {
        long offset = (long) page * size;
        List<T> items = offset >= all.size()
                ? List.of()
                : List.copyOf(all.subList((int) offset, (int) Math.min(all.size(), offset + size)));
        return new Page<>(items, page, size, all.size(), totalPages(all.size(), size));
    }
}
