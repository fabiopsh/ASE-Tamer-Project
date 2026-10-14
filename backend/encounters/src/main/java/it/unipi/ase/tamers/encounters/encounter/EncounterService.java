package it.unipi.ase.tamers.encounters.encounter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.narayana.jta.QuarkusTransaction;
import it.unipi.ase.tamers.encounters.client.CatalogCache;
import it.unipi.ase.tamers.encounters.client.CatalogCache.Catalog;
import it.unipi.ase.tamers.encounters.client.ClientDtos.*;
import it.unipi.ase.tamers.encounters.client.HistoryClient;
import it.unipi.ase.tamers.encounters.client.MonstersClient;
import it.unipi.ase.tamers.encounters.client.PlayersClient;
import it.unipi.ase.tamers.encounters.client.TeamsClient;
import it.unipi.ase.tamers.encounters.common.ApiException;
import it.unipi.ase.tamers.encounters.encounter.EncounterDtos.*;
import it.unipi.ase.tamers.encounters.engine.BattleEngine;
import it.unipi.ase.tamers.encounters.engine.BattleResult;
import it.unipi.ase.tamers.encounters.engine.BoardGenerator;
import it.unipi.ase.tamers.encounters.engine.BoardGenerator.Spawn;
import it.unipi.ase.tamers.encounters.engine.Combatant;
import it.unipi.ase.tamers.encounters.engine.Rewards;
import it.unipi.ase.tamers.encounters.engine.Stats;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

@ApplicationScoped
public class EncounterService {
    private static final Logger LOG = Logger.getLogger(EncounterService.class);
    private static final SecureRandom SEEDS = new SecureRandom();
    private static final int RECENT_WILD = 6;
    /** Serializes board generation and starts of the same player (one instance of the service). */
    private static final Object[] LOCKS = new Object[64];

    static {
        for (int i = 0; i < LOCKS.length; i++) {
            LOCKS[i] = new Object();
        }
    }

    @Inject
    @RestClient
    MonstersClient monsters;
    @Inject
    @RestClient
    TeamsClient teams;
    @Inject
    @RestClient
    PlayersClient players;
    @Inject
    @RestClient
    HistoryClient history;
    @Inject
    CatalogCache catalogCache;
    @Inject
    ObjectMapper mapper;

    private static Object lock(UUID playerId) {
        return LOCKS[Math.floorMod(playerId.hashCode(), LOCKS.length)];
    }

    // ------------------------------------------------------------------ board and offers

    public BoardView board(UUID playerId) {
        PlayerProfile profile = profile(playerId);
        Catalog catalog = catalogCache.get();
        List<EncounterOffer> offers;
        synchronized (lock(playerId)) {
            offers = ensureBoard(playerId, profile.stage(), catalog);
        }
        return new BoardView(profile.stage(), completed(profile),
                offers.stream().map(o -> view(o, catalog)).toList());
    }

    public OfferView offer(UUID playerId, UUID encounterId) {
        EncounterOffer offer = loadOffer(playerId, encounterId);
        return view(offer, catalogCache.get());
    }

    /** Makes sure the player has 3 open wild offers (and the guardian while stage <= 10). */
    private List<EncounterOffer> ensureBoard(UUID playerId, int playerStage, Catalog catalog) {
        int stage = BoardGenerator.boardStage(playerStage);
        boolean gameCompleted = playerStage > BoardGenerator.MAX_STAGE;
        return tx(() -> {
            List<EncounterOffer> keep = new ArrayList<>();
            for (EncounterOffer o : EncounterOffer.openFor(playerId)) {
                if (o.stage != stage || (gameCompleted && o.isGuardian())) {
                    o.status = EncounterOffer.COMPLETED; // left over from a past stage
                } else {
                    keep.add(o);
                }
            }
            long wilds = keep.stream().filter(o -> !o.isGuardian()).count();
            if (wilds < BoardGenerator.WILD_PER_BOARD) {
                Set<Integer> exclude = new HashSet<>();
                keep.forEach(o -> exclude.add(o.monsterId));
                exclude.add(BoardGenerator.guardian(catalog.entries(), stage).monsterId());
                Set<Integer> avoid = new HashSet<>();
                EncounterOffer.recentWild(playerId, stage, RECENT_WILD).forEach(o -> avoid.add(o.monsterId));
                for (long i = wilds; i < BoardGenerator.WILD_PER_BOARD; i++) {
                    Spawn spawn = BoardGenerator.wild(catalog.entries(), stage, exclude, avoid,
                            ThreadLocalRandom.current());
                    exclude.add(spawn.monsterId());
                    keep.add(newOffer(playerId, EncounterOffer.WILD, stage, spawn.monsterId(), spawn.level()));
                }
            }
            if (!gameCompleted && keep.stream().noneMatch(EncounterOffer::isGuardian)) {
                var g = BoardGenerator.guardian(catalog.entries(), stage);
                keep.add(newOffer(playerId, EncounterOffer.GUARDIAN, stage, g.monsterId(),
                        BoardGenerator.guardianLevel(stage)));
            }
            keep.sort(Comparator.comparing(EncounterOffer::isGuardian).thenComparing(o -> o.createdAt));
            return keep;
        });
    }

