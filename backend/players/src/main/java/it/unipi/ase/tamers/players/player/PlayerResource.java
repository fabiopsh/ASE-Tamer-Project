package it.unipi.ase.tamers.players.player;

import com.fasterxml.jackson.databind.JsonNode;
import it.unipi.ase.tamers.players.player.PlayerDtos.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/players")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PlayerResource {
    @Inject
    JsonWebToken jwt;
    @Inject
    PlayerService service;

    @GET
    @Path("/me")
    @RolesAllowed("player")
    public PlayerProfile me() {
        return service.me(playerId());
    }

    @PATCH
    @Path("/me")
    @RolesAllowed("player")
    public PlayerProfile update(@NotNull UpdateProfileRequest request) {
        return service.update(playerId(), request);
    }

    @POST
    @Path("/me/starters")
    @RolesAllowed("player")
    public Response starters(@Valid @NotNull StartersRequest request) {
        java.util.List<JsonNode> owned = service.chooseStarters(playerId(), request.monsterIds());
        return Response.status(201).entity(owned).build();
    }

    private UUID playerId() {
        return UUID.fromString(jwt.getSubject());
    }
}
