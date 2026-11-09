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
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Internal endpoints of monsters. Every call is idempotent (the POSTs on {@code encounter_id} / {@code run_id}),
 * so all of them are retried. 4xx answers ({@link RemoteClientError}) are neither retried nor counted by the
 * circuit breaker.
 */
@RegisterRestClient(configKey = "monsters")
@RegisterProvider(ServiceTokenFilter.class)
@RegisterProvider(RemoteErrorMapper.class)
@Path("/internal")
@Produces(MediaType.APPLICATION_JSON)
public interface MonstersClient {

    /** Candidate species of an element, without the {@code exclude} ids (comma separated). */
    @GET
    @Path("/monsters")
    @Timeout(2000)
    @Retry(maxRetries = 2, delay = 200, abortOn = RemoteClientError.class)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000, skipOn = RemoteClientError.class)
    List<MonsterCandidate> candidates(@QueryParam("element") String element, @QueryParam("exclude") String exclude);

    @GET
    @Path("/monsters/{id}/stats")
    @Timeout(2000)
    @Retry(maxRetries = 2, delay = 200, abortOn = RemoteClientError.class)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000, skipOn = RemoteClientError.class)
    SpeciesStats stats(@PathParam("id") int monsterId, @QueryParam("level") int level);

    @POST
    @Path("/collections/{pid}/monsters")
    @Consumes(MediaType.APPLICATION_JSON)
    @Timeout(2000)
    @Retry(maxRetries = 2, delay = 200, abortOn = RemoteClientError.class)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000, skipOn = RemoteClientError.class)
    OwnedMonster reward(@PathParam("pid") UUID playerId, RewardRequest request);

    @POST
    @Path("/collections/{pid}/xp")
    @Consumes(MediaType.APPLICATION_JSON)
    @Timeout(2000)
    @Retry(maxRetries = 2, delay = 200, abortOn = RemoteClientError.class)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000, skipOn = RemoteClientError.class)
    List<XpGain> xp(@PathParam("pid") UUID playerId, XpRequest request);

    @POST
    @Path("/collections/{pid}/archive")
    @Consumes(MediaType.APPLICATION_JSON)
    @Timeout(2000)
    @Retry(maxRetries = 2, delay = 200, abortOn = RemoteClientError.class)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000, skipOn = RemoteClientError.class)
    CountResponse archive(@PathParam("pid") UUID playerId, RunIdRequest request);
}
