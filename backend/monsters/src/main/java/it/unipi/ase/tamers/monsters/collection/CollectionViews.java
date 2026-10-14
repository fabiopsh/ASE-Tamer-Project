package it.unipi.ase.tamers.monsters.collection;

import it.unipi.ase.tamers.monsters.catalog.Catalog;
import it.unipi.ase.tamers.monsters.catalog.CatalogViews;
import it.unipi.ase.tamers.monsters.catalog.Elements;
import it.unipi.ase.tamers.monsters.catalog.Growth;
import it.unipi.ase.tamers.monsters.catalog.Monster;
import it.unipi.ase.tamers.monsters.collection.CollectionDtos.OwnedMonsterDetail;
import it.unipi.ase.tamers.monsters.collection.CollectionDtos.OwnedMonsterView;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Maps owned monsters to their JSON views (effective stats are always computed here, never by the client). */
@ApplicationScoped
public class CollectionViews {
    @Inject
    Catalog catalog;
    @Inject
    CatalogViews catalogViews;
    @Inject
    Elements elements;

    private Monster species(OwnedMonster o) {
        Monster m = catalog.get().monsterById().get(o.monsterId);
        if (m == null) {
            throw new IllegalStateException("owned monster " + o.ownedId + " references unknown species " + o.monsterId);
        }
        return m;
    }

    public OwnedMonsterView view(OwnedMonster o) {
        Monster m = species(o);
        return new OwnedMonsterView(o.ownedId, m.monsterId, m.name, m.element, m.rarity, m.imageUrl,
                o.level, o.xp, Growth.xpToNextForDisplay(o.level), CatalogViews.stats(m, o.level),
                catalogViews.abilityOf(m), o.source, o.encounterId, o.obtainedAt);
    }

    public OwnedMonsterDetail detail(OwnedMonster o) {
        Monster m = species(o);
        return new OwnedMonsterDetail(o.ownedId, m.monsterId, m.name, m.element, m.rarity, m.imageUrl,
                o.level, o.xp, Growth.xpToNextForDisplay(o.level), CatalogViews.stats(m, o.level),
                catalogViews.abilityOf(m), o.source, o.encounterId, o.obtainedAt,
                m.description, CatalogViews.baseStats(m), CatalogViews.growth(m),
                elements.strongAgainst(m.element), elements.weakAgainst(m.element));
    }
}
