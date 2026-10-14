package it.unipi.ase.tamers.encounters.client;

import it.unipi.ase.tamers.encounters.client.ClientDtos.*;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "monsters")
@Path("/internal")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ClientHeaderParam(name = "X-Internal-Token", value = "${internal.token}")
public interface MonstersClient {

    @GET
    @Path("/monsters")
    List<MonsterDetail> allMonsters();

    /** Only the monsters of the player among the given ids (comma separated), in the same order. */
    @GET
    @Path("/collection/{playerId}/owned")
    List<OwnedMonster> owned(@PathParam("playerId") UUID playerId, @QueryParam("ids") String ids);

    @POST
    @Path("/collection/xp")
    List<XpResult> awardXp(XpRequest request);

    @POST
    @Path("/collection")
    OwnedMonster capture(CaptureRequest request);
}
