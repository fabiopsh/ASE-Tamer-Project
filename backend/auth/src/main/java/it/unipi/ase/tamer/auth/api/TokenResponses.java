package it.unipi.ase.tamer.auth.api;

import it.unipi.ase.tamer.auth.api.dto.TokenResponse;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Token responses must never be cached (RFC 6749 §5.1). */
final class TokenResponses {

    private TokenResponses() {
    }

    static Response ok(TokenResponse body) {
        return Response.ok(body, MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Pragma", "no-cache")
                .build();
    }
}
