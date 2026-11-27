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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        List<MonsterInstance> fetched = fetchForValidation(playerId, members);
        return validator.validate(null, members, fetched, false, 0).response();
    }

    public List<TeamResponse> teams(UUID playerId) {
        List<Team> owned = Team.list("playerId", Sort.by("createdAt"), playerId);
        if (owned.isEmpty()) {
            return List.of();
        }
        List<UUID> teamIds = owned.stream().map(t -> t.teamId).toList();
        List<TeamSlot> allSlots = TeamSlot.list("teamId in ?1", teamIds);
        Map<UUID, List<TeamSlot>> slotsByTeam = new HashMap<>();
        Set<UUID> allOwnedIds = new LinkedHashSet<>();
        for (TeamSlot slot : allSlots) {
            slotsByTeam.computeIfAbsent(slot.teamId, k -> new ArrayList<>()).add(slot);
            allOwnedIds.add(slot.ownedId);
        }
        slotsByTeam.values().forEach(list -> list.sort(Comparator.comparingInt(s -> s.position)));
        Map<UUID, MonsterInstance> byId = map(instances(playerId, new ArrayList<>(allOwnedIds)));
        return owned.stream()
                .map(team -> toResponse(team, slotsByTeam.getOrDefault(team.teamId, List.of()), byId))
                .toList();
    }

    public TeamResponse team(UUID playerId, UUID teamId) {
        Team team = requireTeam(playerId, teamId);
        List<TeamSlot> slots = slots(team.teamId);
        Map<UUID, MonsterInstance> byId = map(instances(playerId,
                slots.stream().map(slot -> slot.ownedId).toList()));
        return toResponse(team, slots, byId);
    }

    @Transactional
    public TeamResponse create(UUID playerId, TeamRequest request) {
        // Lock per player to enforce the five-teams limit even with concurrent create requests.
        lock("teams:" + playerId);
        List<MonsterInstance> fetched = fetchForValidation(playerId, request.members());
        TeamValidator.Result preliminary = validator.validate(request.name(), request.members(), fetched, true, 0);
        long count = preliminary.runId() == null ? 0
                : Team.count("playerId = ?1 and runId = ?2", playerId, preliminary.runId());
        TeamValidator.Result result = count >= 5
                ? validator.validate(request.name(), request.members(), fetched, true, count)
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
        List<TeamSlot> savedSlots = saveSlots(team.teamId, request.members());
        em.flush();
        return toResponse(team, savedSlots, map(fetched));
    }

    @Transactional
    public TeamResponse update(UUID playerId, UUID teamId, TeamRequest request) {
        lock("teams:" + playerId);
        Team team = requireTeam(playerId, teamId);
        if (request.name() == null) {
            throw new ApiException(400, "TEAM_INVALID", "Team name is required.",
                    List.of(new ErrorDetail("name", "NAME_INVALID")));
        }
        List<MonsterInstance> fetched = fetchForValidation(playerId, request.members());
        TeamValidator.Result result = validator.validate(request.name(), request.members(), fetched, false, 0);
        requireValid(result);
        if (!team.runId.equals(result.runId())) {
            throw teamInvalid("MIXED_RUNS", "A team can only use monsters from its own run.");
        }
        team.name = request.name();
        team.updatedAt = Instant.now();
        TeamSlot.delete("teamId", teamId);
        em.flush();
        List<TeamSlot> savedSlots = saveSlots(teamId, request.members());
        em.flush();
        return toResponse(team, savedSlots, map(fetched));
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
        List<MonsterInstance> fetched = fetchForValidation(playerId, ids);
        TeamValidator.Result result = validator.validate(team.name, ids, fetched, false, 0);
        requireValid(result, 409);
        if (!team.runId.equals(result.runId())) {
            throw teamInvalid("MIXED_RUNS", "Team belongs to an earlier run.");
        }
        Map<UUID, MonsterInstance> byId = map(fetched);
        List<InternalTeamMember> members = new ArrayList<>(slots.size());
        for (TeamSlot slot : slots) {
            MonsterInstance m = byId.get(slot.ownedId);
            if (m == null) {
                throw teamInvalid("NOT_OWNED", "Monster is missing or does not belong to the player.");
            }
            members.add(new InternalTeamMember(slot.position, m.ownedId(), m.monsterId(), m.name(),
                    m.level(), m.element(), m.rarity(), m.imageUrl(), m.stats(), m.ability()));
        }
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

    /** Only queries monsters once local shape checks pass (non-null, 1..3, no nulls, no duplicates). */
    private List<MonsterInstance> fetchForValidation(UUID playerId, List<UUID> members) {
        if (!TeamValidator.hasValidMemberShape(members)) {
            return null;
        }
        return instances(playerId, members);
    }

    private TeamResponse toResponse(Team team, List<TeamSlot> slots, Map<UUID, MonsterInstance> byId) {
        List<TeamMemberResponse> members = new ArrayList<>(slots.size());
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
        if (list != null) {
            for (MonsterInstance item : list) {
                result.put(item.ownedId(), item);
            }
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

    private static List<TeamSlot> saveSlots(UUID teamId, List<UUID> members) {
        List<TeamSlot> saved = new ArrayList<>(members.size());
        for (int i = 0; i < members.size(); i++) {
            TeamSlot slot = new TeamSlot();
            slot.teamId = teamId;
            slot.position = (short) (i + 1);
            slot.ownedId = members.get(i);
            slot.persist();
            saved.add(slot);
        }
        return saved;
    }

    private void lock(String key) {
        em.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(hashtext(:key))")
                .setParameter("key", key).getSingleResult();
    }
}
