package it.unipi.ase.tamers.monsters.catalog;

import it.unipi.ase.tamers.monsters.common.ApiException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Pure parsing of query-string filters; every invalid value becomes a 400. */
public final class Filters {
    public static final int MAX_IDS = 200;

    private Filters() {}

    /** Blank/absent means "no filter" (null); otherwise the value must be one of the enum constants. */
    public static <E extends Enum<E>> E parseEnum(Class<E> type, String param, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String wanted = raw.trim().toUpperCase(Locale.ROOT);
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(wanted)) {
                return constant;
            }
        }
        String allowed = Arrays.stream(type.getEnumConstants())
                .map(c -> c.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "));
        throw ApiException.badRequest("invalid " + param + " '" + raw.trim() + "', allowed values: " + allowed);
    }

    public static Element element(String raw) {
        return parseEnum(Element.class, "element", raw);
    }

    public static Rarity rarity(String raw) {
        return parseEnum(Rarity.class, "rarity", raw);
    }

    /** Comma-separated integers, e.g. {@code 1,2,3}. Null when the parameter is absent/blank. */
    public static Set<Integer> intIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Set<Integer> ids = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            if (token.isBlank()) {
                continue;
            }
            try {
                ids.add(Integer.parseInt(token.trim()));
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("invalid id '" + token.trim() + "' in ids");
            }
        }
        checkSize(ids.size());
        return ids;
    }

    /** Comma-separated UUIDs, order preserved, duplicates dropped. Empty set when absent/blank. */
    public static Set<UUID> uuidIds(String raw) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return ids;
        }
        for (String token : raw.split(",")) {
            if (!token.isBlank()) {
                ids.add(uuid(token));
            }
        }
        checkSize(ids.size());
        return ids;
    }

    public static UUID uuid(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.badRequest("invalid id '" + raw + "', a UUID is expected");
        }
    }

    public static int intId(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw ApiException.badRequest("invalid id '" + raw + "', an integer is expected");
        }
    }

    private static void checkSize(int size) {
        if (size > MAX_IDS) {
            throw ApiException.badRequest("too many ids (max " + MAX_IDS + ")");
        }
    }
}
