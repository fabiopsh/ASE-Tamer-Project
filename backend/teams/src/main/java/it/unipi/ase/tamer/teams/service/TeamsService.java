package it.unipi.ase.tamer.teams.service;

import io.quarkus.panache.common.Sort;
import it.unipi.ase.tamer.teams.api.dto.InternalTeamMember;
import it.unipi.ase.tamer.teams.api.dto.InternalTeamResponse;
import it.unipi.ase.tamer.teams.api.dto.ResetTeamsResponse;
import it.unipi.ase.tamer.teams.api.dto.TeamMemberResponse;
import it.unipi.ase.tamer.teams.api.dto.TeamRequest;
import it.unipi.ase.tamer.teams.api.dto.TeamResponse;
import it.unipi.ase.tamer.teams.api.dto.TeamValidationResponse;
import it.unipi.ase.tamer.teams.client.MonsterInstance;
import it.unipi.ase.tamer.teams.client.MonstersClient;
import it.unipi.ase.tamer.teams.domain.Team;
import it.unipi.ase.tamer.teams.domain.TeamSlot;
import it.unipi.ase.tamer.teams.error.ApiException;
import it.unipi.ase.tamer.teams.error.ErrorDetail;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/** Team creation and re-validation; monster information is never copied into teams-db. */
@ApplicationScoped
public class TeamsService {

    @Inject
    @RestClient
    MonstersClient monsters;

    @Inject
    TeamValidator validator;

    @Inject
    EntityManager em;

    public TeamValidationResponse validate(UUID playerId, List<UUID> members) {
        return check(playerId, null, members, false, 0).response();
    }

    public List<TeamResponse> teams(UUID playerId) {
        List<Team> owned = Team.list("playerId", Sort.by("createdAt"), playerId);
        return owned.stream().map(team -> toResponse(team, playerId)).toList();
    }

    public TeamResponse team(UUID playerId, UUID teamId) {
        return toResponse(requireTeam(playerId, teamId), playerId);
    }

    @Transactional
    public TeamResponse create(UUID playerId, TeamRequest request) {
        // Lock per player to enforce the five-teams limit even with concurrent create requests.
        lock("teams:" + playerId);
        TeamValidator.Result preliminary = check(playerId, request.name(), request.members(), true, 0);
        long count = preliminary.runId() == null ? 0
                : Team.count("playerId = ?1 and runId = ?2", playerId, preliminary.runId());
        TeamValidator.Result result = count >= 5
                ? check(playerId, request.name(), request.members(), true, count)
                : preliminary;
        requireValid(result);
        Team team = new Team();
        team.teamId = UUID.randomUUID();
        team.playerId = playerId;
        team.runId = result.runId();
        team.name = request.name();
        team.createdAt = Instant.now();
        team.updatedAt = team.createdAt;
        team.persist();
        saveSlots(team.teamId, request.members());
        em.flush();
        return toResponse(team, playerId);
    }

    @Transactional
    public TeamResponse update(UUID playerId, UUID teamId, TeamRequest request) {
        Team team = requireTeam(playerId, teamId);
        if (request.name() == null) {
            throw new ApiException(400, "TEAM_INVALID", "Team name is required.",
                    List.of(new ErrorDetail("name", "NAME_INVALID")));
        }
        TeamValidator.Result result = check(playerId, request.name(), request.members(), false, 0);
        requireValid(result);
        if (!team.runId.equals(result.runId())) {
            throw teamInvalid("MIXED_RUNS", "A team can only use monsters from its own run.");
        }
        team.name = request.name();
        team.updatedAt = Instant.now();
        TeamSlot.delete("teamId", teamId);
        em.flush();
        saveSlots(teamId, request.members());
        em.flush();
        return toResponse(team, playerId);
    }

    @Transactional
    public void delete(UUID playerId, UUID teamId) {
        Team team = requireTeam(playerId, teamId);
        TeamSlot.delete("teamId", teamId);
        team.delete();
    }

