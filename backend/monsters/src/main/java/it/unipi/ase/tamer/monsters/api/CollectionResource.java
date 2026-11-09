package it.unipi.ase.tamer.monsters.api;

import it.unipi.ase.tamer.monsters.security.CurrentPlayer;
import it.unipi.ase.tamer.monsters.service.CollectionService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

@Path("/api/collection")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("player")
@Tag(name = "collection")
public class CollectionResource {

    @Inject
    CollectionService service;

    @Inject
    CurrentPlayer currentPlayer;

    @GET
    @Operation(summary = "View active or archived personal collection (US-09)")
    @APIResponse(responseCode = "200", description = "Page of owned monsters")
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    public Object list(@QueryParam("run_id") UUID runId, @QueryParam("page") Integer page,
                       @QueryParam("size") Integer size) {
        return service.collection(currentPlayer.id(), runId, page, size);
    }

    @GET
    @Path("/{owned_id}")
    @Operation(summary = "View an owned monster instance (US-11)")
    @APIResponse(responseCode = "200", description = "Owned monster")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    @APIResponse(responseCode = "404", description = "NOT_FOUND: missing or owned by another player")
    public Object detail(@PathParam("owned_id") UUID ownedId) {
        return service.detail(currentPlayer.id(), ownedId);
    }
}
