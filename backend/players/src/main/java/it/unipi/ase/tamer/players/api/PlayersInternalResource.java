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
    public Response create(@Valid @NotNull CreatePlayerRequest request) {
        // The service call is idempotent even when auth retries after a timeout.
        boolean exists = it.unipi.ase.tamer.players.domain.Player.findById(request.playerId()) != null;
        var profile = service.create(request.playerId());
        return Response.status(exists ? 200 : 201).entity(profile).build();
    }

    @POST
    @Path("/{pid}/new-run")
    @Operation(summary = "Rotate the run and starter pool after a completed run")
    public Object newRun(@PathParam("pid") UUID playerId, @Valid @NotNull NewRunRequest request) {
        return service.newRun(playerId, request.endedRunId());
    }
}
