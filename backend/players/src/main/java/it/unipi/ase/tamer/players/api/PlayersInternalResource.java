package it.unipi.ase.tamer.players.api;

import it.unipi.ase.tamer.players.api.dto.CreatePlayerRequest;
import it.unipi.ase.tamer.players.api.dto.NewRunRequest;
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
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

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
    @APIResponse(responseCode = "201", description = "Profile created")
    @APIResponse(responseCode = "200", description = "Profile already existed")
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    @APIResponse(responseCode = "403", description = "FORBIDDEN: not a service token")
    @APIResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE")
    public Response create(@Valid @NotNull CreatePlayerRequest request) {
        // The service call is idempotent even when auth retries after a timeout.
        boolean exists = it.unipi.ase.tamer.players.domain.Player.findById(request.playerId()) != null;
        var profile = service.create(request.playerId());
        return Response.status(exists ? 200 : 201).entity(profile).build();
    }

    @POST
    @Path("/{pid}/new-run")
    @Operation(summary = "Rotate the run and starter pool after a completed run")
    @APIResponse(responseCode = "200", description = "Profile with the new run (unchanged when repeated)")
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    @APIResponse(responseCode = "403", description = "FORBIDDEN: not a service token")
    @APIResponse(responseCode = "404", description = "NOT_FOUND")
    @APIResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE")
    public Object newRun(@PathParam("pid") UUID playerId, @Valid @NotNull NewRunRequest request) {
        return service.newRun(playerId, request.endedRunId());
    }
}
