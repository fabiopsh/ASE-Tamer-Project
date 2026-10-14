package it.unipi.ase.tamers.monsters.catalog;

import io.quarkus.runtime.StartupEvent;
import it.unipi.ase.tamers.monsters.catalog.SeedData.SeedAbility;
import it.unipi.ase.tamers.monsters.catalog.SeedData.SeedMonster;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import org.jboss.logging.Logger;

/** On startup fills the abilities and monsters tables from the classpath seed when they are empty. */
@ApplicationScoped
public class SeedLoader {
    private static final Logger LOG = Logger.getLogger(SeedLoader.class);

    @Inject
    Elements elements;
    @Inject
    Catalog catalog;

    @Transactional
    void onStart(@Observes StartupEvent event) {
        List<SeedAbility> abilities = SeedData.readAbilities(SeedData.open(SeedData.ABILITIES_RESOURCE));
        List<SeedMonster> monsters = SeedData.readMonsters(SeedData.open(SeedData.MONSTERS_RESOURCE));
        List<String> problems = SeedData.validate(monsters, abilities, elements.cycle());
        if (!problems.isEmpty()) {
            throw new IllegalStateException("inconsistent seed data: " + problems);
        }
        if (Ability.count() == 0) {
            abilities.stream().map(SeedLoader::toEntity).forEach(a -> a.persist());
            LOG.infof("seeded %d abilities", abilities.size());
        }
        if (Monster.count() == 0) {
            monsters.stream().map(SeedLoader::toEntity).forEach(m -> m.persist());
            LOG.infof("seeded %d monsters", monsters.size());
        }
        catalog.invalidate();
    }

    static Ability toEntity(SeedAbility s) {
        Ability a = new Ability();
        a.abilityId = s.abilityId();
        a.name = s.name();
        a.scope = s.scope();
        a.element = s.element();
        a.effectType = s.effectType();
        a.effectValue = s.effectValue();
        a.trigger = s.trigger();
        a.description = s.description();
        return a;
    }

    static Monster toEntity(SeedMonster s) {
        Monster m = new Monster();
        m.monsterId = s.monsterId();
        m.name = s.name();
        m.element = s.element();
        m.rarity = s.rarity();
        m.description = s.description();
        m.imageUrl = s.imageUrl();
        m.baseHp = s.baseHp();
        m.baseAttack = s.baseAttack();
        m.baseDefense = s.baseDefense();
        m.baseSpeed = s.baseSpeed();
        m.growthHp = s.growthHp();
        m.growthAttack = s.growthAttack();
        m.growthDefense = s.growthDefense();
        m.growthSpeed = s.growthSpeed();
        m.abilityId = s.abilityId();
        return m;
    }
}
