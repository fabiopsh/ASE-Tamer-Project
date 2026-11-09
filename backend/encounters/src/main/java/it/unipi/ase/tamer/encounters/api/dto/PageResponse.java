package it.unipi.ase.tamer.encounters.api.dto;

import java.util.List;

/** Common paginated envelope (docs/plan/01-conventions.md#pagination). */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        return new PageResponse<>(items, page, size, totalItems, (int) ((totalItems + size - 1) / size));
    }
}
