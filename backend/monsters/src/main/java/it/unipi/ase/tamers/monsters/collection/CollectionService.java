package it.unipi.ase.tamers.monsters.collection;

import it.unipi.ase.tamers.monsters.catalog.Catalog;
import it.unipi.ase.tamers.monsters.catalog.Element;
import it.unipi.ase.tamers.monsters.catalog.Filters;
import it.unipi.ase.tamers.monsters.catalog.Growth;
import it.unipi.ase.tamers.monsters.catalog.Monster;
import it.unipi.ase.tamers.monsters.catalog.Rarity;
import it.unipi.ase.tamers.monsters.collection.CollectionDtos.CollectionStats;
import it.unipi.ase.tamers.monsters.collection.CollectionDtos.XpAward;
import it.unipi.ase.tamers.monsters.collection.CollectionDtos.XpResult;
import it.unipi.ase.tamers.monsters.common.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Database operations on the collection. Monsters are only ever added through the internal API (US-12). */
@ApplicationScoped
public class CollectionService {
    @Inject
    Catalog catalog;

    public List<OwnedMonster> ofPlayer(UUID playerId) {
        return OwnedMonster.<OwnedMonster>list("playerId", playerId);
    }

    /** The player's monsters, optionally filtered by species element/rarity, sorted. */
    public List<OwnedMonster> ofPlayer(UUID playerId, Element element, Rarity rarity, CollectionSort sort) {
        Map<Integer, Monster> species = catalog.get().monsterById();
        return ofPlayer(playerId).stream()
                .filter(o -> {
                    Monster m = species.get(o.monsterId);
                    return m != null
                            && (element == null || element.value().equals(m.element))
                            && (rarity == null || rarity.value().equals(m.rarity));
                })
                .sorted(sort.comparator())
                .toList();
    }

    /** Owned monster by id, enforcing ownership: missing = 404, someone else's = 403. */
    public OwnedMonster getOwned(UUID playerId, UUID ownedId) {
        OwnedMonster owned = OwnedMonster.findById(ownedId);
        if (owned == null) {
            throw ApiException.notFound("owned monster " + ownedId + " not found");
        }
        if (!owned.playerId.equals(playerId)) {
            throw ApiException.forbidden("this monster belongs to another player");
        }
        return owned;
    }

    public List<OwnedMonster> ownedAmong(UUID playerId, Set<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<OwnedMonster> found = OwnedMonster.<OwnedMonster>list("playerId = ?1 and ownedId in ?2", playerId, ids);
        return CollectionRules.ownedInRequestedOrder(playerId, ids, found);
    }

    public CollectionStats stats(UUID playerId) {
        List<OwnedMonster> all = ofPlayer(playerId);
        return new CollectionStats(all.size(), CollectionRules.speciesOwned(all));
    }

    @Transactional
    public OwnedMonster add(UUID playerId, int monsterId, String rawSource, UUID encounterId) {
        return create(playerId, monsterId, source(rawSource), encounterId, Instant.now());
    }

    @Transactional
    public List<OwnedMonster> addBatch(UUID playerId, List<Integer> monsterIds, String rawSource) {
        Source source = source(rawSource);
        Instant now = Instant.now();
        // validate everything first so that nothing is written when one id is wrong
        for (int id : monsterIds) {
            requireSpecies(id);
        }
        List<OwnedMonster> created = new ArrayList<>();
        for (int id : monsterIds) {
            created.add(create(playerId, id, source, null, now));
        }
        return created;
    }

    /** All-or-nothing: every owned_id must belong to the player, otherwise nothing is changed. */
    @Transactional
    public List<XpResult> awardXp(UUID playerId, List<XpAward> awards) {
        if (awards.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = awards.stream().map(XpAward::ownedId).collect(Collectors.toSet());
        Map<UUID, OwnedMonster> byId = new HashMap<>();
        OwnedMonster.<OwnedMonster>find("ownedId in ?1", ids).withLock(LockModeType.PESSIMISTIC_WRITE).list()
                .forEach(o -> byId.put(o.ownedId, o));
        for (UUID id : ids) {
            OwnedMonster o = byId.get(id);
            if (o == null || !o.playerId.equals(playerId)) {
                throw ApiException.badRequest("owned monster " + id + " does not belong to the player");
            }
        }
        List<XpResult> results = new ArrayList<>();
        for (XpAward award : awards) {
            results.add(applyAward(byId.get(award.ownedId()), award.xp()));
        }
        return results;
    }

    private static XpResult applyAward(OwnedMonster owned, int gained) {
        int oldLevel = owned.level;
        Growth.Progress progress = Growth.applyXp(owned.level, owned.xp, gained);
        owned.level = progress.level();
        owned.xp = progress.xp();
        return new XpResult(owned.ownedId, oldLevel, owned.level, owned.xp, Growth.xpToNextForDisplay(owned.level));
    }

    private OwnedMonster create(UUID playerId, int monsterId, Source source, UUID encounterId, Instant now) {
        requireSpecies(monsterId);
        OwnedMonster o = new OwnedMonster();
        o.ownedId = UUID.randomUUID();
        o.playerId = playerId;
        o.monsterId = monsterId;
        o.level = 1;
        o.xp = 0;
        o.source = source.value();
        o.encounterId = encounterId;
        o.obtainedAt = now;
        o.persist();
        return o;
    }

    private void requireSpecies(int monsterId) {
        if (!catalog.get().monsterById().containsKey(monsterId)) {
            throw ApiException.badRequest("unknown monster_id " + monsterId);
        }
    }

    private static Source source(String raw) {
        Source source = Filters.parseEnum(Source.class, "source", raw);
        if (source == null) {
            throw ApiException.badRequest("source is required");
        }
        return source;
    }
}
