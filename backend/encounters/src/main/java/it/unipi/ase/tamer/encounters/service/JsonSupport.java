package it.unipi.ase.tamer.encounters.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import it.unipi.ase.tamer.encounters.engine.BattleEvent;

/**
 * JSON settings shared by the HTTP layer and the payloads stored in the outbox: snake_case, and engine events
 * without the fields that do not belong to their type ({@code NON_NULL}, without touching the engine classes).
 */
public final class JsonSupport {

    private JsonSupport() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract static class NonNullMixin {
    }

    /** Adds the encounters-specific settings to an existing mapper (the Quarkus one or a test one). */
    public static ObjectMapper configure(ObjectMapper mapper) {
        return mapper.addMixIn(BattleEvent.class, NonNullMixin.class);
    }

    /** A standalone mapper equivalent to the Quarkus one, for unit tests. */
    public static ObjectMapper standalone() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return configure(mapper);
    }
}