    private static EncounterOffer newOffer(UUID playerId, String kind, int stage, int monsterId, int level) {
        EncounterOffer o = new EncounterOffer();
        o.encounterId = UUID.randomUUID();
        o.playerId = playerId;
        o.kind = kind;
        o.stage = stage;
        o.status = EncounterOffer.OPEN;
        o.monsterId = monsterId;
        o.level = level;
        o.createdAt = Instant.now();
        o.persist();
        return o;
    }

    private OfferView view(EncounterOffer o, Catalog catalog) {
        MonsterDetail m = catalog.monster(o.monsterId);
        boolean guardian = o.isGuardian();
        OfferEnemy enemy = new OfferEnemy(m.monsterId(), m.name(), m.element(), m.rarity(), m.imageUrl(), o.level,
                m.statsAt(o.level), m.ability());
        OfferRewards rewards = new OfferRewards(Rewards.victoryXp(o.level, guardian),
                Rewards.captureChance(m.rarity(), guardian), guardian);
        return new OfferView(o.encounterId, o.kind, o.stage, o.status,
                BoardGenerator.difficulty(guardian, o.stage, o.level), enemy, rewards, o.createdAt);
    }

    private EncounterOffer loadOffer(UUID playerId, UUID encounterId) {
        return tx(() -> {
            EncounterOffer o = EncounterOffer.findById(encounterId);
            if (o == null) {
                throw ApiException.notFound("encounter not found");
            }
            if (!o.playerId.equals(playerId)) {
                throw ApiException.forbidden("this encounter belongs to another player");
            }
            return o;
        });
    }

    // ------------------------------------------------------------------ start

    public EncounterResult start(UUID playerId, UUID encounterId, UUID teamId) {
        synchronized (lock(playerId)) {
            return doStart(playerId, encounterId, teamId);
        }
    }

    private EncounterResult doStart(UUID playerId, UUID encounterId, UUID teamId) {
        EncounterOffer offer = loadOffer(playerId, encounterId);
        if (!offer.isOpen()) {
            throw ApiException.conflict("encounter is not open");
        }
        PlayerProfile profile = profile(playerId);
        if (offer.stage != BoardGenerator.boardStage(profile.stage())
                || (completed(profile) && offer.isGuardian())) {
            throw ApiException.conflict("encounter belongs to a past stage");
        }
        Catalog catalog = catalogCache.get();
        List<OwnedMonster> members = loadTeam(playerId, teamId);

        boolean guardian = offer.isGuardian();
        MonsterDetail species = catalog.monster(offer.monsterId);
        Combatant enemy = CombatantFactory.enemy(species, offer.level, guardian);
        List<Combatant> fighters = members.stream().map(CombatantFactory::fromOwned).toList();

        long seed = SEEDS.nextLong();
        BattleResult battle = BattleEngine.simulate(fighters, enemy, seed, catalog.chart());
        boolean victory = battle.victory();
        UUID runId = UUID.randomUUID();

        // 1. XP
        int xp = Rewards.xp(victory, offer.level, guardian);
        List<XpReward> xpRewards = awardXp(playerId, members, xp);

        // 2. capture
        OwnedMonster captured = null;
        if (victory) {
            double chance = Rewards.captureChance(species.rarity(), guardian);
            boolean caught = Rewards.rollCapture(chance, new SplittableRandom(seed ^ 0x9E3779B97F4A7C15L));
            if (caught) {
                captured = downstream("monsters service", () -> monsters.capture(
                        new CaptureRequest(playerId, species.monsterId(), "encounter", runId)));
            }
        }

        // 3. stage advance
        boolean advanced = false;
        int newStage = profile.stage();
        if (victory && guardian) {
            PlayerProfile after = downstream("players service", () -> players.advanceStage(playerId,
                    new AdvanceStageRequest(offer.stage)));
            newStage = after.stage();
            advanced = newStage > profile.stage();
        }

        // 4. result, run and offer
        Instant now = Instant.now();
        EncounterResult result = new EncounterResult(runId, offer.encounterId, offer.kind, offer.stage,
                battle.outcome(), battle.rounds(), seed,
                buildTeam(members, fighters), buildEnemy(species, offer.level, enemy), battle.log(),
                new RunRewards(xpRewards, captured, advanced, newStage), now);
        String json = toJson(result);
        persistRun(offer.encounterId, playerId, result, json, victory);
        try {
            ensureBoard(playerId, newStage, catalog);
        } catch (RuntimeException e) {
            LOG.warnf("could not refresh the board of %s: %s", playerId, e.toString());
        }

        // 5. history
        ObjectNode payload = mapper.valueToTree(result);
        payload.put("player_id", playerId.toString());
        downstream("history service", () -> history.record(payload));
        return result;
    }

