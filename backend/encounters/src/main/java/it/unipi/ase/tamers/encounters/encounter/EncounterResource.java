package it.unipi.ase.tamers.encounters.encounter;

import it.unipi.ase.tamers.encounters.encounter.EncounterDtos.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/encounters")
@RolesAllowed("player")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EncounterResource {
    @Inject
    JsonWebToken jwt;
    @Inject
    EncounterService service;

    private UUID playerId() {
        return UUID.fromString(jwt.getSubject());
    }

    @GET
    public BoardView board() {
        return service.board(playerId());
    }

    @GET
    @Path("/{encounterId}")
    public OfferView offer(@PathParam("encounterId") UUID encounterId) {
        return service.offer(playerId(), encounterId);
    }

    @POST
    @Path("/{encounterId}/start")
    public Response start(@PathParam("encounterId") UUID encounterId, @Valid @NotNull StartRequest request) {
        return Response.status(201).entity(service.start(playerId(), encounterId, request.teamId())).build();
    }

    @GET
    @Path("/runs/{runId}")
    public EncounterResult run(@PathParam("runId") UUID runId) {
        return service.run(playerId(), runId);
    }
}
