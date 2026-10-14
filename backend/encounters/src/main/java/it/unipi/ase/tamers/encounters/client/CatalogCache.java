package it.unipi.ase.tamers.encounters.client;

import it.unipi.ase.tamers.encounters.client.ClientDtos.MonsterDetail;
import it.unipi.ase.tamers.encounters.common.ApiException;
import it.unipi.ase.tamers.encounters.engine.BoardGenerator.CatalogEntry;
import it.unipi.ase.tamers.encounters.engine.ElementChart;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/** The whole monster catalog (100 entries), loaded once from the monsters service and kept in memory. */
@ApplicationScoped
public class CatalogCache {
    private static final Logger LOG = Logger.getLogger(CatalogCache.class);

    @Inject
    @RestClient
    MonstersClient monsters;

    private volatile Catalog cached;

    public Catalog get() {
        Catalog c = cached;
        if (c == null) {
            synchronized (this) {
                c = cached;
                if (c == null) {
                    c = load();
                    cached = c;
                }
            }
        }
        return c;
    }

    private Catalog load() {
        try {
            List<MonsterDetail> all = monsters.allMonsters();
            if (all == null || all.isEmpty()) {
                throw new IllegalStateException("empty catalog");
            }
            return new Catalog(all);
        } catch (RuntimeException e) {
            LOG.errorf("monsters catalog unavailable: %s", e.toString());
            throw ApiException.upstream("monsters service unavailable");
        }
    }

    public static final class Catalog {
        private final Map<Integer, MonsterDetail> byId = new HashMap<>();
        private final List<CatalogEntry> entries;
        private final ElementChart chart;

        public Catalog(List<MonsterDetail> all) {
            all.forEach(m -> byId.put(m.monsterId(), m));
            this.entries = all.stream().map(m -> new CatalogEntry(m.monsterId(), m.rarity(), m.element(), m.baseAttack())).toList();
            Map<String, String> strong = new HashMap<>();
            Map<String, String> weak = new HashMap<>();
            for (MonsterDetail m : all) {
                if (m.strongAgainst() != null) {
                    strong.put(m.element(), m.strongAgainst());
                }
                if (m.weakAgainst() != null) {
                    weak.put(m.element(), m.weakAgainst());
                }
            }
            this.chart = strong.isEmpty() ? ElementChart.DEFAULT : new ElementChart(strong, weak);
        }

        public MonsterDetail monster(int monsterId) {
            MonsterDetail m = byId.get(monsterId);
            if (m == null) {
                throw ApiException.upstream("monster " + monsterId + " missing from the catalog");
            }
            return m;
        }

        public List<CatalogEntry> entries() { return entries; }

        public ElementChart chart() { return chart; }
    }
}
