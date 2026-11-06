package it.unipi.ase.tamer.auth.security;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Test-only endpoints used to check how real tokens are verified. */
@Path("/probe")
public class ProbeResource {

    @Inject
    CurrentPlayer player;

    @GET
    @Path("/player")
    @RolesAllowed("player")
    @Produces(MediaType.TEXT_PLAIN)
    public String player() {
        return player.id().toString();
    }

    @GET
    @Path("/service")
    @RolesAllowed("service")
    @Produces(MediaType.TEXT_PLAIN)
    public String service() {
        return "ok";
    }
}
