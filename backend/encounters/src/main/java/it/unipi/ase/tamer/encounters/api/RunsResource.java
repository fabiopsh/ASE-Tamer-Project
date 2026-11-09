package it.unipi.ase.tamer.encounters.api;

import it.unipi.ase.tamer.encounters.api.dto.NewRunStatusResponse;
import it.unipi.ase.tamer.encounters.api.dto.PageResponse;
import it.unipi.ase.tamer.encounters.api.dto.RunResponse;
import it.unipi.ase.tamer.encounters.security.CurrentPlayer;
import it.unipi.ase.tamer.encounters.service.RunService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Runs of the player: list, current, detail, abandon (US-21), new run after a won run. */
@Path("/api/runs")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("player")
@Tag(name = "runs")
public class RunsResource {

    @Inject
    CurrentPlayer player;

    @Inject
    RunService runs;

    @GET
    @Operation(summary = "Runs of the player, newest first")
    @APIResponse(responseCode = "200", description = "Page of runs")
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR (page < 0, size outside 1-50)")
    public PageResponse<RunResponse> list(@QueryParam("page") Integer page, @QueryParam("size") Integer size) {
        return runs.runs(player.id(), page, size);
    }

    @GET
    @Path("/current")
    @Operation(summary = "Active run, or the latest won run waiting for a new run")
    @APIResponse(responseCode = "200", description = "The run")
    @APIResponse(responseCode = "404", description = "NO_ACTIVE_RUN: choose the starters to open a run")
    public RunResponse current() {
        return runs.current(player.id());
    }

    @GET
    @Path("/{run_id}")
    @Operation(summary = "Run detail (own runs only)")
    @APIResponse(responseCode = "200", description = "The run")
    @APIResponse(responseCode = "404", description = "Not found or not owned")
    public RunResponse get(@PathParam("run_id") UUID runId) {
        return runs.run(player.id(), runId);
    }

    @POST
    @Path("/current/abandon")
    @Operation(summary = "Abandon the active run (US-21)",
            description = "Closes any battle as abandoned (history record, no reward), sets the run to abandoned "
                    + "and resets it: collection archived, teams removed, new starter pool. If a dependency is "
                    + "down the run is still abandoned and reset_pending is true; the reset resumes by itself.")
    @APIResponse(responseCode = "200", description = "The abandoned run")
    @APIResponse(responseCode = "409", description = "NO_ACTIVE_RUN or RESET_PENDING")
    public RunResponse abandon() {
        return runs.abandon(player.id());
    }

    @POST
    @Operation(summary = "Start over after a won run",
            description = "Resets the won run (archive, teams reset, new starter pool); the player then chooses "
                    + "new starters with POST /api/players/me/starters. Idempotent.")
    @APIResponse(responseCode = "202", description = "{\"status\": \"choose_starters\"}",
            content = @Content(schema = @Schema(implementation = NewRunStatusResponse.class)))
    @APIResponse(responseCode = "409", description = "NEW_RUN_NOT_ALLOWED (latest run not won) or RESET_PENDING")
    @APIResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE: the reset will be resumed")
    public Response newRun() {
        return Response.status(202).entity(runs.newRun(player.id())).build();
    }
}
