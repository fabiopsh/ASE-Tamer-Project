package it.unipi.ase.tamers.monsters.catalog;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only in-memory view of the catalog (100 monsters, 28 abilities). The data comes from the database,
 * where the seed loader put it, and never changes while the service runs, so it is loaded once on first use.
 */
@ApplicationScoped
public class Catalog {

    public record Snapshot(List<Monster> monsters, Map<Integer, Monster> monsterById,
                           List<Ability> abilities, Map<Integer, Ability> abilityById) {}

    private volatile Snapshot snapshot;

    public Snapshot get() {
        Snapshot s = snapshot;
        if (s == null) {
            synchronized (this) {
                s = snapshot;
                if (s == null) {
                    s = QuarkusTransaction.joiningExisting().call(Catalog::load);
                    snapshot = s;
                }
            }
        }
        return s;
    }

    /** Forgets the cached data, e.g. after the seed loader has (re)filled the tables. */
    public synchronized void invalidate() {
        snapshot = null;
    }

    private static Snapshot load() {
        List<Monster> monsters = Monster.<Monster>list("order by monsterId");
        List<Ability> abilities = Ability.<Ability>list("order by abilityId");
        return new Snapshot(List.copyOf(monsters),
                monsters.stream().collect(Collectors.toUnmodifiableMap(m -> m.monsterId, Function.identity())),
                List.copyOf(abilities),
                abilities.stream().collect(Collectors.toUnmodifiableMap(a -> a.abilityId, Function.identity())));
    }
}
