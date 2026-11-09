package it.unipi.ase.tamer.encounters.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/** OAuth2 client credentials grant on {@code auth}. Never register ServiceTokenFilter here. */
@RegisterRestClient(configKey = "auth")
@Path("/internal/auth/token")
public interface AuthClient {

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @Timeout(2000)
    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000)
    TokenResponse token(@FormParam("grant_type") String grantType,
                        @FormParam("client_id") String clientId,
                        @FormParam("client_secret") String clientSecret);
}
