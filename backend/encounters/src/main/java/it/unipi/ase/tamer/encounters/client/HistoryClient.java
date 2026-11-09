package it.unipi.ase.tamer.encounters.client;

import com.fasterxml.jackson.databind.JsonNode;
import it.unipi.ase.tamer.encounters.security.ServiceTokenFilter;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/** {@code POST /internal/history}: idempotent on {@code encounter_id} (201 first insert, 200 repeat). */
@RegisterRestClient(configKey = "history")
@RegisterProvider(ServiceTokenFilter.class)
@RegisterProvider(RemoteErrorMapper.class)
@Path("/internal/history")
@Produces(MediaType.APPLICATION_JSON)
public interface HistoryClient {

    /** {@code record} is the snake_case record built by {@code HistoryRecordBuilder}. */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Timeout(2000)
    @Retry(maxRetries = 2, delay = 200, abortOn = RemoteClientError.class)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000, skipOn = RemoteClientError.class)
    JsonNode store(JsonNode record);
}
