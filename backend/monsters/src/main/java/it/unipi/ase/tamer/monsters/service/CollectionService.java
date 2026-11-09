package it.unipi.ase.tamer.monsters.service;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import it.unipi.ase.tamer.monsters.api.dto.ArchiveRequest;
import it.unipi.ase.tamer.monsters.api.dto.ArchiveResponse;
import it.unipi.ase.tamer.monsters.api.dto.OwnedMonsterResponse;
import it.unipi.ase.tamer.monsters.api.dto.PageResponse;
import it.unipi.ase.tamer.monsters.api.dto.RewardRequest;
import it.unipi.ase.tamer.monsters.api.dto.StarterEntry;
import it.unipi.ase.tamer.monsters.api.dto.StarterGrantRequest;
import it.unipi.ase.tamer.monsters.api.dto.XpGainResponse;
import it.unipi.ase.tamer.monsters.api.dto.XpGrantRequest;
import it.unipi.ase.tamer.monsters.domain.Monster;
import it.unipi.ase.tamer.monsters.domain.OwnedMonster;
import it.unipi.ase.tamer.monsters.domain.XpGrant;
import it.unipi.ase.tamer.monsters.error.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Owns the collection, stats, levelling and idempotent battle-side effects (MO-2). */
@ApplicationScoped
public class CollectionService {

    @Inject
    EntityManager em;

    public PageResponse<OwnedMonsterResponse> collection(UUID playerId, UUID runId,
                                                         Integer page, Integer size) {
        CatalogueService.PageSpec spec = CatalogueService.pageSpec(page, size);
        PanacheQuery<OwnedMonster> query = runId == null
                ? OwnedMonster.find("playerId = ?1 and status = ?2", Sort.by("obtainedAt").and("ownedId"),
                        playerId, "active")
                : OwnedMonster.find("playerId = ?1 and runId = ?2", Sort.by("obtainedAt").and("ownedId"),
                        playerId, runId);
        long total = query.count();
        var items = query.page(Page.of(spec.page(), spec.size())).list().stream()
                .map(MonsterMapper::owned).toList();
        return PageResponse.of(items, spec.page(), spec.size(), total);
    }

    public OwnedMonsterResponse detail(UUID playerId, UUID ownedId) {
        OwnedMonster owned = ownedByPlayer(playerId, ownedId);
        if (owned == null) {
            throw ApiException.notFound("Owned monster not found.");
        }
        return MonsterMapper.owned(owned);
    }

    public List<OwnedMonsterResponse> instances(UUID playerId, String ids) {
        List<UUID> requested = parseUuids(ids);
        if (requested.isEmpty()) {
            return List.of();
        }
        return OwnedMonster.<OwnedMonster>list("playerId = ?1 and ownedId in ?2", playerId, requested)
                .stream().map(MonsterMapper::owned).toList();
    }

    public record StarterGrant(boolean created, List<StarterEntry> starters) {
    }

    public record RewardGrant(boolean created, OwnedMonsterResponse monster) {
    }

    @Transactional
    public StarterGrant starters(UUID playerId, StarterGrantRequest request) {
        // All grants for a run are atomic and a repeated request returns the original three.
        lock("starter:" + request.runId());
        List<OwnedMonster> existing = OwnedMonster.list(
                "playerId = ?1 and runId = ?2 and source = ?3", playerId, request.runId(), "starter");
        if (!existing.isEmpty()) {
            return new StarterGrant(false, entries(existing));
        }
        List<Integer> ids = request.monsterIds();
        if (ids == null || ids.size() != 3 || ids.contains(null)
                || new HashSet<>(ids).size() != 3) {
            throw invalid("Invalid starter selection.");
        }
        List<OwnedMonster> created = new ArrayList<>();
        for (int id : ids) {
            Monster monster = CatalogueService.requireMonster(id);
            if (!"common".equals(monster.rarity)) {
                throw invalid("Starters must be common monsters.");
            }
            created.add(create(playerId, request.runId(), id, 1, "starter", null));
        }
        em.flush();
        return new StarterGrant(true, entries(created));
    }

    @Transactional
    public RewardGrant reward(UUID playerId, RewardRequest request) {
        lock("reward:" + request.encounterId());
        OwnedMonster previous = OwnedMonster.find("encounterId", request.encounterId()).firstResult();
        if (previous != null) {
            if (!previous.playerId.equals(playerId)) {
                throw invalid("Encounter reward belongs to another player.");
            }
            return new RewardGrant(false, MonsterMapper.owned(previous));
        }
        if (!"encounter".equals(request.source()) && !"boss".equals(request.source())) {
            throw invalid("Reward source must be encounter or boss.");
        }
        if (request.level() < 1) {
            throw invalid("Reward level must be positive.");
        }
        CatalogueService.requireMonster(request.monsterId());
        OwnedMonster owned = create(playerId, request.runId(), request.monsterId(),
                Math.min(Leveling.MAX_LEVEL, request.level()), request.source(), request.encounterId());
        em.flush();
        return new RewardGrant(true, MonsterMapper.owned(owned));
    }

