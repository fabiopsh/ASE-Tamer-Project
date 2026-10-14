package it.unipi.ase.tamers.monsters.collection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Pure helpers for collection queries. */
public final class CollectionRules {
    private CollectionRules() {}

    /**
     * Keeps only the owned monsters whose id was requested and that belong to {@code playerId},
     * in the order of the request.
     */
    public static List<OwnedMonster> ownedInRequestedOrder(UUID playerId, Collection<UUID> requested,
                                                           Collection<OwnedMonster> candidates) {
        Map<UUID, OwnedMonster> byId = new HashMap<>();
        for (OwnedMonster o : candidates) {
            if (playerId.equals(o.playerId)) {
                byId.put(o.ownedId, o);
            }
        }
        List<OwnedMonster> result = new ArrayList<>();
        for (UUID id : requested) {
            OwnedMonster o = byId.get(id);
            if (o != null) {
                result.add(o);
            }
        }
        return result;
    }

    public static int speciesOwned(Collection<OwnedMonster> owned) {
        return owned.stream().map(o -> o.monsterId).collect(Collectors.toSet()).size();
    }
}
