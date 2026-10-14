package it.unipi.ase.tamers.teams.team;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence for teams. Each write is its own short transaction, so no DB transaction spans the HTTP calls to monsters. */
@ApplicationScoped
public class TeamStore {

    public List<Team> findByPlayer(UUID playerId) {
        return Team.findByPlayer(playerId);
    }

    public long countByPlayer(UUID playerId) {
        return Team.countByPlayer(playerId);
    }

    public Optional<Team> find(UUID teamId) {
        return Team.<Team>findByIdOptional(teamId);
    }

    @Transactional
    public Team insert(UUID playerId, String name, List<UUID> ownedIds) {
        Team team = new Team();
        team.teamId = UUID.randomUUID();
        team.playerId = playerId;
        team.name = name;
        team.ownedIds = new ArrayList<>(ownedIds);
        team.createdAt = Instant.now();
        team.updatedAt = team.createdAt;
        team.persist();
        return team;
    }

    @Transactional
    public Optional<Team> update(UUID teamId, String name, List<UUID> ownedIds) {
        return Team.<Team>findByIdOptional(teamId).map(team -> {
            team.name = name;
            team.ownedIds.clear();
            team.ownedIds.addAll(ownedIds);
            team.updatedAt = Instant.now();
            return team;
        });
    }

    @Transactional
    public void delete(UUID teamId) {
        Team.deleteById(teamId);
    }
}
