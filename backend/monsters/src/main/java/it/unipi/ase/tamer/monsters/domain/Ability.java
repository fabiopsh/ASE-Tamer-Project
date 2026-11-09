package it.unipi.ase.tamer.monsters.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "abilities")
public class Ability extends PanacheEntityBase {
    @Id
    @Column(name = "ability_id")
    public int abilityId;
    public String name;
    public String scope;
    public String element;
    @Column(name = "effect_type")
    public String effectType;
    @Column(name = "effect_value")
    public int effectValue;
    public String trigger;
    public String description;
}
