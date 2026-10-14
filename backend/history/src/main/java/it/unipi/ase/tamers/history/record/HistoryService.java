package it.unipi.ase.tamers.history.record;

import com.fasterxml.jackson.databind.JsonNode;
import it.unipi.ase.tamers.history.common.ApiException;
import it.unipi.ase.tamers.history.record.EncounterPayload.Header;
import it.unipi.ase.tamers.history.record.HashChain.Link;
import it.unipi.ase.tamers.history.record.HashChain.VerifyResult;
import it.unipi.ase.tamers.history.record.HistoryDtos.AppendResponse;
import it.unipi.ase.tamers.history.record.HistoryDtos.HistoryDetail;
import it.unipi.ase.tamers.history.record.HistoryDtos.HistoryPage;
import it.unipi.ase.tamers.history.record.HistoryDtos.HistorySummary;
import it.unipi.ase.tamers.history.record.HistoryDtos.StatsResponse;
import it.unipi.ase.tamers.history.record.HistoryDtos.VerifyResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class HistoryService {
    @ConfigProperty(name = "history.secret")
    String secret;

    @Inject
    EntityManager em;

    /**
     * Appends one encounter to the player's chain. The per-player transaction lock serializes
     * concurrent appends, so sequences are never duplicated; the unique constraint on
     * (player_id, sequence) is the last line of defence.
     */
    @Transactional
    public AppendResponse append(JsonNode body) {
        Header header = EncounterPayload.header(body);
        String payload = EncounterPayload.serialize(body);

        lockPlayerChain(header.playerId());
        if (HistoryRecord.count("runId", header.runId()) > 0) {
            throw ApiException.conflict("run already recorded");
        }
        Optional<HistoryRecord> last = HistoryRecord
                .<HistoryRecord>find("playerId = ?1 order by sequence desc", header.playerId())
                .firstResultOptional();
        long sequence = last.map(r -> r.sequence + 1).orElse(1L);
        String prevHash = last.map(r -> r.hash).orElse(HashChain.GENESIS);

        HistoryRecord record = new HistoryRecord();
        record.recordId = UUID.randomUUID();
        record.playerId = header.playerId();
        record.runId = header.runId();
        record.sequence = sequence;
        record.occurredAt = header.occurredAt();
        record.outcome = header.outcome();
        record.kind = header.kind();
        record.stage = header.stage();
        record.payload = payload;
        record.prevHash = prevHash;
        record.hash = HashChain.hash(secret, prevHash, sequence, payload);
        try {
            record.persistAndFlush();
        } catch (PersistenceException e) {
            throw ApiException.conflict("run already recorded or concurrent write, retry");
        }
        return new AppendResponse(record.recordId, record.sequence, record.hash);
    }

    public HistoryPage list(UUID playerId, String outcome, int page, int size) {
        String where = outcome == null ? "playerId = ?1" : "playerId = ?1 and outcome = ?2";
        Object[] params = outcome == null ? new Object[] {playerId} : new Object[] {playerId, outcome};
        long total = HistoryRecord.count(where, params);
        List<HistorySummary> items = HistoryRecord
                .<HistoryRecord>find(where + " order by sequence desc", params)
                .page(page, size)
                .list()
                .stream()
                .map(this::summaryOf)
                .toList();
        int totalPages = (int) ((total + size - 1) / size);
        return new HistoryPage(items, page, size, total, totalPages);
    }

    public HistoryDetail detail(UUID runId, UUID playerId) {
        HistoryRecord record = HistoryRecord.<HistoryRecord>find("runId", runId)
                .firstResultOptional()
                .orElseThrow(() -> ApiException.notFound("run not found"));
        if (!record.playerId.equals(playerId)) {
            throw ApiException.forbidden("run belongs to another player");
        }
        JsonNode result = EncounterPayload.parse(record.payload);
        return new HistoryDetail(
                EncounterPayload.summary(record.sequence, result), result, record.hash, record.prevHash);
    }

    public VerifyResponse verify(UUID playerId) {
        List<Link> links = HistoryRecord.<HistoryRecord>list("playerId = ?1 order by sequence asc", playerId)
                .stream()
                .map(r -> new Link(r.sequence, r.prevHash, r.hash, r.payload))
                .toList();
        VerifyResult result = HashChain.verify(secret, links);
        return new VerifyResponse(result.valid(), result.records(), result.firstInvalidSequence());
    }

    public StatsResponse stats(UUID playerId) {
        long total = HistoryRecord.count("playerId", playerId);
        long won = HistoryRecord.count("playerId = ?1 and outcome = ?2", playerId, "victory");
        long lost = HistoryRecord.count("playerId = ?1 and outcome = ?2", playerId, "defeat");
        return new StatsResponse(total, won, lost);
    }

    /** Transaction-scoped advisory lock on the player: held until commit or rollback. */
    private void lockPlayerChain(UUID playerId) {
        em.createNativeQuery("select pg_advisory_xact_lock(hashtext(:key))")
                .setParameter("key", "history:" + playerId)
                .getResultList();
    }

    private HistorySummary summaryOf(HistoryRecord record) {
        return EncounterPayload.summary(record.sequence, EncounterPayload.parse(record.payload));
    }
}
