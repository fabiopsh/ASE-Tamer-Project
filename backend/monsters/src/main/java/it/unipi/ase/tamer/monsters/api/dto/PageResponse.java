package it.unipi.ase.tamer.monsters.api.dto;

import java.util.List;

/** Shared zero-based page convention; capped at 50 items. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }
}
