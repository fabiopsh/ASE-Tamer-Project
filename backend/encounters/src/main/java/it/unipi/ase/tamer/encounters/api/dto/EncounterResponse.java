package it.unipi.ase.tamer.encounters.api.dto;

import it.unipi.ase.tamer.encounters.domain.EncounterTemplate;
import java.util.Map;

/**
 * A stage template available in the active run (US-17). {@code boss_monster_id} is {@code null} for a normal
 * template; for a boss {@code level_min = level_max} = boss level. {@code rarity_weights} are the weights of the
 * wild rarity draw (common / rare / legendary).
 */
public record EncounterResponse(int templateId, String name, int stage, String element, boolean isBoss,
                                Integer bossMonsterId, int levelMin, int levelMax,
                                Map<String, Integer> rarityWeights) {

    public static EncounterResponse from(EncounterTemplate t) {
        return new EncounterResponse(t.templateId, t.name, t.stage, t.element, t.isBoss, t.bossMonsterId,
                t.levelMin, t.levelMax, t.weights());
    }
}
