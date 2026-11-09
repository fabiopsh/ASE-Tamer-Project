package it.unipi.ase.tamer.encounters.client;

import it.unipi.ase.tamer.encounters.security.ServiceTokenFilter;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/** Internal endpoints of teams. */
@RegisterRestClient(configKey = "teams")
@RegisterProvider(ServiceTokenFilter.class)
@RegisterProvider(RemoteErrorMapper.class)
@Path("/internal/teams")
@Produces(MediaType.APPLICATION_JSON)
public interface TeamsClient {

    /** Revalidated team with member stats; {@code 409 TEAM_INVALID}, {@code 404} when not found or not owned. */
    @GET
    @Path("/{id}")
    @Timeout(2000)
    @Retry(maxRetries = 2, delay = 200, abortOn = RemoteClientError.class)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000, skipOn = RemoteClientError.class)
    TeamSnapshot team(@PathParam("id") UUID teamId, @QueryParam("player_id") UUID playerId);

    /** Removes the teams of an ended run; idempotent. */
    @POST
    @Path("/{pid}/reset")
    @Consumes(MediaType.APPLICATION_JSON)
    @Timeout(2000)
    @Retry(maxRetries = 2, delay = 200, abortOn = RemoteClientError.class)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000, skipOn = RemoteClientError.class)
    CountResponse reset(@PathParam("pid") UUID playerId, RunIdRequest request);
}