    public InternalTeamResponse internalTeam(UUID playerId, UUID teamId) {
        Team team = requireTeam(playerId, teamId);
        List<TeamSlot> slots = slots(teamId);
        List<UUID> ids = slots.stream().map(s -> s.ownedId).toList();
        TeamValidator.Result result = check(playerId, team.name, ids, false, 0);
        requireValid(result, 409);
        if (!team.runId.equals(result.runId())) {
            throw teamInvalid("MIXED_RUNS", "Team belongs to an earlier run.");
        }
        Map<UUID, MonsterInstance> byId = map(instances(playerId, ids));
        List<InternalTeamMember> members = slots.stream().map(slot -> {
            MonsterInstance m = byId.get(slot.ownedId);
            return new InternalTeamMember(slot.position, m.ownedId(), m.monsterId(), m.name(),
                    m.level(), m.element(), m.rarity(), m.imageUrl(), m.stats(), m.ability());
        }).toList();
        return new InternalTeamResponse(team.teamId, team.name, team.runId, members);
    }

    @Transactional
    public ResetTeamsResponse reset(UUID playerId, UUID runId) {
        List<Team> affected = Team.list("playerId = ?1 and runId = ?2", playerId, runId);
        for (Team team : affected) {
            TeamSlot.delete("teamId", team.teamId);
            team.delete();
        }
        return new ResetTeamsResponse(affected.size());
    }

    private TeamValidator.Result check(UUID playerId, String name, List<UUID> members,
                                       boolean isCreate, long existingTeams) {
        List<MonsterInstance> owned = members == null || members.isEmpty() || members.contains(null)
                ? List.of() : instances(playerId, members);
        return validator.validate(name, members, owned, isCreate, existingTeams);
    }

    private TeamResponse toResponse(Team team, UUID playerId) {
        List<TeamSlot> slots = slots(team.teamId);
        Map<UUID, MonsterInstance> byId = map(instances(playerId,
                slots.stream().map(slot -> slot.ownedId).toList()));
        List<TeamMemberResponse> members = new ArrayList<>();
        for (TeamSlot slot : slots) {
            MonsterInstance m = byId.get(slot.ownedId);
            if (m == null) {
                // Team data is inconsistent with its owner; do not return a misleading summary.
                throw ApiException.dependencyUnavailable(new IllegalStateException("Missing team instance"));
            }
            members.add(new TeamMemberResponse(slot.position, m.ownedId(), m.monsterId(),
                    m.name(), m.level(), m.element(), m.imageUrl()));
        }
        return new TeamResponse(team.teamId, team.name, team.runId,
                members, team.createdAt, team.updatedAt);
    }

    private List<MonsterInstance> instances(UUID playerId, List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String csv = String.join(",", ids.stream().map(UUID::toString).toList());
        try {
            return monsters.instances(playerId, csv);
        } catch (RuntimeException e) {
            throw ApiException.dependencyUnavailable(e);
        }
    }

    private static Map<UUID, MonsterInstance> map(List<MonsterInstance> list) {
        Map<UUID, MonsterInstance> result = new HashMap<>();
        for (MonsterInstance item : list) {
            result.put(item.ownedId(), item);
        }
        return result;
    }

    private static List<TeamSlot> slots(UUID teamId) {
        List<TeamSlot> slots = TeamSlot.list("teamId", teamId);
        slots.sort(Comparator.comparingInt(slot -> slot.position));
        return slots;
    }

    private static Team requireTeam(UUID playerId, UUID teamId) {
        Team team = Team.find("teamId = ?1 and playerId = ?2", teamId, playerId).firstResult();
        if (team == null) {
            throw ApiException.notFound("Team not found.");
        }
        return team;
    }

    private static void requireValid(TeamValidator.Result result) {
        requireValid(result, 400);
    }

    private static void requireValid(TeamValidator.Result result, int status) {
        if (!result.valid()) {
            var details = result.errors().stream().map(e -> new ErrorDetail(
                    "NAME_INVALID".equals(e.code()) ? "name"
                            : e.ownedId() == null ? "members" : "owned_id", e.code())).toList();
            throw new ApiException(status, "TEAM_INVALID", result.errors().getFirst().message(), details);
        }
    }

    private static ApiException teamInvalid(String code, String message) {
        return new ApiException(409, "TEAM_INVALID", message, List.of(new ErrorDetail("members", code)));
    }

    private static void saveSlots(UUID teamId, List<UUID> members) {
        for (int i = 0; i < members.size(); i++) {
            TeamSlot slot = new TeamSlot();
            slot.teamId = teamId;
            slot.position = (short) (i + 1);
            slot.ownedId = members.get(i);
            slot.persist();
        }
    }

    private void lock(String key) {
        em.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(hashtext(:key))")
                .setParameter("key", key).getSingleResult();
    }
}
