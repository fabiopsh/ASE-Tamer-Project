package it.unipi.ase.tamer.encounters.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.jackson.ObjectMapperCustomizer;
import it.unipi.ase.tamer.encounters.service.JsonSupport;
import jakarta.inject.Singleton;

/** Applies {@link JsonSupport} to the Quarkus ObjectMapper (responses, REST clients, outbox payloads). */
@Singleton
public class JacksonConfig implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper mapper) {
        JsonSupport.configure(mapper);
    }
}
