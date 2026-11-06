package it.unipi.ase.tamer.players.security;

import it.unipi.ase.tamer.players.error.ApiException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/** Test-only endpoints exercising the cross-cutting code (INFRA-7). */
@Path("/probe")
public class ProbeResource {

    public record Body(@NotBlank String name) {
    }

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

    @POST
    @Path("/body")
    @RolesAllowed("player")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Body body(@Valid Body body, @QueryParam("size") @Max(50) Integer size) {
        return body;
    }

    @GET
    @Path("/conflict")
    @RolesAllowed("player")
    public String conflict() {
        throw new ApiException(409, "BATTLE_IN_PROGRESS", "A battle is already in progress.");
    }

    @GET
    @Path("/boom")
    @RolesAllowed("player")
    public String boom() {
        throw new IllegalStateException("secret internal detail");
    }
}
