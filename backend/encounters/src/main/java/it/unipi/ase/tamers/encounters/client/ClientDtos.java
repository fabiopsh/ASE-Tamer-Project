package it.unipi.ase.tamers.encounters.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import it.unipi.ase.tamers.encounters.engine.Ability;
import it.unipi.ase.tamers.encounters.engine.Stats;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Payloads exchanged with the other services (see api-contract.md, sections 4-6 and 8). */
public final class ClientDtos {
    private ClientDtos() {}

    /** monsters: GET /internal/monsters item. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MonsterDetail(
            int monsterId, String name, String element, String rarity, String description, String imageUrl,
            int baseHp, int baseAttack, int baseDefense, int baseSpeed,
            double growthHp, double growthAttack, double growthDefense, double growthSpeed,
            Integer abilityId, Ability ability, String strongAgainst, String weakAgainst) {

        public Stats statsAt(int level) {
            return new Stats(
                    Stats.grow(baseHp, growthHp, level),
                    Stats.grow(baseAttack, growthAttack, level),
                    Stats.grow(baseDefense, growthDefense, level),
                    Stats.grow(baseSpeed, growthSpeed, level));
        }
    }

    /** monsters: OwnedMonster. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OwnedMonster(
            UUID ownedId, int monsterId, String name, String element, String rarity, String imageUrl,
            int level, int xp, int xpToNext, Stats stats, Ability ability, String source, UUID encounterId,
            Instant obtainedAt) {}

    public record XpAward(UUID ownedId, int xp) {}

    public record XpRequest(UUID playerId, List<XpAward> awards) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record XpResult(UUID ownedId, int oldLevel, int newLevel, int xp, int xpToNext) {}

    public record CaptureRequest(UUID playerId, int monsterId, String source, UUID encounterId) {}

    /** teams: GET /internal/teams/{team_id}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamRef(UUID teamId, UUID playerId, String name, List<UUID> ownedIds) {}

    /** players: PlayerProfile (only what encounters needs). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlayerProfile(UUID playerId, int stage, boolean gameCompleted) {}

    public record AdvanceStageRequest(int clearedStage) {}
}
