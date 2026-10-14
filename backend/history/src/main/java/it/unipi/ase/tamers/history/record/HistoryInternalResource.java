package it.unipi.ase.tamers.history.record;

import com.fasterxml.jackson.databind.JsonNode;
import it.unipi.ase.tamers.history.record.HistoryDtos.AppendResponse;
import it.unipi.ase.tamers.history.record.HistoryDtos.StatsResponse;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

/** Service-to-service endpoints; the X-Internal-Token filter guards /internal. */
@Path("/internal/history")
@PermitAll
public class HistoryInternalResource {
    @Inject
    HistoryService service;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response append(JsonNode body) {
        AppendResponse created = service.append(body);
        return Response.status(201).entity(created).build();
    }

    @GET
    @Path("/{player_id}/stats")
    public StatsResponse stats(@PathParam("player_id") UUID playerId) {
        return service.stats(playerId);
    }
}
