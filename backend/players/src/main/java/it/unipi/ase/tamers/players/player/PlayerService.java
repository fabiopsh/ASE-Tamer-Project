package it.unipi.ase.tamers.players.player;

import com.fasterxml.jackson.databind.JsonNode;
import it.unipi.ase.tamers.players.common.ApiException;
import it.unipi.ase.tamers.players.player.PlayerDtos.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PlayerService {
    private static final Logger LOG = Logger.getLogger(PlayerService.class);

    @Inject
    @RestClient
    MonstersClient monsters;
    @Inject
    @RestClient
    HistoryClient history;

    /** Profile with aggregated progress; progress fields are null when an upstream service fails. */
    public PlayerProfile me(UUID playerId) {
        Player player = find(playerId);
        return PlayerProfile.from(player, progress(playerId));
    }

    public PlayerProfile get(UUID playerId) {
        return PlayerProfile.from(find(playerId), null);
    }

    /** Idempotent: an existing profile is returned untouched. */
    @Transactional
    public PlayerProfile create(UUID playerId, String username) {
        Player existing = Player.findById(playerId);
        if (existing != null) {
            return PlayerProfile.from(existing, null);
        }
        List<Integer> commons;
        try {
            commons = monsters.allMonsters().stream()
                    .filter(m -> "common".equals(m.rarity()))
                    .map(MonsterInfo::monsterId)
                    .toList();
        } catch (RuntimeException e) {
            LOG.errorf("monsters service unavailable while creating player: %s", e.getMessage());
            throw ApiException.upstream("could not load the monster catalog, try again later");
        }
        List<Integer> pool;
        try {
            pool = StarterPolicy.drawPool(commons, ThreadLocalRandom.current());
        } catch (IllegalArgumentException e) {
            LOG.errorf("cannot draw starter pool: %s", e.getMessage());
            throw ApiException.upstream("the monster catalog does not contain enough common monsters");
        }
        Player player = new Player();
        player.playerId = playerId;
        player.displayName = username;
        player.stage = StageRules.INITIAL_STAGE;
        player.starterPool = new ArrayList<>(pool);
        player.startersChosen = false;
        player.createdAt = Instant.now();
        player.persist();
        return PlayerProfile.from(player, null);
    }

    @Transactional
    public PlayerProfile update(UUID playerId, UpdateProfileRequest request) {
        Player player = find(playerId);
        List<String> errors = new ArrayList<>();
        String newName = null;
        if (request.displayName() != null) {
            newName = ProfileRules.normalizeName(request.displayName());
            errors.addAll(ProfileRules.validateDisplayName(newName));
        }
        if (request.avatarMonsterId() != null) {
            errors.addAll(ProfileRules.validateAvatar(request.avatarMonsterId(), ownedSpecies(playerId)));
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("invalid profile", errors);
        }
        if (newName != null) {
            player.displayName = newName;
        }
        if (request.avatarMonsterId() != null) {
            player.avatarMonsterId = request.avatarMonsterId();
        }
        return PlayerProfile.from(player, null);
    }

    /**
     * The player row is locked for the whole call so a double submit cannot grant the starters twice.
     * If the monsters service fails the exception rolls the transaction back: starters_chosen stays false.
     */
    @Transactional
    public List<JsonNode> chooseStarters(UUID playerId, List<Integer> choice) {
        Player player = Player.findById(playerId, LockModeType.PESSIMISTIC_WRITE);
        if (player == null) {
            throw ApiException.notFound("player profile not found");
        }
        List<String> errors = StarterPolicy.validate(choice, player.starterPool, player.startersChosen);
        if (player.startersChosen) {
            throw ApiException.conflict(StarterPolicy.ALREADY_CHOSEN);
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("invalid starter choice", errors);
        }
        List<JsonNode> owned;
        try {
            owned = monsters.addBatch(new BatchRequest(playerId, List.copyOf(choice), "starter"));
        } catch (RuntimeException e) {
            LOG.errorf("monsters service failed while adding starters: %s", e.getMessage());
            throw ApiException.upstream("could not add the starters to your collection, try again later");
        }
        player.startersChosen = true;
        return owned;
    }

    @Transactional
    public PlayerProfile advanceStage(UUID playerId, int clearedStage) {
        Player player = Player.findById(playerId, LockModeType.PESSIMISTIC_WRITE);
        if (player == null) {
            throw ApiException.notFound("player profile not found");
        }
        player.stage = StageRules.advance(player.stage, clearedStage);
        return PlayerProfile.from(player, null);
    }

    private Player find(UUID playerId) {
        Player player = Player.findById(playerId);
        if (player == null) {
            throw ApiException.notFound("player profile not found");
        }
        return player;
    }

    private List<Integer> ownedSpecies(UUID playerId) {
        try {
            return monsters.collection(playerId).stream().map(OwnedRef::monsterId).toList();
        } catch (RuntimeException e) {
            LOG.errorf("monsters service unavailable while checking the avatar: %s", e.getMessage());
            throw ApiException.upstream("could not check your collection, try again later");
        }
    }

    private Progress progress(UUID playerId) {
        Integer collectionSize = null;
        Integer speciesOwned = null;
        Integer total = null;
        Integer won = null;
        try {
            CollectionStats stats = monsters.collectionStats(playerId);
            collectionSize = stats.collectionSize();
            speciesOwned = stats.speciesOwned();
        } catch (RuntimeException e) {
            LOG.warnf("collection stats unavailable: %s", e.getMessage());
        }
        try {
            HistoryStats stats = history.stats(playerId);
            total = stats.encountersTotal();
            won = stats.encountersWon();
        } catch (RuntimeException e) {
            LOG.warnf("history stats unavailable: %s", e.getMessage());
        }
        return new Progress(collectionSize, speciesOwned, total, won);
    }
}
