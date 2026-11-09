package it.unipi.ase.tamer.encounters.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.unipi.ase.tamer.encounters.client.TeamSnapshot;
import it.unipi.ase.tamer.encounters.engine.Battle;
import it.unipi.ase.tamer.encounters.engine.Combatant;
import it.unipi.ase.tamer.encounters.engine.RoundResult;
import it.unipi.ase.tamer.encounters.engine.TemplateInfo;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Builds the JSON documents written to the outbox: the history record ({@code POST /internal/history} body,
 * docs/plan/08-history.md) and its parts. Pure: unit-tested with {@link JsonSupport#standalone()}.
 */
public final class HistoryRecordBuilder {

    /** Limit of {@code turns} accepted by history; longer logs keep their first rounds. */
    public static final int MAX_TURNS = 500;
    public static final int MAX_TEAM = 6;

    private HistoryRecordBuilder() {
    }

    /** History {@code wild}: {@code {monster_id, name, level, rarity, element}}. */
    public static ObjectNode wild(ObjectMapper mapper, Combatant wild) {
        ObjectNode node = mapper.createObjectNode();
        node.put("monster_id", wild.monsterId());
        node.put("name", wild.name());
        node.put("level", wild.level());
        node.put("rarity", wild.rarity());
        node.put("element", wild.element());
        return node;
    }

    /** History {@code team_snapshot}: the members with the stats used in the battle. */
    public static ArrayNode team(ObjectMapper mapper, List<TeamSnapshot.Member> members) {
        ArrayNode array = mapper.createArrayNode();
        for (TeamSnapshot.Member m : members.stream().limit(MAX_TEAM).toList()) {
            ObjectNode node = array.addObject();
            node.put("position", m.position());
            node.put("owned_id", String.valueOf(m.ownedId()));
            node.put("monster_id", m.monsterId());
            node.put("name", m.name());
            node.put("level", m.level());
            node.put("element", m.element());
            node.put("rarity", m.rarity());
            if (m.stats() != null) {
                ObjectNode stats = node.putObject("stats");
                stats.put("hp", m.stats().hp());
                stats.put("attack", m.stats().attack());
                stats.put("defense", m.stats().defense());
                stats.put("speed", m.stats().speed());
            }
            if (m.ability() != null) {
                ObjectNode ability = node.putObject("ability");
                ability.put("ability_id", m.ability().abilityId());
                ability.put("name", m.ability().name());
            }
        }
        return array;
    }

    /** History {@code reward}: {@code {owned_id, monster_id, level}}; {@code owned_id} is set once granted. */
    public static ObjectNode reward(ObjectMapper mapper, int monsterId, int level) {
        ObjectNode node = mapper.createObjectNode();
        node.putNull("owned_id");
        node.put("monster_id", monsterId);
        node.put("level", level);
        return node;
    }

    /**
     * The full record.
     *
     * @param wild    see {@link #wild}
     * @param team    see {@link #team}; may be empty (interrupted battle)
     * @param reward  see {@link #reward}, or {@code null}
     * @param turns   the turn log (empty for an interrupted battle); capped to {@link #MAX_TURNS}
     * @param seed    the battle seed, or {@code null} when unknown
     */
    public static ObjectNode record(ObjectMapper mapper, UUID encounterId, UUID playerId, UUID runId,
                                    TemplateInfo template, JsonNode wild, JsonNode team, String outcome,
                                    JsonNode reward, int xpGained, List<RoundResult> turns, Long seed,
                                    Instant startedAt, Instant endedAt) {
        ObjectNode node = mapper.createObjectNode();
        node.put("encounter_id", encounterId.toString());
        node.put("player_id", playerId.toString());
        node.put("run_id", runId.toString());
        ObjectNode t = node.putObject("template");
        t.put("template_id", template.templateId());
        t.put("name", template.name());
        t.put("stage", template.stage());
        t.put("element", template.element());
        t.put("is_boss", template.isBoss());
        node.set("wild", wild);
        node.set("team_snapshot", team == null ? mapper.createArrayNode() : team);
        node.put("outcome", outcome);
        if (reward == null) {
            node.putNull("reward");
        } else {
            node.set("reward", reward);
        }
        node.put("xp_gained", Math.max(0, xpGained));
        ArrayNode log = node.putArray("turns");
        for (RoundResult round : turns.stream().limit(MAX_TURNS).toList()) {
            log.add(mapper.valueToTree(round));
        }
        if (seed != null) {
            node.put("rng_seed", Long.toString(seed));
        }
        node.put("engine_version", Battle.ENGINE_VERSION);
        node.put("started_at", startedAt.toString());
        node.put("ended_at", (endedAt.isBefore(startedAt) ? startedAt : endedAt).toString());
        return node;
    }
}