    @Transactional
    public List<XpGainResponse> grantXp(UUID playerId, XpGrantRequest request) {
        lock("xp:" + request.encounterId());
        if (request.xp() == null || request.ownedIds() == null || request.xp() < 0 || request.ownedIds().isEmpty()
                || request.ownedIds().contains(null)
                || new HashSet<>(request.ownedIds()).size() != request.ownedIds().size()) {
            throw invalid("XP and owned ids must be valid and unique.");
        }
        XpGrant previous = XpGrant.findById(request.encounterId());
        boolean repeat = previous != null;
        if (repeat && !previous.playerId.equals(playerId)) {
            throw invalid("XP grant belongs to another player.");
        }
        // Lock recipients in a stable order: different concurrent encounters must not
        // overwrite each other's level/XP updates or deadlock on overlapping teams.
        List<UUID> orderedIds = request.ownedIds().stream().sorted().toList();
        java.util.Map<UUID, OwnedMonster> byId = new java.util.HashMap<>();
        for (UUID id : orderedIds) {
            OwnedMonster owned = OwnedMonster.<OwnedMonster>find(
                    "ownedId = ?1 and playerId = ?2", id, playerId)
                    .withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
            if (owned == null || (!repeat && !"active".equals(owned.status))) {
                throw invalid("All XP recipients must be owned and active.");
            }
            byId.put(id, owned);
        }
        List<OwnedMonster> monsters = request.ownedIds().stream().map(byId::get).toList();
        if (!repeat) {
            XpGrant grant = new XpGrant();
            grant.encounterId = request.encounterId();
            grant.playerId = playerId;
            grant.xp = request.xp();
            grant.grantedAt = Instant.now();
            grant.persist();
        }
        List<XpGainResponse> result = new ArrayList<>();
        for (OwnedMonster owned : monsters) {
            int before = owned.level;
            if (!repeat) {
                Leveling.State state = Leveling.grant(owned.level, owned.xp, request.xp());
                owned.level = (short) state.level();
                owned.xp = state.xp();
            }
            result.add(new XpGainResponse(owned.ownedId, before, owned.level, owned.xp));
        }
        em.flush();
        return result;
    }

    @Transactional
    public ArchiveResponse archive(UUID playerId, ArchiveRequest request) {
        long count = OwnedMonster.update("status = ?1 where playerId = ?2 and runId = ?3 and status = ?4",
                "archived", playerId, request.runId(), "active");
        return new ArchiveResponse(count);
    }

    private OwnedMonster create(UUID playerId, UUID runId, int monsterId, int level,
                                String source, UUID encounterId) {
        OwnedMonster owned = new OwnedMonster();
        owned.ownedId = UUID.randomUUID();
        owned.playerId = playerId;
        owned.runId = runId;
        owned.monsterId = monsterId;
        owned.level = (short) level;
        owned.xp = 0;
        owned.source = source;
        owned.encounterId = encounterId;
        owned.status = "active";
        owned.obtainedAt = Instant.now();
        owned.persist();
        return owned;
    }

    private void lock(String key) {
        // PostgreSQL transaction-level lock: the same logical operation cannot be double-granted
        // by concurrent requests, including those processed by different service instances.
        em.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(hashtext(:key))")
                .setParameter("key", key).getSingleResult();
    }

    private static List<StarterEntry> entries(List<OwnedMonster> items) {
        return items.stream().map(m -> new StarterEntry(m.ownedId, m.monsterId, m.level)).toList();
    }

    private static OwnedMonster ownedByPlayer(UUID playerId, UUID ownedId) {
        return OwnedMonster.find("ownedId = ?1 and playerId = ?2", ownedId, playerId).firstResult();
    }

    private static List<UUID> parseUuids(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String[] parts = value.split(",", -1);
        if (parts.length > 50) {
            throw invalid("Maximum 50 owned monster ids per request.");
        }
        List<UUID> ids = new ArrayList<>();
        try {
            for (String part : parts) {
                ids.add(UUID.fromString(part));
            }
        } catch (IllegalArgumentException e) {
            throw invalid("Invalid owned monster id.");
        }
        return ids;
    }

    private static ApiException invalid(String message) {
        return new ApiException(400, "INVALID_COLLECTION_OPERATION", message);
    }
}
