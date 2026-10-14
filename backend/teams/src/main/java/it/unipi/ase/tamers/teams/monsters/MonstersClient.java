package it.unipi.ase.tamers.teams.monsters;

import it.unipi.ase.tamers.teams.monsters.MonstersDtos.OwnedMonster;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "monsters")
@Path("/internal/collection")
@ClientHeaderParam(name = "X-Internal-Token", value = "${internal.token}")
public interface MonstersClient {
    /** Returns only the monsters of the player among the given ids (comma separated). */
    @GET
    @Path("/{player_id}/owned")
    List<OwnedMonster> owned(@PathParam("player_id") UUID playerId, @QueryParam("ids") String ids);
}
