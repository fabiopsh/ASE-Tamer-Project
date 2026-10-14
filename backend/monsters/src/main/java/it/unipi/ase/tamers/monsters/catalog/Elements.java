package it.unipi.ase.tamers.monsters.catalog;

import it.unipi.ase.tamers.monsters.catalog.SeedData.ElementCycle;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

/** In-memory element cycle, loaded from {@code seed/elements.json}. */
@ApplicationScoped
public class Elements {
    private ElementCycle cycle;

    @PostConstruct
    void load() {
        cycle = SeedData.readElements(SeedData.open(SeedData.ELEMENTS_RESOURCE));
    }

    public ElementCycle cycle() {
        return cycle;
    }

    public String strongAgainst(String element) {
        return cycle.strongOf(element);
    }

    public String weakAgainst(String element) {
        return cycle.weakOf(element);
    }
}
