package it.unipi.ase.tamers.players.player;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "monsters")
@Path("/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ClientHeaderParam(name = "X-Internal-Token", value = "${internal.token}")
public interface MonstersClient {
    @GET
    @Path("/monsters")
    List<PlayerDtos.MonsterInfo> allMonsters();

    @GET
    @Path("/collection/{playerId}")
    List<PlayerDtos.OwnedRef> collection(@PathParam("playerId") UUID playerId);

    @GET
    @Path("/collection/{playerId}/stats")
    PlayerDtos.CollectionStats collectionStats(@PathParam("playerId") UUID playerId);

    /** Returned as-is to the caller: the OwnedMonster payload belongs to the monsters service. */
    @POST
    @Path("/collection/batch")
    List<JsonNode> addBatch(PlayerDtos.BatchRequest request);
}
