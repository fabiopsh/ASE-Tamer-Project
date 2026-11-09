package it.unipi.ase.tamer.encounters.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

/** {@code GET /internal/teams/{team_id}?player_id=}: the revalidated team, members in position order. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamSnapshot(UUID teamId, String name, UUID runId, List<Member> members) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Member(int position, UUID ownedId, int monsterId, String name, int level, String element,
                         String rarity, String imageUrl, StatBlock stats, AbilityInfo ability) {
    }
}
