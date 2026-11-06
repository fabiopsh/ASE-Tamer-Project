package it.unipi.ase.tamer.encounters.security;

import it.unipi.ase.tamer.encounters.client.AuthClient;
import it.unipi.ase.tamer.encounters.client.TokenResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Obtains a service token from {@code auth} (client credentials grant) and caches it until
 * 30 seconds before it expires. Thread-safe.
 */
@ApplicationScoped
public class ServiceTokenProvider {

    private static final long SAFETY_MARGIN_SECONDS = 30;

    @Inject
    @RestClient
    AuthClient authClient;

    @ConfigProperty(name = "tamer.service-client.id")
    String clientId;

    @ConfigProperty(name = "tamer.service-client.secret")
    String clientSecret;

    private volatile CachedToken cached;

    public String token() {
        CachedToken current = cached;
        if (current != null && Instant.now().isBefore(current.refreshAt())) {
            return current.value();
        }
        synchronized (this) {
            current = cached;
            if (current != null && Instant.now().isBefore(current.refreshAt())) {
                return current.value();
            }
            TokenResponse response = authClient.token("client_credentials", clientId, clientSecret);
            cached = new CachedToken(response.accessToken(),
                    Instant.now().plusSeconds(response.expiresIn() - SAFETY_MARGIN_SECONDS));
            return response.accessToken();
        }
    }

    private record CachedToken(String value, Instant refreshAt) {
    }
}
