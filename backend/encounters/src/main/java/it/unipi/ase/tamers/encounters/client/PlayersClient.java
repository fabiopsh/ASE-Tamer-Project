package it.unipi.ase.tamers.encounters.client;

import it.unipi.ase.tamers.encounters.client.ClientDtos.AdvanceStageRequest;
import it.unipi.ase.tamers.encounters.client.ClientDtos.PlayerProfile;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "players")
@Path("/internal/players")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ClientHeaderParam(name = "X-Internal-Token", value = "${internal.token}")
public interface PlayersClient {

    @GET
    @Path("/{playerId}")
    PlayerProfile profile(@PathParam("playerId") UUID playerId);

    @POST
    @Path("/{playerId}/advance-stage")
    PlayerProfile advanceStage(@PathParam("playerId") UUID playerId, AdvanceStageRequest request);
}
