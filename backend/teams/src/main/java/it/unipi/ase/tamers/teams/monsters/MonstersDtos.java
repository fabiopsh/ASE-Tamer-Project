package it.unipi.ase.tamers.teams.monsters;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public final class MonstersDtos {
    private MonstersDtos() {}

    /**
     * A monster owned by a player, as returned by monsters:/internal/collection/{player_id}/owned.
     * stats and ability are kept as JSON trees: teams only pass them through to the client.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OwnedMonster(
            UUID ownedId,
            int monsterId,
            String name,
            String element,
            String rarity,
            String imageUrl,
            int level,
            int xp,
            int xpToNext,
            JsonNode stats,
            JsonNode ability,
            String source,
            UUID encounterId,
            Instant obtainedAt) {}
}
