package it.unipi.ase.tamers.monsters.catalog;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A shared ability of the catalog (seed data, read-only at runtime). */
@Entity
@Table(name = "abilities")
public class Ability extends PanacheEntityBase {
    @Id
    @Column(name = "ability_id")
    public int abilityId;

    @Column(nullable = false, length = 60)
    public String name;

    /** generic | element | legendary */
    @Column(nullable = false, length = 20)
    public String scope;

    /** Only for element-scoped abilities. */
    @Column(length = 20)
    public String element;

    @Column(name = "effect_type", nullable = false, length = 40)
    public String effectType;

    @Column(name = "effect_value", nullable = false)
    public int effectValue;

    // "trigger" is a reserved word in some SQL dialects
    @Column(name = "trigger_when", nullable = false, length = 40)
    public String trigger;

    @Column(nullable = false, length = 300)
    public String description;
}
