package it.unipi.ase.tamer.players.api.dto;

import it.unipi.ase.tamer.players.domain.Player;
import java.time.Instant;
import java.util.UUID;

/** US-03: public (and internal creation) profile view. */
public record PlayerResponse(UUID playerId, String displayName, String avatar,
                             UUID currentRunId, boolean startersChosen, Instant createdAt) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(player.playerId, player.displayName, player.avatar,
                player.currentRunId, player.startersChosen, player.createdAt);
    }
}
