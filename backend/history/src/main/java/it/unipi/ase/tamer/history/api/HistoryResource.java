package it.unipi.ase.tamer.history.api;

import it.unipi.ase.tamer.history.api.dto.EncounterRecordResponse;
import it.unipi.ase.tamer.history.api.dto.PageResponse;
import it.unipi.ase.tamer.history.api.dto.RecordSummary;
import it.unipi.ase.tamer.history.security.CurrentPlayer;
import it.unipi.ase.tamer.history.service.HistoryService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/history")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("player")
@Tag(name = "history")
public class HistoryResource {

    @Inject
    HistoryService service;

    @Inject
    CurrentPlayer currentPlayer;

    @GET
    @Operation(summary = "List my previous encounters, newest first (US-23)")
    @APIResponse(responseCode = "200", description = "Page of encounter summaries")
    @APIResponse(responseCode = "400", description = "Invalid run_id, outcome, page or size (VALIDATION_ERROR)")
    @APIResponse(responseCode = "401", description = "Missing or invalid token")
    public PageResponse<RecordSummary> list(
            @Parameter(description = "Only encounters of this run") @QueryParam("run_id") String runId,
            @Parameter(description = "victory, defeat, fled, expired, interrupted or abandoned")
            @QueryParam("outcome") String outcome,
            @Parameter(description = "Zero-based page, default 0") @QueryParam("page") Integer page,
            @Parameter(description = "Page size, default 20, max 50") @QueryParam("size") Integer size) {
        return service.list(currentPlayer.id(), runId, outcome, page, size);
    }

    @GET
    @Path("/{encounter_id}")
    @Operation(summary = "View one previous encounter with its full turn log (US-24)")
    @APIResponse(responseCode = "200", description = "Full record")
    @APIResponse(responseCode = "401", description = "Missing or invalid token")
    @APIResponse(responseCode = "404", description = "Not found or not owned (NOT_FOUND)")
    public EncounterRecordResponse detail(@PathParam("encounter_id") UUID encounterId) {
        return service.detail(currentPlayer.id(), encounterId);
    }
}
