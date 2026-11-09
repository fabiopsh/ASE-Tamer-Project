package it.unipi.ase.tamer.encounters.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Sort;
import it.unipi.ase.tamer.encounters.domain.EncounterTemplate;
import it.unipi.ase.tamer.encounters.domain.Run;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which templates a run can play (US-17): non-boss templates with {@code stage ≤ run.stage}, plus the boss of
 * {@code run.stage} once unlocked ({@link RunRules#available}). Templates are seed data and are read once.
 */
@ApplicationScoped
public class AvailabilityService {

    private volatile List<EncounterTemplate> templates;

    /** All templates ordered by stage then id (read-only, detached). */
    public List<EncounterTemplate> templates() {
        List<EncounterTemplate> current = templates;
        if (current == null) {
            current = QuarkusTransaction.requiringNew().call(() -> List.copyOf(
                    EncounterTemplate.<EncounterTemplate>listAll(Sort.by("stage").and("templateId"))));
            templates = current;
        }
        return current;
    }

    public List<EncounterTemplate> available(Run run) {
        RunRules.State state = run.state();
        return templates().stream().filter(t -> RunRules.available(state, t.stage, t.isBoss)).toList();
    }

    public Optional<EncounterTemplate> availableTemplate(Run run, int templateId) {
        return available(run).stream().filter(t -> t.templateId == templateId).findFirst();
    }

    /** Species of the 8 bosses: never drawn as normal wild monsters. */
    public Set<Integer> bossMonsterIds() {
        Set<Integer> ids = new TreeSet<>();
        for (EncounterTemplate t : templates()) {
            if (t.isBoss && t.bossMonsterId != null) {
                ids.add(t.bossMonsterId);
            }
        }
        return ids;
    }

    public Optional<EncounterTemplate> template(int templateId) {
        return templates().stream().filter(t -> t.templateId == templateId).findFirst();
    }
}
