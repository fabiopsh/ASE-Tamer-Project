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
    public Object catalogue(@QueryParam("element") String element, @QueryParam("rarity") String rarity,
                            @QueryParam("ids") String ids, @QueryParam("page") Integer page,
                            @QueryParam("size") Integer size) {
        return service.catalogue(element, rarity, ids, page, size);
    }

    @GET
    @Path("/monsters/{id}")
    @Operation(summary = "View a monster species and its ability (US-11)")
    public Object monster(@PathParam("id") int id) {
        return service.detail(id);
    }

    @GET
    @Path("/abilities")
    @Operation(summary = "List all abilities")
    public Object abilities() {
        return service.abilities();
    }

    @GET
    @Path("/abilities/{id}")
    @Operation(summary = "View an ability")
    public Object ability(@PathParam("id") int id) {
        return service.ability(id);
    }

    @GET
    @Path("/elements")
    @Operation(summary = "Get the element effectiveness cycle")
    public Object elements() {
        return service.elements();
    }
}
