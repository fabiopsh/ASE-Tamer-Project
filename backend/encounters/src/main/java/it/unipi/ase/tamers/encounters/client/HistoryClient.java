package it.unipi.ase.tamers.encounters.client;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "history")
@Path("/internal/history")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ClientHeaderParam(name = "X-Internal-Token", value = "${internal.token}")
public interface HistoryClient {

    /** Body: the EncounterResult JSON plus player_id. */
    @POST
    JsonNode record(JsonNode encounterResultWithPlayer);
}
