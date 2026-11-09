package it.unipi.ase.tamer.monsters.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "xp_grants")
public class XpGrant extends PanacheEntityBase {
    @Id
    @Column(name = "encounter_id")
    public UUID encounterId;
    @Column(name = "player_id", nullable = false)
    public UUID playerId;
    public int xp;
    @Column(name = "granted_at")
    public Instant grantedAt;
}
