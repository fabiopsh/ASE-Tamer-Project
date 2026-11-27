package it.unipi.ase.tamer.teams.api;

import it.unipi.ase.tamer.teams.api.dto.InternalTeamResponse;
import it.unipi.ase.tamer.teams.api.dto.ResetTeamsRequest;
import it.unipi.ase.tamer.teams.api.dto.ResetTeamsResponse;
import it.unipi.ase.tamer.teams.error.ApiException;
import it.unipi.ase.tamer.teams.error.ErrorCodes;
import it.unipi.ase.tamer.teams.error.ErrorResponse;
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
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

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
    @APIResponse(responseCode = "200", description = "Team with member stats",
            content = @Content(schema = @Schema(implementation = InternalTeamResponse.class)))
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "FORBIDDEN: not a service token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "409", description = "TEAM_INVALID",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public InternalTeamResponse team(@PathParam("id") UUID id, @QueryParam("player_id") UUID playerId) {
        if (playerId == null) {
            throw new ApiException(400, ErrorCodes.VALIDATION_ERROR,
                    "The player_id parameter is required.");
        }
        return service.internalTeam(playerId, id);
    }

    @POST
    @Path("/{pid}/reset")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Idempotently remove all teams of a completed run")
    @APIResponse(responseCode = "200", description = "Number of removed teams (0 when repeated)",
            content = @Content(schema = @Schema(implementation = ResetTeamsResponse.class)))
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "FORBIDDEN: not a service token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResetTeamsResponse reset(@PathParam("pid") UUID playerId, @Valid @NotNull ResetTeamsRequest request) {
        return service.reset(playerId, request.runId());
    }
}
