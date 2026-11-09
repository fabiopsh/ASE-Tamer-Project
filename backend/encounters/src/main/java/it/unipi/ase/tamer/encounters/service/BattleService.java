package it.unipi.ase.tamer.encounters.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import it.unipi.ase.tamer.encounters.api.dto.ActionRequest;
import it.unipi.ase.tamer.encounters.api.dto.BattleResultResponse;
import it.unipi.ase.tamer.encounters.api.dto.BattleStateResponse;
import it.unipi.ase.tamer.encounters.client.AbilityInfo;
import it.unipi.ase.tamer.encounters.client.MonsterCandidate;
import it.unipi.ase.tamer.encounters.client.MonstersClient;
import it.unipi.ase.tamer.encounters.client.RemoteClientError;
import it.unipi.ase.tamer.encounters.client.SpeciesStats;
import it.unipi.ase.tamer.encounters.client.StatBlock;
import it.unipi.ase.tamer.encounters.client.TeamSnapshot;
import it.unipi.ase.tamer.encounters.client.TeamsClient;
import it.unipi.ase.tamer.encounters.domain.EncounterTemplate;
import it.unipi.ase.tamer.encounters.domain.Run;
import it.unipi.ase.tamer.encounters.engine.Ability;
import it.unipi.ase.tamer.encounters.engine.Action;
import it.unipi.ase.tamer.encounters.engine.Battle;
import it.unipi.ase.tamer.encounters.engine.BattleRandom;
import it.unipi.ase.tamer.encounters.engine.Combatant;
import it.unipi.ase.tamer.encounters.engine.ElementChart;
import it.unipi.ase.tamer.encounters.engine.EncounterSpec;
import it.unipi.ase.tamer.encounters.engine.InvalidActionException;
import it.unipi.ase.tamer.encounters.engine.WildSelector;
import it.unipi.ase.tamer.encounters.error.ApiException;
import it.unipi.ase.tamer.encounters.error.ErrorDetail;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/** Battle API (EN-3): start, current state, actions. The outcome is computed only here, by the engine. */
@ApplicationScoped
public class BattleService {

    @Inject
    BattleRegistry registry;

    @Inject
    RecoveryService recovery;

    @Inject
    RunService runs;

    @Inject
    AvailabilityService availability;

    @Inject
    BattleCompletionService completions;

    @Inject
    SeedSource seeds;

    @Inject
    TimeSource time;

    @Inject
    ObjectMapper mapper;

    @Inject
    @RestClient
    MonstersClient monsters;

    @Inject
    @RestClient
    TeamsClient teams;

