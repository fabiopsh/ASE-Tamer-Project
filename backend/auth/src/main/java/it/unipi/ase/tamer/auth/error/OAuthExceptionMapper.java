package it.unipi.ase.tamer.auth.error;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** OAuth2 errors use their own format, not the common one. Responses are never cached. */
public class OAuthExceptionMapper {

    @ServerExceptionMapper
    public Response oauth(OAuthException e) {
        return Response.status(e.status())
                .type(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Pragma", "no-cache")
                .entity(new OAuthErrorResponse(e.error(), e.getMessage()))
                .build();
    }
}
