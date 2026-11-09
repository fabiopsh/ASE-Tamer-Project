package it.unipi.ase.tamer.teams.api;

import it.unipi.ase.tamer.teams.api.dto.ResetTeamsRequest;
import it.unipi.ase.tamer.teams.service.TeamsService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

@Path("/internal/teams")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("service")
@Tag(name = "teams-internal")
public class TeamsInternalResource {

    @Inject
    TeamsService service;

    @GET
    @Path("/{id}")
    @Operation(summary = "Revalidate a team and return full member stats for a battle")
    @APIResponse(responseCode = "200", description = "Team with member stats")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    @APIResponse(responseCode = "403", description = "FORBIDDEN: not a service token")
    @APIResponse(responseCode = "404", description = "NOT_FOUND")
    @APIResponse(responseCode = "409", description = "TEAM_INVALID")
    @APIResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE")
    public Object team(@PathParam("id") UUID id, @QueryParam("player_id") UUID playerId) {
        if (playerId == null) {
            throw new it.unipi.ase.tamer.teams.error.ApiException(400, "VALIDATION_ERROR",
                    "The player_id parameter is required.");
        }
        return service.internalTeam(playerId, id);
    }

    @POST
    @Path("/{pid}/reset")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Idempotently remove all teams of a completed run")
    @APIResponse(responseCode = "200", description = "Number of removed teams (0 when repeated)")
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    @APIResponse(responseCode = "403", description = "FORBIDDEN: not a service token")
    public Object reset(@PathParam("pid") UUID playerId, @Valid @NotNull ResetTeamsRequest request) {
        return service.reset(playerId, request.runId());
    }
}
