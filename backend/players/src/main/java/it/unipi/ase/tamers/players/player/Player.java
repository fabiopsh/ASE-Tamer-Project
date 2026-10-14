package it.unipi.ase.tamers.players.player;

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
@Table(name = "players")
public class Player extends PanacheEntityBase {
    @Id
    @Column(name = "player_id")
    public UUID playerId;

    @Column(name = "display_name", nullable = false, length = 32)
    public String displayName;

    @Column(name = "avatar_monster_id")
    public Integer avatarMonsterId;

    @Column(nullable = false)
    public int stage;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "player_starter_pool", joinColumns = @JoinColumn(name = "player_id"))
    @OrderColumn(name = "position")
    @Column(name = "monster_id")
    public List<Integer> starterPool = new ArrayList<>();

    @Column(name = "starters_chosen", nullable = false)
    public boolean startersChosen;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
