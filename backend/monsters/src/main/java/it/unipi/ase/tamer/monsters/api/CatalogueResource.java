package it.unipi.ase.tamer.monsters.api;

import it.unipi.ase.tamer.monsters.api.dto.AbilityResponse;
import it.unipi.ase.tamer.monsters.api.dto.ElementsResponse;
import it.unipi.ase.tamer.monsters.api.dto.MonsterDetail;
import it.unipi.ase.tamer.monsters.api.dto.MonsterSummary;
import it.unipi.ase.tamer.monsters.api.dto.PageResponse;
import it.unipi.ase.tamer.monsters.error.ErrorResponse;
import it.unipi.ase.tamer.monsters.service.CatalogueService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("player")
@Tag(name = "monsters")
public class CatalogueResource {

    @Inject
    CatalogueService service;

    @GET
    @Path("/monsters")
    @Operation(summary = "Browse the monster catalogue (US-10)")
    @APIResponse(responseCode = "200", description = "Page of monster species")
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public PageResponse<MonsterSummary> catalogue(@QueryParam("element") String element,
                                                  @QueryParam("rarity") String rarity,
                                                  @QueryParam("ids") String ids,
                                                  @QueryParam("page") Integer page,
                                                  @QueryParam("size") Integer size) {
        return service.catalogue(element, rarity, ids, page, size);
    }

    @GET
    @Path("/monsters/{id}")
    @Operation(summary = "View a monster species and its ability (US-11)")
    @APIResponse(responseCode = "200", description = "Monster species with stats and ability",
            content = @Content(schema = @Schema(implementation = MonsterDetail.class)))
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "NOT_FOUND: unknown species",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public MonsterDetail monster(@PathParam("id") int id) {
        return service.detail(id);
    }

    @GET
    @Path("/abilities")
    @Operation(summary = "List all abilities")
    @APIResponse(responseCode = "200", description = "Abilities")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<AbilityResponse> abilities() {
        return service.abilities();
    }

    @GET
    @Path("/abilities/{id}")
    @Operation(summary = "View an ability")
    @APIResponse(responseCode = "200", description = "Ability",
            content = @Content(schema = @Schema(implementation = AbilityResponse.class)))
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "NOT_FOUND: unknown ability",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public AbilityResponse ability(@PathParam("id") int id) {
        return service.ability(id);
    }

    @GET
    @Path("/elements")
    @Operation(summary = "Get the element effectiveness cycle")
    @APIResponse(responseCode = "200", description = "Elements and effectiveness",
            content = @Content(schema = @Schema(implementation = ElementsResponse.class)))
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ElementsResponse elements() {
        return service.elements();
    }
}
