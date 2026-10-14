package it.unipi.ase.tamers.teams.common;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/** Service-to-service calls under /internal must carry the shared X-Internal-Token. */
public class InternalTokenFilter {
    @ConfigProperty(name = "internal.token")
    String token;

    @ServerRequestFilter(preMatching = true)
    public Response check(ContainerRequestContext ctx) {
        if (!ctx.getUriInfo().getPath().startsWith("/internal")) {
            return null;
        }
        String given = ctx.getHeaderString("X-Internal-Token");
        if (given != null && MessageDigest.isEqual(
                given.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        return Response.status(401).type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("unauthorized", "missing or invalid internal token", null)).build();
    }
}
