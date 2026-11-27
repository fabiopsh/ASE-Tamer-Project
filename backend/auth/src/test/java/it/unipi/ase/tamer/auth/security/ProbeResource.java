package it.unipi.ase.tamer.auth.security;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

/** Test-only endpoints used to check how real tokens are verified. */
@Path("/probe")
public class ProbeResource {

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/player")
    @RolesAllowed("player")
    @Produces(MediaType.TEXT_PLAIN)
    public String player() {
        return jwt.getSubject();
    }

    @GET
    @Path("/service")
    @RolesAllowed("service")
    @Produces(MediaType.TEXT_PLAIN)
    public String service() {
        return "ok";
    }
}
