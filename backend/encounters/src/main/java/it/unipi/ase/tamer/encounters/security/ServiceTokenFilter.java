package it.unipi.ase.tamer.encounters.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;

/**
 * Adds the service token and the request id to every call of an internal REST client.
 * Register it with {@code @RegisterProvider(ServiceTokenFilter.class)} on internal clients only
 * (not on AuthClient).
 */
@ApplicationScoped
public class ServiceTokenFilter implements ClientRequestFilter {

    @Inject
    ServiceTokenProvider tokens;

    @Override
    public void filter(ClientRequestContext request) {
        request.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token());
        String requestId = RequestIdFilter.current();
        if (requestId != null) {
            request.getHeaders().putSingle(RequestIdFilter.HEADER, requestId);
        }
    }
}
