package it.unipi.ase.tamers.monsters.collection;

import it.unipi.ase.tamers.monsters.catalog.Catalog;
import it.unipi.ase.tamers.monsters.catalog.CatalogDtos.MonsterDetail;
import it.unipi.ase.tamers.monsters.catalog.CatalogViews;
import it.unipi.ase.tamers.monsters.catalog.Filters;
import it.unipi.ase.tamers.monsters.collection.CollectionDtos.AddRequest;
import it.unipi.ase.tamers.monsters.collection.CollectionDtos.BatchRequest;
import it.unipi.ase.tamers.monsters.collection.CollectionDtos.CollectionStats;
import it.unipi.ase.tamers.monsters.collection.CollectionDtos.OwnedMonsterView;
import it.unipi.ase.tamers.monsters.collection.CollectionDtos.XpRequest;
import it.unipi.ase.tamers.monsters.collection.CollectionDtos.XpResult;
import jakarta.annotation.security.PermitAll;
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

/**
 * Service-to-service API. Never exposed by the gateway; the {@code X-Internal-Token} header is checked by
 * {@link it.unipi.ase.tamers.monsters.common.InternalTokenFilter}. This is the only place where monsters are added.
 */
@Path("/internal")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InternalResource {
    @Inject
    Catalog catalog;
    @Inject
    CatalogViews catalogViews;
    @Inject
    CollectionService service;
    @Inject
    CollectionViews views;

    @GET
    @Path("/monsters")
    public List<MonsterDetail> monsters() {
        return catalog.get().monsters().stream().map(catalogViews::detail).toList();
    }

    @GET
    @Path("/collection/{player_id}")
    public List<OwnedMonsterView> collection(@PathParam("player_id") String playerId) {
        return service.ofPlayer(Filters.uuid(playerId), null, null, CollectionSort.LEVEL)
                .stream().map(views::view).toList();
    }

    @GET
    @Path("/collection/{player_id}/owned")
    public List<OwnedMonsterView> owned(@PathParam("player_id") String playerId, @QueryParam("ids") String ids) {
        return service.ownedAmong(Filters.uuid(playerId), Filters.uuidIds(ids)).stream().map(views::view).toList();
    }

    @GET
    @Path("/collection/{player_id}/stats")
    public CollectionStats stats(@PathParam("player_id") String playerId) {
        return service.stats(Filters.uuid(playerId));
    }

    @POST
    @Path("/collection")
    public Response add(@Valid @NotNull AddRequest request) {
        OwnedMonster created = service.add(request.playerId(), request.monsterId(), request.source(),
                request.encounterId());
        return Response.status(201).entity(views.view(created)).build();
    }

    @POST
    @Path("/collection/batch")
    public Response addBatch(@Valid @NotNull BatchRequest request) {
        List<OwnedMonsterView> created = service.addBatch(request.playerId(), request.monsterIds(), request.source())
                .stream().map(views::view).toList();
        return Response.status(201).entity(created).build();
    }

    @POST
    @Path("/collection/xp")
    public List<XpResult> xp(@Valid @NotNull XpRequest request) {
        return service.awardXp(request.playerId(), request.awards());
    }
}
