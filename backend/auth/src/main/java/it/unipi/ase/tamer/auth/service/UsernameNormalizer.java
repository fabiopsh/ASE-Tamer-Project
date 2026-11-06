package it.unipi.ase.tamer.auth.service;

import java.util.Locale;

/** Usernames are unique case-insensitively: the same normalisation is used at registration and login. */
public final class UsernameNormalizer {

    private UsernameNormalizer() {
    }

    public static String normalize(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }
}
