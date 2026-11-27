package it.unipi.ase.tamer.monsters.api;

import it.unipi.ase.tamer.monsters.api.dto.ArchiveRequest;
import it.unipi.ase.tamer.monsters.api.dto.ArchiveResponse;
import it.unipi.ase.tamer.monsters.api.dto.OwnedMonsterResponse;
import it.unipi.ase.tamer.monsters.api.dto.RewardRequest;
import it.unipi.ase.tamer.monsters.api.dto.StarterEntry;
import it.unipi.ase.tamer.monsters.api.dto.StarterGrantRequest;
import it.unipi.ase.tamer.monsters.api.dto.XpGainResponse;
import it.unipi.ase.tamer.monsters.api.dto.XpGrantRequest;
import it.unipi.ase.tamer.monsters.error.ErrorResponse;
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
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
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
    @APIResponse(responseCode = "200", description = "Owned instances with stats")
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "FORBIDDEN: not a service token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<OwnedMonsterResponse> instances(@PathParam("pid") UUID playerId, @QueryParam("ids") String ids) {
        return service.instances(playerId, ids);
    }

    @POST
    @Path("/starters")
    @Operation(summary = "Idempotently grant three common starters for a run")
    @APIResponse(responseCode = "201", description = "Starters created",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = StarterEntry.class)))
    @APIResponse(responseCode = "200", description = "Already granted for this run: the same instances",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = StarterEntry.class)))
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR or INVALID_COLLECTION_OPERATION",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "FORBIDDEN: not a service token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response starters(@PathParam("pid") UUID playerId,
                             @Valid @NotNull StarterGrantRequest request) {
        var grant = service.starters(playerId, request);
        return Response.status(grant.created() ? 201 : 200).entity(grant.starters()).build();
    }

    @POST
    @Path("/monsters")
    @Operation(summary = "Idempotently grant an encounter reward (US-12)")
    @APIResponse(responseCode = "201", description = "Reward created",
            content = @Content(schema = @Schema(implementation = OwnedMonsterResponse.class)))
    @APIResponse(responseCode = "200", description = "Already granted for this encounter: the same instance",
            content = @Content(schema = @Schema(implementation = OwnedMonsterResponse.class)))
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "FORBIDDEN: not a service token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response reward(@PathParam("pid") UUID playerId, @Valid @NotNull RewardRequest request) {
        var grant = service.reward(playerId, request);
        return Response.status(grant.created() ? 201 : 200).entity(grant.monster()).build();
    }

    @POST
    @Path("/xp")
    @Operation(summary = "Idempotently grant XP to active owned instances")
    @APIResponse(responseCode = "200", description = "XP applied (or already applied for this encounter)")
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "FORBIDDEN: not a service token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<XpGainResponse> xp(@PathParam("pid") UUID playerId, @Valid @NotNull XpGrantRequest request) {
        return service.grantXp(playerId, request);
    }

    @POST
    @Path("/archive")
    @Operation(summary = "Archive active monsters of a completed run")
    @APIResponse(responseCode = "200", description = "Number of archived monsters (0 when repeated)",
            content = @Content(schema = @Schema(implementation = ArchiveResponse.class)))
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "FORBIDDEN: not a service token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ArchiveResponse archive(@PathParam("pid") UUID playerId, @Valid @NotNull ArchiveRequest request) {
        return service.archive(playerId, request);
    }
}
