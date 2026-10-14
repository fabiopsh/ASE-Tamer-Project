package it.unipi.ase.tamers.encounters.client;

import it.unipi.ase.tamers.encounters.client.ClientDtos.TeamRef;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "teams")
@Path("/internal/teams")
@Produces(MediaType.APPLICATION_JSON)
@ClientHeaderParam(name = "X-Internal-Token", value = "${internal.token}")
public interface TeamsClient {

    /** 404 when the team does not exist or belongs to another player. */
    @GET
    @Path("/{teamId}")
    TeamRef team(@PathParam("teamId") UUID teamId, @QueryParam("player_id") UUID playerId);
}
