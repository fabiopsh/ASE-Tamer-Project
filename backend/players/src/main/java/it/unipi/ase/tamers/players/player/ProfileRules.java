package it.unipi.ase.tamers.players.player;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/** Pure validation of the editable profile fields. */
public final class ProfileRules {
    public static final int NAME_MIN = 3;
    public static final int NAME_MAX = 20;
    private static final Pattern NAME_CHARS = Pattern.compile("^[A-Za-z0-9_ ]+$");

    private ProfileRules() {}

    /** Display names are stored trimmed. */
    public static String normalizeName(String name) {
        return name == null ? null : name.trim();
    }

    /** Validates an already normalized display name; empty list means valid. */
    public static List<String> validateDisplayName(String name) {
        if (name == null) {
            return List.of("display_name must not be null");
        }
        if (name.length() < NAME_MIN || name.length() > NAME_MAX) {
            return List.of("display_name must be " + NAME_MIN + "-" + NAME_MAX + " characters");
        }
        if (!NAME_CHARS.matcher(name).matches()) {
            return List.of("display_name may contain only letters, digits, underscores and spaces");
        }
        return List.of();
    }

    /** The avatar must be a species the player owns. */
    public static List<String> validateAvatar(int monsterId, Collection<Integer> ownedSpeciesIds) {
        if (!ownedSpeciesIds.contains(monsterId)) {
            return List.of("avatar_monster_id " + monsterId + " is not a monster you own");
        }
        return List.of();
    }
}
