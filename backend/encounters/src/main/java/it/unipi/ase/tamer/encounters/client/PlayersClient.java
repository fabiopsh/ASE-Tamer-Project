package it.unipi.ase.tamer.encounters.client;

import it.unipi.ase.tamer.encounters.security.ServiceTokenFilter;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/** Internal endpoint of players used by the run reset. */
@RegisterRestClient(configKey = "players")
@RegisterProvider(ServiceTokenFilter.class)
@RegisterProvider(RemoteErrorMapper.class)
@Path("/internal/players")
@Produces(MediaType.APPLICATION_JSON)
public interface PlayersClient {

    /** Rotates the run and starter pool of the player; idempotent once the run was rotated. */
    @POST
    @Path("/{pid}/new-run")
    @Consumes(MediaType.APPLICATION_JSON)
    @Timeout(2000)
    @Retry(maxRetries = 2, delay = 200, abortOn = RemoteClientError.class)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000, skipOn = RemoteClientError.class)
    NewRunResponse newRun(@PathParam("pid") UUID playerId, NewRunRequest request);
}
