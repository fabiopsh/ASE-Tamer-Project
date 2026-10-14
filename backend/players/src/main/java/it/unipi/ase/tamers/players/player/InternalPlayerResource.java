package it.unipi.ase.tamers.players.player;

import it.unipi.ase.tamers.players.player.PlayerDtos.*;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

/** Service-to-service endpoints; the X-Internal-Token is checked by InternalTokenFilter. */
@Path("/internal/players")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InternalPlayerResource {
    @Inject
    PlayerService service;

    @POST
    public Response create(@Valid @NotNull CreatePlayerRequest request) {
        return Response.status(201).entity(service.create(request.playerId(), request.username())).build();
    }

    @GET
    @Path("/{playerId}")
    public PlayerProfile get(@PathParam("playerId") UUID playerId) {
        return service.get(playerId);
    }

    @POST
    @Path("/{playerId}/advance-stage")
    public PlayerProfile advanceStage(@PathParam("playerId") UUID playerId,
                                      @Valid @NotNull AdvanceStageRequest request) {
        return service.advanceStage(playerId, request.clearedStage());
    }
}
