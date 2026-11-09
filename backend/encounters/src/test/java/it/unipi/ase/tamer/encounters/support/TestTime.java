package it.unipi.ase.tamer.encounters.support;

import it.unipi.ase.tamer.encounters.service.TimeSource;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.time.Duration;
import java.time.Instant;

/** A clock that tests can move forward (battle expiry). */
@Alternative
@Priority(1)
@ApplicationScoped
public class TestTime extends TimeSource {

    private static volatile Duration offset = Duration.ZERO;

    public static void reset() {
        offset = Duration.ZERO;
    }

    public static void advance(Duration amount) {
        offset = offset.plus(amount);
    }

    @Override
    public Instant now() {
        return super.now().plus(offset);
    }
}
