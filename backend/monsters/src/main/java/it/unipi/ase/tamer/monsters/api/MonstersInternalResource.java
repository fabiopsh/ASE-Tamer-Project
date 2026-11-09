package it.unipi.ase.tamer.monsters.api;

import it.unipi.ase.tamer.monsters.service.CatalogueService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

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
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    @APIResponse(responseCode = "403", description = "FORBIDDEN: not a service token")
    public Object candidates(@QueryParam("rarity") String rarity, @QueryParam("element") String element,
                             @QueryParam("exclude") String exclude) {
        return service.candidates(rarity, element, exclude);
    }

    @GET
    @Path("/{id}/stats")
    @Operation(summary = "Get effective stats of a species at a level")
    @APIResponse(responseCode = "200", description = "Stats at the level")
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR: level outside 1..50")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    @APIResponse(responseCode = "403", description = "FORBIDDEN: not a service token")
    @APIResponse(responseCode = "404", description = "NOT_FOUND")
    public Object stats(@PathParam("id") int id, @QueryParam("level") int level) {
        return service.stats(id, level);
    }
}
