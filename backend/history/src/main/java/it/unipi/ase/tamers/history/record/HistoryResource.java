package it.unipi.ase.tamers.history.record;

import it.unipi.ase.tamers.history.common.ApiException;
import it.unipi.ase.tamers.history.record.HistoryDtos.HistoryDetail;
import it.unipi.ase.tamers.history.record.HistoryDtos.HistoryPage;
import it.unipi.ase.tamers.history.record.HistoryDtos.VerifyResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/history")
@RolesAllowed("player")
public class HistoryResource {
    private static final int MAX_SIZE = 100;

    @Inject
    JsonWebToken jwt;
    @Inject
    HistoryService service;

    @GET
    public HistoryPage list(@QueryParam("outcome") String outcome,
                            @QueryParam("page") String page,
                            @QueryParam("size") String size) {
        if (outcome != null && !outcome.isBlank() && !EncounterPayload.OUTCOMES.contains(outcome)) {
            throw ApiException.badRequest("outcome must be victory or defeat");
        }
        int pageIndex = intParam(page, "page", 0);
        int pageSize = intParam(size, "size", 20);
        if (pageIndex < 0) {
            throw ApiException.badRequest("page must be >= 0");
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw ApiException.badRequest("size must be between 1 and " + MAX_SIZE);
        }
        String filter = outcome == null || outcome.isBlank() ? null : outcome;
        return service.list(currentPlayer(), filter, pageIndex, pageSize);
    }

    /** Declared explicitly so it is never captured by /{run_id}. */
    @GET
    @Path("/verify")
    public VerifyResponse verify() {
        return service.verify(currentPlayer());
    }

    @GET
    @Path("/{run_id}")
    public HistoryDetail detail(@PathParam("run_id") String runId) {
        UUID id;
        try {
            id = UUID.fromString(runId);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("run not found");
        }
        return service.detail(id, currentPlayer());
    }

    private UUID currentPlayer() {
        return UUID.fromString(jwt.getSubject());
    }

    private static int intParam(String raw, String name, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(name + " must be an integer");
        }
    }
}
