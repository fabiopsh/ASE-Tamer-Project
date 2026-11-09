package it.unipi.ase.tamer.players.api;

import it.unipi.ase.tamer.players.api.dto.ChooseStartersRequest;
import it.unipi.ase.tamer.players.error.ErrorResponse;
import it.unipi.ase.tamer.players.security.CurrentPlayer;
import it.unipi.ase.tamer.players.service.PlayersService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/players/me")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("player")
@Tag(name = "players")
public class PlayersResource {

    @Inject
    PlayersService service;

    @Inject
    CurrentPlayer currentPlayer;

    @GET
    @Operation(summary = "Read my player profile (US-03)")
    @APIResponse(responseCode = "200", description = "Player profile")
    @APIResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Object profile() {
        return service.profile(currentPlayer.id());
    }

    @GET
    @Path("/starters")
    @Operation(summary = "Read the persistent starter pool (US-06)")
    public Object starterPool() {
        return service.starterPool(currentPlayer.id());
    }

    @POST
    @Path("/starters")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Choose three starter monsters and open the run (US-06, US-07)")
    @APIResponse(responseCode = "201", description = "Starters chosen")
    @APIResponse(responseCode = "400", description = "INVALID_STARTERS")
    @APIResponse(responseCode = "409", description = "STARTERS_ALREADY_CHOSEN")
    @APIResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE")
    public Response choose(@Valid @NotNull ChooseStartersRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.choose(currentPlayer.id(), request)).build();
    }
}
