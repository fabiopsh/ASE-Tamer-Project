package it.unipi.ase.tamers.teams.team;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Pure team rules: no CDI, no database, no HTTP. Empty result means valid. */
public final class TeamRules {
    public static final int MAX_NAME_LENGTH = 30;
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 3;
    public static final int MAX_TEAMS = 5;

    private TeamRules() {}

    /** Full validation: name, size, duplicates and ownership (ownedByPlayer = ids the player owns). */
    public static List<String> validate(String name, List<UUID> ownedIds, Set<UUID> ownedByPlayer) {
        List<String> errors = new ArrayList<>(validateName(name));
        errors.addAll(validateMembers(ownedIds, ownedByPlayer));
        return errors;
    }

    /** Name must be 1..30 characters once trimmed. */
    public static List<String> validateName(String name) {
        if (name == null) {
            return List.of("name is required");
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return List.of("name must not be blank");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            return List.of("name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        return List.of();
    }

    /** Size, duplicates and ownership of the monsters, without the name check. */
    public static List<String> validateMembers(List<UUID> ownedIds, Set<UUID> ownedByPlayer) {
        List<UUID> ids = ownedIds == null ? List.of() : ownedIds;
        Set<UUID> owned = ownedByPlayer == null ? Set.of() : ownedByPlayer;
        List<String> errors = new ArrayList<>();

        if (ids.size() < MIN_SIZE || ids.size() > MAX_SIZE) {
            errors.add("a team needs between " + MIN_SIZE + " and " + MAX_SIZE + " monsters");
        }

        Set<UUID> distinct = new LinkedHashSet<>();
        Set<UUID> duplicates = new LinkedHashSet<>();
        boolean hasNull = false;
        for (UUID id : ids) {
            if (id == null) {
                hasNull = true;
            } else if (!distinct.add(id)) {
                duplicates.add(id);
            }
        }
        if (hasNull) {
            errors.add("monster id is required");
        }
        for (UUID id : duplicates) {
            errors.add("duplicate monster " + id);
        }
        for (UUID id : distinct) {
            if (!owned.contains(id)) {
                errors.add("monster " + id + " is not in your collection");
            }
        }
        return errors;
    }

    /** Checked only when a player creates a team: the limit is MAX_TEAMS. */
    public static List<String> validateCreation(long existingTeams) {
        if (existingTeams >= MAX_TEAMS) {
            return List.of("a player can have at most " + MAX_TEAMS + " teams");
        }
        return List.of();
    }

    /** Stored name: trimmed. Call only after validateName succeeded. */
    public static String normalizeName(String name) {
        return name.trim();
    }
}
