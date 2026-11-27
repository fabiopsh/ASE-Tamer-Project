package it.unipi.ase.tamer.monsters.api;

import it.unipi.ase.tamer.monsters.api.dto.MonsterCandidate;
import it.unipi.ase.tamer.monsters.api.dto.SpeciesStatsResponse;
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

@Path("/internal/monsters")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("service")
@Tag(name = "monsters-internal")
public class MonstersInternalResource {

    @Inject
    CatalogueService service;

    @GET
    @Operation(summary = "List species candidates for starter/wild generation")
    @APIResponse(responseCode = "200", description = "Candidate species")
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "FORBIDDEN: not a service token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<MonsterCandidate> candidates(@QueryParam("rarity") String rarity,
                                             @QueryParam("element") String element,
                                             @QueryParam("exclude") String exclude) {
        return service.candidates(rarity, element, exclude);
    }

    @GET
    @Path("/{id}/stats")
    @Operation(summary = "Get effective stats of a species at a level")
    @APIResponse(responseCode = "200", description = "Stats at the level",
            content = @Content(schema = @Schema(implementation = SpeciesStatsResponse.class)))
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR: level outside 1..50",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "FORBIDDEN: not a service token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public SpeciesStatsResponse stats(@PathParam("id") int id, @QueryParam("level") int level) {
        return service.stats(id, level);
    }
}
