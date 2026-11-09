package it.unipi.ase.tamer.teams.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Response of the {@code auth} token endpoint. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenResponse(String accessToken, String tokenType, long expiresIn, String scope) {
}
