package it.unipi.ase.tamer.players.api;

import it.unipi.ase.tamer.players.api.dto.CreatePlayerRequest;
import it.unipi.ase.tamer.players.api.dto.NewRunRequest;
import it.unipi.ase.tamer.players.api.dto.NewRunResponse;
import it.unipi.ase.tamer.players.api.dto.PlayerResponse;
import it.unipi.ase.tamer.players.error.ErrorResponse;
import it.unipi.ase.tamer.players.service.PlayersService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/internal/players")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("service")
@Tag(name = "players-internal")
public class PlayersInternalResource {

    @Inject
    PlayersService service;

    @POST
    @Operation(summary = "Create a profile idempotently, called by auth")
    @APIResponse(responseCode = "201", description = "Profile created",
            content = @Content(schema = @Schema(implementation = PlayerResponse.class)))
    @APIResponse(responseCode = "200", description = "Profile already existed",
            content = @Content(schema = @Schema(implementation = PlayerResponse.class)))
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "FORBIDDEN: not a service token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response create(@Valid @NotNull CreatePlayerRequest request) {
        // The service checks existence and creates atomically under the player advisory lock.
        var result = service.createProfile(request.playerId());
        return Response.status(result.created() ? 201 : 200).entity(result.player()).build();
    }

    @POST
    @Path("/{pid}/new-run")
    @Operation(summary = "Rotate the run and starter pool after a completed run")
    @APIResponse(responseCode = "200", description = "Profile with the new run (unchanged when repeated)",
            content = @Content(schema = @Schema(implementation = NewRunResponse.class)))
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "FORBIDDEN: not a service token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public NewRunResponse newRun(@PathParam("pid") UUID playerId, @Valid @NotNull NewRunRequest request) {
        return service.newRun(playerId, request.endedRunId());
    }
}
