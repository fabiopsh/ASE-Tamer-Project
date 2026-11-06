package it.unipi.ase.tamer.auth.security;

import it.unipi.ase.tamer.auth.service.TokenIssuer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;

/**
 * {@code auth} signs its own service token locally (no HTTP round trip to itself) and adds it,
 * with the request id, to its internal calls.
 */
@ApplicationScoped
public class LocalServiceTokenFilter implements ClientRequestFilter {

    @Inject
    TokenIssuer issuer;

    @Override
    public void filter(ClientRequestContext request) {
        String token = issuer.serviceToken(TokenIssuer.SELF_CLIENT_ID).value();
        request.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        String requestId = RequestIdFilter.current();
        if (requestId != null) {
            request.getHeaders().putSingle(RequestIdFilter.HEADER, requestId);
        }
    }
}