    /** {@code POST /api/battles} (US-18). */
    public BattleStateResponse start(UUID playerId, int templateId, UUID teamId) {
        return registry.withPlayerLock(playerId, () -> {
            recovery.recover(playerId, true);
            Run run = runs.activeRun(playerId);
            if (run == null) {
                throw EncounterErrors.noActiveRun(409);
            }
            if (run.resetPending) {
                throw EncounterErrors.resetPending();
            }
            if (run.activeBattleId != null || registry.get(playerId) != null) {
                throw EncounterErrors.battleInProgress();
            }
            EncounterTemplate template = availability.availableTemplate(run, templateId)
                    .orElseThrow(() -> ApiException.notFound("Encounter not available."));
            TeamSnapshot team = team(teamId, playerId);
            if (!run.runId.equals(team.runId())) {
                throw teamInvalid("The team belongs to another run.", "MIXED_RUNS");
            }
            List<TeamSnapshot.Member> members = team.members() == null ? List.of()
                    : team.members().stream().sorted(Comparator.comparingInt(TeamSnapshot.Member::position)).toList();
            if (members.isEmpty() || members.size() > Battle.MAX_TEAM_SIZE) {
                throw teamInvalid("A team has between 1 and 3 monsters.", "TEAM_SIZE");
            }

            long seed = seeds.next();
            BattleRandom rng = new BattleRandom(seed);
            int monsterId;
            int level;
            if (template.isBoss) {
                monsterId = template.bossMonsterId;
                level = template.levelMin;
            } else {
                Set<Integer> bossIds = availability.bossMonsterIds();
                String exclude = bossIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                List<MonsterCandidate> candidates = Remote.call(() -> monsters.candidates(template.element, exclude));
                List<WildSelector.Species> species = candidates.stream()
                        .map(c -> new WildSelector.Species(c.monsterId(), c.element(), c.rarity())).toList();
                WildSelector.WildPick pick;
                try {
                    pick = WildSelector.select(rng, species, template.element, bossIds, template.weights(),
                            template.levelMin, template.levelMax);
                } catch (IllegalStateException e) {
                    throw ApiException.dependencyUnavailable(e);
                }
                monsterId = pick.monsterId();
                level = pick.level();
            }
            int wildId = monsterId;
            int wildLevel = level;
            SpeciesStats stats = Remote.call(() -> monsters.stats(wildId, wildLevel));
            StatBlock s = stats.stats();
            Combatant wild = new Combatant(null, stats.monsterId(), stats.name(), stats.element(), stats.rarity(),
                    wildLevel, s.hp(), s.attack(), s.defense(), s.speed(), ability(stats.ability()));
            List<Combatant> combatants = members.stream()
                    .map(m -> new Combatant(m.ownedId().toString(), m.monsterId(), m.name(), m.element(), m.rarity(),
                            m.level(), m.stats().hp(), m.stats().attack(), m.stats().defense(), m.stats().speed(),
                            ability(m.ability())))
                    .toList();
            Battle battle = new Battle(rng, new EncounterSpec(template.info(), wild), combatants,
                    ElementChart.defaults());

            UUID encounterId = UUID.randomUUID();
            Instant now = time.now();
            JsonNode wildInfo = HistoryRecordBuilder.wild(mapper, wild);
            JsonNode teamSnapshot = HistoryRecordBuilder.team(mapper, members);
            UUID runId = run.runId;
            QuarkusTransaction.requiringNew().run(() -> {
                Run row = Run.findById(runId, LockModeType.PESSIMISTIC_WRITE);
                if (row == null || !RunRules.ACTIVE.equals(row.status) || row.activeBattleId != null) {
                    throw EncounterErrors.battleInProgress();
                }
                row.activeBattleId = encounterId;
                row.activeBattleTemplateId = template.templateId;
                row.activeBattleTeamId = teamId;
                row.activeBattleStartedAt = now;
                row.activeBattleSeed = seed;
                row.activeBattleWild = wildInfo;
                row.activeBattleTeam = teamSnapshot;
            });
            BattleSession session = new BattleSession(encounterId, playerId, runId, teamId, battle, wildInfo,
                    teamSnapshot, now);
            registry.put(session);
            return BattleStateResponse.of(encounterId, battle.view(), null);
        });
    }

    /** {@code GET /api/battles/current}: the battle in progress; none → 404. */
    public BattleStateResponse current(UUID playerId) {
        recovery.recover(playerId, false);
        BattleSession session = registry.get(playerId);
        if (session == null || session.closed()) {
            throw EncounterErrors.noBattle();
        }
        session.lock().lock();
        try {
            return BattleStateResponse.of(session.encounterId(), session.battle().view(), null);
        } finally {
            session.lock().unlock();
        }
    }

    /** {@code POST /api/battles/current/actions}: resolves one round (US-19, US-21, US-22). */
    public BattleStateResponse act(UUID playerId, ActionRequest request) {
        return registry.withPlayerLock(playerId, () -> {
            recovery.recover(playerId, true);
            BattleSession session = registry.get(playerId);
            if (session == null || session.closed()) {
                throw EncounterErrors.noBattle();
            }
            Action action;
            try {
                action = new Action(Action.Type.fromWire(request.type()), request.slot());
            } catch (InvalidActionException e) {
                throw EncounterErrors.invalidAction(e.getMessage());
            }
            session.lock().lock();
            try {
                if (session.closed()) {
                    throw EncounterErrors.noBattle();
                }
                Battle battle = session.battle();
                try {
                    battle.apply(action);
                } catch (InvalidActionException e) {
                    throw EncounterErrors.invalidAction(e.getMessage());
                }
                session.touch(time.now());
                BattleResultResponse result = battle.isFinished() ? completions.finish(session) : null;
                return BattleStateResponse.of(session.encounterId(), battle.view(), result);
            } finally {
                session.lock().unlock();
            }
        });
    }

    private TeamSnapshot team(UUID teamId, UUID playerId) {
        try {
            TeamSnapshot team = teams.team(teamId, playerId);
            if (team == null) {
                throw ApiException.notFound("Team not found.");
            }
            return team;
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            RemoteClientError error = Remote.clientError(e);
            if (error == null) {
                throw ApiException.dependencyUnavailable(e);
            }
            if (error.status() == 404) {
                throw ApiException.notFound("Team not found.");
            }
            throw new ApiException(409, EncounterErrors.TEAM_INVALID,
                    error.getMessage() == null ? "The team is not valid." : error.getMessage());
        }
    }

    private static ApiException teamInvalid(String message, String code) {
        return new ApiException(409, EncounterErrors.TEAM_INVALID, message,
                List.of(new ErrorDetail("team_id", code)));
    }

    private static Ability ability(AbilityInfo info) {
        return info == null ? null : info.toEngine();
    }
}