    private List<OwnedMonster> loadTeam(UUID playerId, UUID teamId) {
        TeamRef team;
        try {
            team = teams.team(teamId, playerId);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                throw ApiException.badRequest("team not found");
            }
            LOG.errorf("teams service error: %s", e.toString());
            throw ApiException.upstream("teams service unavailable");
        } catch (RuntimeException e) {
            LOG.errorf("teams service error: %s", e.toString());
            throw ApiException.upstream("teams service unavailable");
        }
        if (team.ownedIds() == null || team.ownedIds().isEmpty()) {
            throw ApiException.badRequest("the team has no monsters");
        }
        String ids = team.ownedIds().stream().map(UUID::toString).collect(Collectors.joining(","));
        List<OwnedMonster> owned = downstream("monsters service", () -> monsters.owned(playerId, ids));
        Map<UUID, OwnedMonster> byId = new LinkedHashMap<>();
        owned.forEach(m -> byId.put(m.ownedId(), m));
        List<OwnedMonster> ordered = new ArrayList<>();
        for (UUID id : team.ownedIds()) {
            OwnedMonster m = byId.get(id);
            if (m == null) {
                throw ApiException.badRequest("monster " + id + " is not in your collection");
            }
            ordered.add(m);
        }
        return ordered;
    }

    private List<XpReward> awardXp(UUID playerId, List<OwnedMonster> members, int xp) {
        List<XpAward> awards = members.stream().map(m -> new XpAward(m.ownedId(), xp)).toList();
        List<XpResult> results = downstream("monsters service",
                () -> monsters.awardXp(new XpRequest(playerId, awards)));
        Map<UUID, XpResult> byId = new LinkedHashMap<>();
        results.forEach(r -> byId.put(r.ownedId(), r));
        List<XpReward> out = new ArrayList<>();
        for (OwnedMonster m : members) {
            XpResult r = byId.get(m.ownedId());
            out.add(new XpReward(m.ownedId(), xp, r == null ? m.level() : r.oldLevel(),
                    r == null ? m.level() : r.newLevel()));
        }
        return out;
    }

    private static List<ResultMember> buildTeam(List<OwnedMonster> members, List<Combatant> fighters) {
        List<ResultMember> out = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            OwnedMonster m = members.get(i);
            out.add(new ResultMember(m.ownedId(), m.monsterId(), m.name(), m.element(), m.imageUrl(), m.level(),
                    fighters.get(i).maxHp(), m.stats()));
        }
        return out;
    }

    private static ResultEnemy buildEnemy(MonsterDetail m, int level, Combatant enemy) {
        Stats stats = m.statsAt(level);
        return new ResultEnemy(m.monsterId(), m.name(), m.element(), m.imageUrl(), level, enemy.maxHp(), stats);
    }

    private void persistRun(UUID encounterId, UUID playerId, EncounterResult result, String json, boolean victory) {
        tx(() -> {
            EncounterRun run = new EncounterRun();
            run.runId = result.runId();
            run.playerId = playerId;
            run.encounterId = encounterId;
            run.outcome = result.outcome().value();
            run.seed = result.seed();
            run.result = json;
            run.occurredAt = result.occurredAt();
            run.persist();
            if (victory) {
                EncounterOffer o = EncounterOffer.findById(encounterId);
                o.status = EncounterOffer.COMPLETED;
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ runs

    public EncounterResult run(UUID playerId, UUID runId) {
        EncounterRun run = tx(() -> {
            EncounterRun r = EncounterRun.findById(runId);
            if (r == null) {
                throw ApiException.notFound("run not found");
            }
            if (!r.playerId.equals(playerId)) {
                throw ApiException.forbidden("this run belongs to another player");
            }
            return r;
        });
        try {
            return mapper.readValue(run.result, EncounterResult.class);
        } catch (JsonProcessingException e) {
            LOG.errorf("stored run %s is not readable: %s", runId, e.toString());
            throw new IllegalStateException("stored run is corrupted", e);
        }
    }

    // ------------------------------------------------------------------ helpers

    private PlayerProfile profile(UUID playerId) {
        try {
            return players.profile(playerId);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                throw ApiException.notFound("player profile not found");
            }
            LOG.errorf("players service error: %s", e.toString());
            throw ApiException.upstream("players service unavailable");
        } catch (RuntimeException e) {
            LOG.errorf("players service error: %s", e.toString());
            throw ApiException.upstream("players service unavailable");
        }
    }

    private static boolean completed(PlayerProfile p) {
        return p.gameCompleted() || p.stage() > BoardGenerator.MAX_STAGE;
    }

    private String toJson(EncounterResult result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize the result", e);
        }
    }

    private static <T> T downstream(String what, Supplier<T> call) {
        try {
            return call.get();
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.errorf("%s error: %s", what, e.toString());
            throw ApiException.upstream(what + " unavailable");
        }
    }

    private static <T> T tx(Callable<T> body) {
        return QuarkusTransaction.requiringNew().call(body);
    }
}
