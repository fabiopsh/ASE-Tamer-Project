package it.unipi.ase.tamer.auth.service;

/** A signed access token with its lifetime in seconds. */
public record IssuedToken(String value, long expiresIn, String scope) {
}
