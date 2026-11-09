package it.unipi.ase.tamer.players.service;

import it.unipi.ase.tamer.players.api.dto.ChosenStartersResponse;
import it.unipi.ase.tamer.players.api.dto.ChooseStartersRequest;
import it.unipi.ase.tamer.players.api.dto.NewRunResponse;
import it.unipi.ase.tamer.players.api.dto.PlayerResponse;
import it.unipi.ase.tamer.players.api.dto.StarterPoolResponse;
import it.unipi.ase.tamer.players.client.EncountersClient;
import it.unipi.ase.tamer.players.client.MonstersClient;
import it.unipi.ase.tamer.players.client.OpenRunRequest;
import it.unipi.ase.tamer.players.client.StarterGrantRequest;
import it.unipi.ase.tamer.players.domain.Player;
import it.unipi.ase.tamer.players.error.ApiException;
import it.unipi.ase.tamer.players.error.ErrorCodes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/** Profile lifecycle and the ordered, idempotent starter selection workflow (PL-1). */
@ApplicationScoped
public class PlayersService {

    private static final Logger LOG = Logger.getLogger(PlayersService.class);

    @Inject
    @RestClient
    MonstersClient monsters;

    @Inject
    @RestClient
    EncountersClient encounters;

    @Inject
    PoolGenerator pools;

    @Inject
    EntityManager em;

    public PlayerResponse profile(UUID playerId) {
        return PlayerResponse.from(requirePlayer(playerId));
    }

    public StarterPoolResponse starterPool(UUID playerId) {
        Player player = requirePlayer(playerId);
        return new StarterPoolResponse(player.currentRunId, Arrays.asList(player.starterPool),
                player.startersChosen);
    }

    @Transactional
    public PlayerResponse create(UUID playerId) {
        lockPlayer(playerId);
        Player existing = Player.findById(playerId);
        if (existing != null) {
            return PlayerResponse.from(existing);
        }
        List<Integer> ids = generatePool();
        Player player = new Player();
        player.playerId = playerId;
        player.displayName = "Tamer-" + playerId.toString().replace("-", "").substring(0, 6);
        player.currentRunId = UUID.randomUUID();
        player.starterPool = ids.toArray(Integer[]::new);
        player.startersChosen = false;
        player.createdAt = Instant.now();
        player.persistAndFlush();
        LOG.infof("Player profile created, player_id=%s", playerId);
        return PlayerResponse.from(player);
    }

    @Transactional
    public ChosenStartersResponse choose(UUID playerId, ChooseStartersRequest request) {
        lockPlayer(playerId);
        Player player = requirePlayer(playerId);
        if (player.startersChosen) {
            throw new ApiException(409, "STARTERS_ALREADY_CHOSEN", "Starters already chosen for this run.");
        }
        List<Integer> ids = request == null ? null : request.monsterIds();
        if (ids == null || ids.size() != 3 || ids.contains(null)
                || new HashSet<>(ids).size() != 3
                || !Arrays.asList(player.starterPool).containsAll(ids)) {
            throw new ApiException(400, "INVALID_STARTERS", "Choose three different monsters from your starter pool.");
        }
        List<it.unipi.ase.tamer.players.api.dto.StarterEntry> starters;
        try {
            starters = monsters.createStarters(playerId,
                    new StarterGrantRequest(player.currentRunId, ids));
            // A retry after a partial failure must use the same original selection.
            if (starters.size() != 3 || !new HashSet<>(starters.stream()
                    .map(it.unipi.ase.tamer.players.api.dto.StarterEntry::monsterId).toList())
                    .equals(new HashSet<>(ids))) {
                throw new ApiException(409, "STARTERS_ALREADY_CHOSEN",
                        "The run already has a different starter selection; retry the original choice.");
            }
            encounters.openRun(new OpenRunRequest(player.currentRunId, playerId));
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApiException.dependencyUnavailable(e);
        }
        player.startersChosen = true;
        try {
            em.flush();
        } catch (OptimisticLockException e) {
            throw new ApiException(409, "STARTERS_ALREADY_CHOSEN", "Starters already chosen for this run.");
        }
        return new ChosenStartersResponse(player.currentRunId, starters);
    }

    @Transactional
    public NewRunResponse newRun(UUID playerId, UUID endedRunId) {
        lockPlayer(playerId);
        Player player = requirePlayer(playerId);
        if (!player.currentRunId.equals(endedRunId)) {
            return new NewRunResponse(player.currentRunId);
        }
        List<Integer> ids = generatePool();
        player.currentRunId = UUID.randomUUID();
        player.starterPool = ids.toArray(Integer[]::new);
        player.startersChosen = false;
        em.flush();
        return new NewRunResponse(player.currentRunId);
    }

    private void lockPlayer(UUID playerId) {
        // Serialise the same player's registration, starter choice and run reset across replicas.
        em.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(hashtext(:key))")
                .setParameter("key", "player:" + playerId).getSingleResult();
    }

    private List<Integer> generatePool() {
        try {
            return pools.generate(monsters.candidates("common"));
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApiException.dependencyUnavailable(e);
        }
    }

    private static Player requirePlayer(UUID playerId) {
        Player player = Player.findById(playerId);
        if (player == null) {
            throw ApiException.notFound("Player profile not found.");
        }
        return player;
    }
}
