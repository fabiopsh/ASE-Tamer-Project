package it.unipi.ase.tamers.auth.account;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "players")
@Path("/internal/players")
@ClientHeaderParam(name = "X-Internal-Token", value = "${internal.token}")
public interface PlayersClient {
    @POST
    void create(AuthDtos.CreatePlayerRequest request);
}
