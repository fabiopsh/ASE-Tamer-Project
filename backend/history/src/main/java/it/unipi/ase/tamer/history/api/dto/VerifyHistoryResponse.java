package it.unipi.ase.tamer.history.api.dto;

import java.util.UUID;

/** Result of {@code GET /api/history/verify} (US-25). */
public record VerifyHistoryResponse(
        boolean valid,
        int records,
        String headHash,
        UUID firstInvalid,
        Long firstBrokenSeq) {
}
