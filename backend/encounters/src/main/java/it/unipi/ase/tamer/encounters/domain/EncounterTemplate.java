package it.unipi.ase.tamer.encounters.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import it.unipi.ase.tamer.encounters.engine.TemplateInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Stage template (seed data, read-only). Ids: {@code stage × 10 + k}, k = 1..3 normal, k = 4 boss. */
@Entity
@Immutable
@Table(name = "encounter_templates")
public class EncounterTemplate extends PanacheEntityBase {

    @Id
    @Column(name = "template_id")
    public Integer templateId;

    @Column(name = "stage", nullable = false)
    public short stage;

    @Column(name = "name", nullable = false, length = 40)
    public String name;

    @Column(name = "element", nullable = false, length = 10)
    public String element;

    @Column(name = "is_boss", nullable = false)
    public boolean isBoss;

    @Column(name = "boss_monster_id")
    public Integer bossMonsterId;

    @Column(name = "level_min", nullable = false)
    public short levelMin;

    @Column(name = "level_max", nullable = false)
    public short levelMax;

    /** {@code {"common":80,"rare":20,"legendary":0}}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rarity_weights", nullable = false)
    public JsonNode rarityWeights;

    public Map<String, Integer> weights() {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (rarityWeights != null) {
            rarityWeights.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue().asInt()));
        }
        return result;
    }

    public TemplateInfo info() {
        return new TemplateInfo(templateId, name, stage, element, isBoss);
    }
}
