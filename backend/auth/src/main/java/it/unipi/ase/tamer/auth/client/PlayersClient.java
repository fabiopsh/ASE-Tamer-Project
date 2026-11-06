package it.unipi.ase.tamer.auth.client;

import it.unipi.ase.tamer.auth.security.LocalServiceTokenFilter;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/** Internal API of {@code players}. */
@RegisterRestClient(configKey = "players")
@RegisterProvider(LocalServiceTokenFilter.class)
@Path("/internal/players")
public interface PlayersClient {

    /** Creates the player profile. Idempotent on {@code player_id}, so it can be retried. */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Timeout(2000)
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000)
    void create(CreatePlayerRequest request);
}
