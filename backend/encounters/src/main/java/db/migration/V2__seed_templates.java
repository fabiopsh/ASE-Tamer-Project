package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Stage templates seed (EN-1). The source JSON is copied from {@code backend/encounters/seed} into the
 * classpath during the build (see the {@code copy-seed} execution in the module pom).
 */
public class V2__seed_templates extends BaseJavaMigration {

    private static final String SEED = "seed/encounter_templates.json";

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        JsonNode templates = read();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO encounter_templates(template_id, stage, name, element, is_boss, boss_monster_id, "
                + "level_min, level_max, rarity_weights) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))")) {
            for (JsonNode item : templates) {
                statement.setInt(1, item.get("template_id").asInt());
                statement.setInt(2, item.get("stage").asInt());
                statement.setString(3, item.get("name").asText());
                statement.setString(4, item.get("element").asText());
                statement.setBoolean(5, item.get("is_boss").asBoolean());
                JsonNode boss = item.get("boss_monster_id");
                if (boss == null || boss.isNull()) {
                    statement.setNull(6, Types.INTEGER);
                } else {
                    statement.setInt(6, boss.asInt());
                }
                statement.setInt(7, item.get("level_min").asInt());
                statement.setInt(8, item.get("level_max").asInt());
                statement.setString(9, mapper.writeValueAsString(item.get("rarity_weights")));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private JsonNode read() throws Exception {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(SEED)) {
            if (stream == null) {
                throw new IllegalStateException("Missing stage templates seed: " + SEED);
            }
            return mapper.readTree(stream);
        }
    }
}
