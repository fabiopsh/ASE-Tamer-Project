package it.unipi.ase.tamers.monsters.collection;

import it.unipi.ase.tamers.monsters.catalog.Filters;
import it.unipi.ase.tamers.monsters.collection.CollectionDtos.OwnedMonsterDetail;
import it.unipi.ase.tamers.monsters.collection.CollectionDtos.OwnedMonsterView;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;

/** The authenticated player's own collection (US-09). Read-only: there is no way to add monsters from here. */
@Path("/collection")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("player")
public class CollectionResource {
    @Inject
    JsonWebToken jwt;
    @Inject
    CollectionService service;
    @Inject
    CollectionViews views;

    @GET
    public List<OwnedMonsterView> list(@QueryParam("element") String element,
                                       @QueryParam("rarity") String rarity,
                                       @QueryParam("sort") String sort) {
        return service.ofPlayer(playerId(), Filters.element(element), Filters.rarity(rarity), CollectionSort.parse(sort))
                .stream().map(views::view).toList();
    }

    @GET
    @Path("/{owned_id}")
    public OwnedMonsterDetail detail(@PathParam("owned_id") String ownedId) {
        UUID id = Filters.uuid(ownedId);
        return views.detail(service.getOwned(playerId(), id));
    }

    private UUID playerId() {
        return UUID.fromString(jwt.getSubject());
    }
}
