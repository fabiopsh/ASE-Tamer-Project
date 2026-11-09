package it.unipi.ase.tamer.encounters.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code {count}} answer of the archive and reset endpoints. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CountResponse(long count) {
}
