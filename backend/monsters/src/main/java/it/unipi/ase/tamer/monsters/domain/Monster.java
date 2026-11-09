package it.unipi.ase.tamer.monsters.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "monsters")
public class Monster extends PanacheEntityBase {
    @Id
    @Column(name = "monster_id")
    public int monsterId;
    public String name;
    public String element;
    public String rarity;
    @Column(name = "base_hp")
    public int baseHp;
    @Column(name = "base_attack")
    public int baseAttack;
    @Column(name = "base_defense")
    public int baseDefense;
    @Column(name = "base_speed")
    public int baseSpeed;
    @Column(name = "growth_hp")
    public BigDecimal growthHp;
    @Column(name = "growth_attack")
    public BigDecimal growthAttack;
    @Column(name = "growth_defense")
    public BigDecimal growthDefense;
    @Column(name = "growth_speed")
    public BigDecimal growthSpeed;
    @Column(name = "ability_id")
    public int abilityId;
    public String description;
    @Column(name = "image_url")
    public String imageUrl;
}
