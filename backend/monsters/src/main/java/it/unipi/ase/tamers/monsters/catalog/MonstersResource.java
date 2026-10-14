package it.unipi.ase.tamers.monsters.catalog;

import it.unipi.ase.tamers.monsters.catalog.CatalogDtos.AbilityView;
import it.unipi.ase.tamers.monsters.catalog.CatalogDtos.MonsterDetail;
import it.unipi.ase.tamers.monsters.catalog.CatalogDtos.MonsterSummary;
import it.unipi.ase.tamers.monsters.catalog.Paging.Page;
import it.unipi.ase.tamers.monsters.catalog.SeedData.ElementCycle;
import it.unipi.ase.tamers.monsters.common.ApiException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** Catalog browsing (US-10, US-11). Authenticated players only. */
@Path("/monsters")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("player")
public class MonstersResource {
    @Inject
    Catalog catalog;
    @Inject
    CatalogViews views;
    @Inject
    Elements elements;

    @GET
    public Page<MonsterSummary> list(@QueryParam("element") String element,
                                     @QueryParam("rarity") String rarity,
                                     @QueryParam("ids") String ids,
                                     @QueryParam("q") String q,
                                     @QueryParam("page") @DefaultValue("0") String page,
                                     @QueryParam("size") @DefaultValue("20") String size) {
        MonsterFilter filter = MonsterFilter.parse(element, rarity, ids, q);
        Paging paging = Paging.parse(page, size);
        List<MonsterSummary> matching = catalog.get().monsters().stream()
                .filter(filter::matches).map(views::summary).toList();
        return paging.slice(matching);
    }

    @GET
    @Path("/abilities")
    public List<AbilityView> abilities() {
        return catalog.get().abilities().stream().map(views::ability).toList();
    }

    @GET
    @Path("/elements")
    public ElementCycle elements() {
        return elements.cycle();
    }

    @GET
    @Path("/{monster_id}")
    public MonsterDetail detail(@PathParam("monster_id") String rawId) {
        Monster m = catalog.get().monsterById().get(Filters.intId(rawId));
        if (m == null) {
            throw ApiException.notFound("monster " + rawId + " not found");
        }
        return views.detail(m);
    }
}
