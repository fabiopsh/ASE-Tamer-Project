package it.unipi.ase.tamer.encounters.support;

import it.unipi.ase.tamer.encounters.service.SeedSource;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/** Deterministic battle seed for every {@code @QuarkusTest}. */
@Alternative
@Priority(1)
@ApplicationScoped
public class FixedSeedSource extends SeedSource {

    public static volatile long seed = 42L;

    @Override
    public long next() {
        return seed;
    }
}
