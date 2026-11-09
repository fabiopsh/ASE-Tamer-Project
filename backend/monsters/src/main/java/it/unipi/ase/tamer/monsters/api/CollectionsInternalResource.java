package it.unipi.ase.tamer.monsters.api;

import it.unipi.ase.tamer.monsters.api.dto.ArchiveRequest;
import it.unipi.ase.tamer.monsters.api.dto.RewardRequest;
import it.unipi.ase.tamer.monsters.api.dto.StarterGrantRequest;
import it.unipi.ase.tamer.monsters.api.dto.XpGrantRequest;
import it.unipi.ase.tamer.monsters.service.CollectionService;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/internal/collections/{pid}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("service")
@Tag(name = "collections-internal")
public class CollectionsInternalResource {

    @Inject
    CollectionService service;

    @GET
    @Path("/instances")
    @Operation(summary = "Get player-owned instances and stats for teams/encounters")
    public Object instances(@PathParam("pid") UUID playerId, @QueryParam("ids") String ids) {
        return service.instances(playerId, ids);
    }

    @POST
    @Path("/starters")
    @Operation(summary = "Idempotently grant three common starters for a run")
    public Response starters(@PathParam("pid") UUID playerId,
                             @Valid @NotNull StarterGrantRequest request) {
        var grant = service.starters(playerId, request);
        return Response.status(grant.created() ? 201 : 200).entity(grant.starters()).build();
    }

    @POST
    @Path("/monsters")
    @Operation(summary = "Idempotently grant an encounter reward (US-12)")
    public Response reward(@PathParam("pid") UUID playerId, @Valid @NotNull RewardRequest request) {
        var grant = service.reward(playerId, request);
        return Response.status(grant.created() ? 201 : 200).entity(grant.monster()).build();
    }

    @POST
    @Path("/xp")
    @Operation(summary = "Idempotently grant XP to active owned instances")
    public Object xp(@PathParam("pid") UUID playerId, @Valid @NotNull XpGrantRequest request) {
        return service.grantXp(playerId, request);
    }

    @POST
    @Path("/archive")
    @Operation(summary = "Archive active monsters of a completed run")
    public Object archive(@PathParam("pid") UUID playerId, @Valid @NotNull ArchiveRequest request) {
        return service.archive(playerId, request);
    }
}
