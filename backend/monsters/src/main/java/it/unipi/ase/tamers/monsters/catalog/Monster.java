package it.unipi.ase.tamers.monsters.catalog;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A species of the catalog (seed data, read-only at runtime). */
@Entity
@Table(name = "monsters")
public class Monster extends PanacheEntityBase {
    @Id
    @Column(name = "monster_id")
    public int monsterId;

    @Column(nullable = false, unique = true, length = 60)
    public String name;

    @Column(nullable = false, length = 20)
    public String element;

    @Column(nullable = false, length = 20)
    public String rarity;

    @Column(nullable = false, length = 500)
    public String description;

    @Column(name = "image_url", nullable = false)
    public String imageUrl;

    @Column(name = "base_hp", nullable = false)
    public int baseHp;
    @Column(name = "base_attack", nullable = false)
    public int baseAttack;
    @Column(name = "base_defense", nullable = false)
    public int baseDefense;
    @Column(name = "base_speed", nullable = false)
    public int baseSpeed;

    @Column(name = "growth_hp", nullable = false)
    public double growthHp;
    @Column(name = "growth_attack", nullable = false)
    public double growthAttack;
    @Column(name = "growth_defense", nullable = false)
    public double growthDefense;
    @Column(name = "growth_speed", nullable = false)
    public double growthSpeed;

    @Column(name = "ability_id", nullable = false)
    public int abilityId;
}
