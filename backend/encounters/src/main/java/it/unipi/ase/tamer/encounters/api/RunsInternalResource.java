package it.unipi.ase.tamer.encounters.api;

import it.unipi.ase.tamer.encounters.api.dto.OpenRunRequest;
import it.unipi.ase.tamer.encounters.service.RunService;
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

/** Called by players once the starters are granted. */
@Path("/internal/runs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("service")
@Tag(name = "runs-internal")
public class RunsInternalResource {

    @Inject
    RunService runs;

    @POST
    @Operation(summary = "Open a run at stage 1; idempotent on run_id")
    @APIResponse(responseCode = "201", description = "Run created")
    @APIResponse(responseCode = "200", description = "Run already existed (retry)")
    @APIResponse(responseCode = "409", description = "RUN_CONFLICT: another active run, or run of another player")
    public Response open(@Valid @NotNull OpenRunRequest request) {
        RunService.Opened opened = runs.open(request.runId(), request.playerId());
        return Response.status(opened.created() ? 201 : 200).entity(opened.run()).build();
    }
}
