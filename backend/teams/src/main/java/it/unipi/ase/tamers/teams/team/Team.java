package it.unipi.ase.tamers.teams.team;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "teams")
public class Team extends PanacheEntityBase {
    @Id
    @Column(name = "team_id")
    public UUID teamId;

    @Column(name = "player_id", nullable = false)
    public UUID playerId;

    @Column(nullable = false, length = 30)
    public String name;

    /** Battle order: the position column keeps the order of the members. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "team_members", joinColumns = @JoinColumn(name = "team_id"))
    @OrderColumn(name = "position")
    @Column(name = "owned_id", nullable = false)
    public List<UUID> ownedIds = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    public static List<Team> findByPlayer(UUID playerId) {
        return list("playerId = ?1 order by createdAt", playerId);
    }

    public static long countByPlayer(UUID playerId) {
        return count("playerId", playerId);
    }
}
