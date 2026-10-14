package it.unipi.ase.tamers.players.player;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PlayerDtos {
    private PlayerDtos() {}

    // ---- public / internal API ----

    /** Fields are null when the owning service did not answer. */
    public record Progress(Integer collectionSize, Integer speciesOwned, Integer encountersTotal, Integer encountersWon) {
        public static Progress unavailable() {
            return new Progress(null, null, null, null);
        }
    }

    public record PlayerProfile(
            UUID playerId,
            String displayName,
            Integer avatarMonsterId,
            int stage,
            int maxStage,
            boolean gameCompleted,
            boolean startersChosen,
            List<Integer> starterPool,
            Instant createdAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) Progress progress) {

        public static PlayerProfile from(Player p, Progress progress) {
            return new PlayerProfile(p.playerId, p.displayName, p.avatarMonsterId, p.stage, StageRules.MAX_STAGE,
                    StageRules.isCompleted(p.stage), p.startersChosen, List.copyOf(p.starterPool), p.createdAt, progress);
        }
    }

    public record UpdateProfileRequest(String displayName, Integer avatarMonsterId) {}

    public record StartersRequest(@NotNull List<Integer> monsterIds) {}

    public record CreatePlayerRequest(@NotNull UUID playerId, @NotNull String username) {}

    public record AdvanceStageRequest(@NotNull @Min(1) Integer clearedStage) {}

    // ---- payloads exchanged with other services (only the fields we need) ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MonsterInfo(int monsterId, String rarity) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OwnedRef(int monsterId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CollectionStats(Integer collectionSize, Integer speciesOwned) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HistoryStats(Integer encountersTotal, Integer encountersWon) {}

    public record BatchRequest(UUID playerId, List<Integer> monsterIds, String source) {}
}
