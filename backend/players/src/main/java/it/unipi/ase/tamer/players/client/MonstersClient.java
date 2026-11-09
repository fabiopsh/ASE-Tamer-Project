package it.unipi.ase.tamer.players.client;

import it.unipi.ase.tamer.players.api.dto.StarterEntry;
import it.unipi.ase.tamer.players.security.ServiceTokenFilter;
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
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/** The only access players has to the monsters database. */
@RegisterRestClient(configKey = "monsters")
@RegisterProvider(ServiceTokenFilter.class)
public interface MonstersClient {

    @GET
    @Path("/internal/monsters")
    @Produces(MediaType.APPLICATION_JSON)
    @Timeout(2000)
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000)
    List<MonsterCandidate> candidates(@QueryParam("rarity") String rarity);

    @POST
    @Path("/internal/collections/{pid}/starters")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Timeout(2000)
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000)
    List<StarterEntry> createStarters(@PathParam("pid") UUID playerId, StarterGrantRequest request);
}
