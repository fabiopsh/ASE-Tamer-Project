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
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    public Object catalogue(@QueryParam("element") String element, @QueryParam("rarity") String rarity,
                            @QueryParam("ids") String ids, @QueryParam("page") Integer page,
                            @QueryParam("size") Integer size) {
        return service.catalogue(element, rarity, ids, page, size);
    }

    @GET
    @Path("/monsters/{id}")
    @Operation(summary = "View a monster species and its ability (US-11)")
    @APIResponse(responseCode = "200", description = "Monster species with stats and ability")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    @APIResponse(responseCode = "404", description = "NOT_FOUND: unknown species")
    public Object monster(@PathParam("id") int id) {
        return service.detail(id);
    }

    @GET
    @Path("/abilities")
    @Operation(summary = "List all abilities")
    @APIResponse(responseCode = "200", description = "Abilities")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    public Object abilities() {
        return service.abilities();
    }

    @GET
    @Path("/abilities/{id}")
    @Operation(summary = "View an ability")
    @APIResponse(responseCode = "200", description = "Ability")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    @APIResponse(responseCode = "404", description = "NOT_FOUND: unknown ability")
    public Object ability(@PathParam("id") int id) {
        return service.ability(id);
    }

    @GET
    @Path("/elements")
    @Operation(summary = "Get the element effectiveness cycle")
    @APIResponse(responseCode = "200", description = "Elements and effectiveness")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    public Object elements() {
        return service.elements();
    }
}
