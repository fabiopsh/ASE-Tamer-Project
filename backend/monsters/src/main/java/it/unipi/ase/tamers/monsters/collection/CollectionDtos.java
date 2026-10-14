package it.unipi.ase.tamers.monsters.collection;

import it.unipi.ase.tamers.monsters.catalog.CatalogDtos.AbilityView;
import it.unipi.ase.tamers.monsters.catalog.CatalogDtos.GrowthBlock;
import it.unipi.ase.tamers.monsters.catalog.CatalogDtos.StatBlock;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CollectionDtos {
    private CollectionDtos() {}

    public record OwnedMonsterView(UUID ownedId, int monsterId, String name, String element, String rarity,
                                   String imageUrl, int level, int xp, int xpToNext, StatBlock stats,
                                   AbilityView ability, String source, UUID encounterId, Instant obtainedAt) {}

    public record OwnedMonsterDetail(UUID ownedId, int monsterId, String name, String element, String rarity,
                                     String imageUrl, int level, int xp, int xpToNext, StatBlock stats,
                                     AbilityView ability, String source, UUID encounterId, Instant obtainedAt,
                                     String description, StatBlock baseStats, GrowthBlock growth,
                                     String strongAgainst, String weakAgainst) {}

    public record CollectionStats(int collectionSize, int speciesOwned) {}

    public record AddRequest(@NotNull UUID playerId, @NotNull Integer monsterId, @NotBlank String source,
                             UUID encounterId) {}

    public record BatchRequest(@NotNull UUID playerId,
                               @NotEmpty @Size(max = 50) List<@NotNull Integer> monsterIds,
                               @NotBlank String source) {}

    public record XpAward(@NotNull UUID ownedId, @NotNull @Min(0) @Max(10_000_000) Integer xp) {}

    public record XpRequest(@NotNull UUID playerId, @NotNull @Size(max = 100) List<@Valid @NotNull XpAward> awards) {}

    public record XpResult(UUID ownedId, int oldLevel, int newLevel, int xp, int xpToNext) {}
}
