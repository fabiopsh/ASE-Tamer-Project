package it.unipi.ase.tamers.teams.team;

import it.unipi.ase.tamers.teams.common.ApiException;
import it.unipi.ase.tamers.teams.monsters.MonstersClient;
import it.unipi.ase.tamers.teams.monsters.MonstersDtos.OwnedMonster;
import it.unipi.ase.tamers.teams.team.TeamDtos.InternalTeam;
import it.unipi.ase.tamers.teams.team.TeamDtos.TeamRequest;
import it.unipi.ase.tamers.teams.team.TeamDtos.TeamView;
import it.unipi.ase.tamers.teams.team.TeamDtos.ValidateRequest;
import it.unipi.ase.tamers.teams.team.TeamDtos.ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TeamService {
    private static final Logger LOG = Logger.getLogger(TeamService.class);

    @Inject
    TeamStore store;
    @Inject
    @RestClient
    MonstersClient monsters;

    public List<TeamView> list(UUID playerId) {
        return toViews(playerId, store.findByPlayer(playerId));
    }

    public TeamView get(UUID playerId, UUID teamId) {
        return toViews(playerId, List.of(ownedTeam(playerId, teamId))).get(0);
    }

    public TeamView create(UUID playerId, TeamRequest request) {
        List<UUID> ids = orEmpty(request.ownedIds());
        Set<UUID> owned = ownedAmong(playerId, ids);
        List<String> errors = new ArrayList<>(TeamRules.validateCreation(store.countByPlayer(playerId)));
        errors.addAll(TeamRules.validate(request.name(), ids, owned));
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("invalid team", errors);
        }
        Team team = store.insert(playerId, TeamRules.normalizeName(request.name()), ids);
        return toViews(playerId, List.of(team)).get(0);
    }

    public TeamView update(UUID playerId, UUID teamId, TeamRequest request) {
        ownedTeam(playerId, teamId);
        List<UUID> ids = orEmpty(request.ownedIds());
        Set<UUID> owned = ownedAmong(playerId, ids);
        List<String> errors = TeamRules.validate(request.name(), ids, owned);
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("invalid team", errors);
        }
        Team team = store.update(teamId, TeamRules.normalizeName(request.name()), ids)
                .orElseThrow(() -> ApiException.notFound("team not found"));
        return toViews(playerId, List.of(team)).get(0);
    }

    public void delete(UUID playerId, UUID teamId) {
        ownedTeam(playerId, teamId);
        store.delete(teamId);
    }

    public ValidationResult validate(UUID playerId, ValidateRequest request) {
        List<UUID> ids = orEmpty(request.ownedIds());
        Set<UUID> owned = ownedAmong(playerId, ids);
        List<String> errors = request.name() == null
                ? TeamRules.validateMembers(ids, owned)
                : TeamRules.validate(request.name(), ids, owned);
        return new ValidationResult(errors.isEmpty(), errors);
    }

    public InternalTeam internalTeam(UUID teamId, UUID playerId) {
        return store.find(teamId)
                .filter(team -> team.playerId.equals(playerId))
                .map(team -> new InternalTeam(team.teamId, team.playerId, team.name, new ArrayList<>(team.ownedIds)))
                .orElseThrow(() -> ApiException.notFound("team not found"));
    }

    private Team ownedTeam(UUID playerId, UUID teamId) {
        Team team = store.find(teamId).orElseThrow(() -> ApiException.notFound("team not found"));
        if (!team.playerId.equals(playerId)) {
            throw ApiException.forbidden("the team belongs to another player");
        }
        return team;
    }

    /** Ids among the given ones that the player owns (monsters is called only when there is something to check). */
    private Set<UUID> ownedAmong(UUID playerId, List<UUID> ids) {
        Set<UUID> distinct = ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        return fetchOwned(playerId, distinct).stream()
                .map(OwnedMonster::ownedId)
                .collect(Collectors.toSet());
    }

    private List<OwnedMonster> fetchOwned(UUID playerId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String csv = ids.stream().map(UUID::toString).collect(Collectors.joining(","));
        try {
            List<OwnedMonster> owned = monsters.owned(playerId, csv);
            return owned == null ? List.of() : owned;
        } catch (RuntimeException e) {
            LOG.errorf("monsters service unavailable: %s", e.getMessage());
            throw ApiException.upstream("monsters service unavailable, try again later");
        }
    }

    /** One call to monsters for all the teams: members in team order, validity recomputed. */
    private List<TeamView> toViews(UUID playerId, List<Team> teams) {
        if (teams.isEmpty()) {
            return List.of();
        }
        Set<UUID> allIds = new LinkedHashSet<>();
        teams.forEach(team -> allIds.addAll(team.ownedIds));
        Map<UUID, OwnedMonster> byId = fetchOwned(playerId, allIds).stream()
                .collect(Collectors.toMap(OwnedMonster::ownedId, m -> m, (a, b) -> a));
        return teams.stream().map(team -> view(team, byId)).toList();
    }

    private static TeamView view(Team team, Map<UUID, OwnedMonster> byId) {
        List<UUID> ids = new ArrayList<>(team.ownedIds);
        List<OwnedMonster> members = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        List<String> errors = TeamRules.validate(team.name, ids, byId.keySet());
        return new TeamView(team.teamId, team.name, ids, members, errors.isEmpty(), errors,
                team.createdAt, team.updatedAt);
    }

    private static List<UUID> orEmpty(List<UUID> ids) {
        return ids == null ? List.of() : ids;
    }
}
