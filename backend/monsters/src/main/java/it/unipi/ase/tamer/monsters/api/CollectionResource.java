package it.unipi.ase.tamer.monsters.api;

import it.unipi.ase.tamer.monsters.api.dto.OwnedMonsterResponse;
import it.unipi.ase.tamer.monsters.api.dto.PageResponse;
import it.unipi.ase.tamer.monsters.api.dto.UpdateNicknameRequest;
import it.unipi.ase.tamer.monsters.error.ErrorResponse;
import it.unipi.ase.tamer.monsters.security.CurrentPlayer;
import it.unipi.ase.tamer.monsters.service.CollectionService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/collection")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("player")
@Tag(name = "collection")
public class CollectionResource {

    @Inject
    CollectionService service;

    @Inject
    CurrentPlayer currentPlayer;

    @GET
    @Operation(summary = "View active or archived personal collection (US-09)")
    @APIResponse(responseCode = "200", description = "Page of owned monsters")
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public PageResponse<OwnedMonsterResponse> list(@QueryParam("run_id") UUID runId,
                                                   @QueryParam("page") Integer page,
                                                   @QueryParam("size") Integer size) {
        return service.collection(currentPlayer.id(), runId, page, size);
    }

    @GET
    @Path("/{owned_id}")
    @Operation(summary = "View an owned monster instance (US-11)")
    @APIResponse(responseCode = "200", description = "Owned monster",
            content = @Content(schema = @Schema(implementation = OwnedMonsterResponse.class)))
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "NOT_FOUND: missing or owned by another player",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public OwnedMonsterResponse detail(@PathParam("owned_id") UUID ownedId) {
        return service.detail(currentPlayer.id(), ownedId);
    }

    @PATCH
    @Path("/{owned_id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Set or clear an owned monster's nickname (US-13)")
    @APIResponse(responseCode = "200", description = "Updated owned monster",
            content = @Content(schema = @Schema(implementation = OwnedMonsterResponse.class)))
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "NOT_FOUND: missing or owned by another player",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public OwnedMonsterResponse updateNickname(@PathParam("owned_id") UUID ownedId,
                                               @Valid @NotNull UpdateNicknameRequest request) {
        return service.updateNickname(currentPlayer.id(), ownedId, request);
    }
}
