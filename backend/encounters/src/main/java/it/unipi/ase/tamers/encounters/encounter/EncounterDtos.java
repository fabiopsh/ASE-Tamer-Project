package it.unipi.ase.tamers.encounters.encounter;

import it.unipi.ase.tamers.encounters.client.ClientDtos.OwnedMonster;
import it.unipi.ase.tamers.encounters.engine.Ability;
import it.unipi.ase.tamers.encounters.engine.BattleEvent;
import it.unipi.ase.tamers.encounters.engine.Outcome;
import it.unipi.ase.tamers.encounters.engine.Stats;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EncounterDtos {
    private EncounterDtos() {}

    public record StartRequest(@NotNull UUID teamId) {}

    public record OfferEnemy(int monsterId, String name, String element, String rarity, String imageUrl, int level,
                             Stats stats, Ability ability) {}

    public record OfferRewards(int xp, double captureChance, boolean advancesStage) {}

    public record OfferView(UUID encounterId, String kind, int stage, String status, String difficulty,
                            OfferEnemy enemy, OfferRewards rewards, Instant createdAt) {}

    public record BoardView(int stage, boolean gameCompleted, List<OfferView> encounters) {}

    public record ResultMember(UUID ownedId, int monsterId, String name, String element, String imageUrl, int level,
                               int maxHp, Stats stats) {}

    public record ResultEnemy(int monsterId, String name, String element, String imageUrl, int level, int maxHp,
                              Stats stats) {}

    public record XpReward(UUID ownedId, int xp, int oldLevel, int newLevel) {}

    public record RunRewards(List<XpReward> xp, OwnedMonster captured, boolean stageAdvanced, int newStage) {}

    public record EncounterResult(UUID runId, UUID encounterId, String kind, int stage, Outcome outcome, int rounds,
                                  long seed, List<ResultMember> team, ResultEnemy enemy, List<BattleEvent> log,
                                  RunRewards rewards, Instant occurredAt) {}
}
