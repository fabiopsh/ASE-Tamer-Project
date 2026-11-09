package it.unipi.ase.tamer.history.api;

import it.unipi.ase.tamer.history.api.dto.EncounterRecordRequest;
import it.unipi.ase.tamer.history.service.HistoryService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/internal/history")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("service")
@Tag(name = "history-internal")
public class HistoryInternalResource {

    @Inject
    HistoryService service;

    @POST
    @Operation(summary = "Store a finished encounter; idempotent on encounter_id (called by encounters)")
    @APIResponse(responseCode = "201", description = "Stored; body is the summary of the new record")
    @APIResponse(responseCode = "200", description = "Already stored: nothing changed; body is the stored summary")
    @APIResponse(responseCode = "400", description = "Invalid body (VALIDATION_ERROR)")
    @APIResponse(responseCode = "401", description = "Missing or invalid token")
    @APIResponse(responseCode = "403", description = "Not a service token (FORBIDDEN)")
    public Response store(@Valid @NotNull EncounterRecordRequest request) {
        HistoryService.StoreResult result = service.store(request);
        return Response.status(result.created() ? Response.Status.CREATED : Response.Status.OK)
                .entity(result.summary())
                .build();
    }
}
