package it.unipi.ase.tamer.monsters.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "elements")
public class Element extends PanacheEntityBase {
    @Id
    @Column(name = "element", length = 10)
    public String name;
    @Column(name = "strong_against", nullable = false)
    public String strongAgainst;
    @Column(name = "weak_against", nullable = false)
    public String weakAgainst;
    @Column(name = "cycle_position", nullable = false)
    public short cyclePosition;
}
