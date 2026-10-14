package it.unipi.ase.tamers.players.player;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "history")
@Path("/internal/history")
@Produces(MediaType.APPLICATION_JSON)
@ClientHeaderParam(name = "X-Internal-Token", value = "${internal.token}")
public interface HistoryClient {
    @GET
    @Path("/{playerId}/stats")
    PlayerDtos.HistoryStats stats(@PathParam("playerId") UUID playerId);
}
