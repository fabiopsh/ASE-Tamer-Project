package it.unipi.ase.tamer.encounters.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Current time (microsecond precision, like {@code timestamptz}). A CDI bean so that tests can move it. */
@ApplicationScoped
public class TimeSource {

    public Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
