package it.unipi.ase.tamer.encounters.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;

/** Seed of a new battle ({@code SecureRandom.nextLong()}). A CDI bean so that tests can fix it. */
@ApplicationScoped
public class SeedSource {

    private final SecureRandom random = new SecureRandom();

    public long next() {
        return random.nextLong();
    }
}
