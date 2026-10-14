package it.unipi.ase.tamers.history.record;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class HistoryDtos {
    private HistoryDtos() {}

    public record EnemySummary(int monsterId, String name, String element, String imageUrl, int level) {}

    public record TeamMemberSummary(int monsterId, String name, String imageUrl, int level) {}

    public record CapturedSummary(int monsterId, String name, String imageUrl) {}

    public record HistorySummary(
            UUID runId,
            UUID encounterId,
            long sequence,
            String kind,
            int stage,
            String outcome,
            int rounds,
            EnemySummary enemy,
            List<TeamMemberSummary> team,
            CapturedSummary captured,
            Instant occurredAt) {}

    /** Summary fields flattened next to result, hash and prevHash. */
    public record HistoryDetail(
            @JsonUnwrapped HistorySummary summary,
            JsonNode result,
            String hash,
            String prevHash) {}

    public record HistoryPage(List<HistorySummary> items, int page, int size, long total, int totalPages) {}

    public record VerifyResponse(boolean valid, int records, Long firstInvalidSequence) {}

    public record AppendResponse(UUID recordId, long sequence, String hash) {}

    public record StatsResponse(long encountersTotal, long encountersWon, long encountersLost) {}
}
