package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Versioned catalogue seed. The source JSON is copied from monsters/seed during the build. */
public class V2__seed_catalogue extends BaseJavaMigration {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        JsonNode elements = read("seed/elements.json");
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO elements(element, strong_against, weak_against, cycle_position) VALUES (?, ?, ?, ?)")) {
            for (int i = 0; i < elements.get("cycle").size(); i++) {
                String element = elements.get("cycle").get(i).asText();
                statement.setString(1, element);
                statement.setString(2, elements.get("strong_against").get(element).asText());
                statement.setString(3, elements.get("weak_against").get(element).asText());
                statement.setInt(4, i);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO abilities(ability_id,name,scope,element,effect_type,effect_value,trigger,description) "
                + "VALUES (?,?,?,?,?,?,?,?)")) {
            for (JsonNode item : read("seed/abilities.json")) {
                statement.setInt(1, item.get("ability_id").asInt());
                statement.setString(2, item.get("name").asText());
                statement.setString(3, item.get("scope").asText());
                statement.setString(4, item.get("element").isNull() ? null : item.get("element").asText());
                statement.setString(5, item.get("effect_type").asText());
                statement.setInt(6, item.get("effect_value").asInt());
                statement.setString(7, item.get("trigger").asText());
                statement.setString(8, item.get("description").asText());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO monsters(monster_id,name,element,rarity,base_hp,base_attack,base_defense,base_speed,"
                + "growth_hp,growth_attack,growth_defense,growth_speed,ability_id,description,image_url) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            for (JsonNode item : read("seed/monsters.json")) {
                statement.setInt(1, item.get("monster_id").asInt());
                statement.setString(2, item.get("name").asText());
                statement.setString(3, item.get("element").asText());
                statement.setString(4, item.get("rarity").asText());
                for (int j = 0; j < 4; j++) {
                    statement.setInt(5 + j, item.get(new String[]{"base_hp", "base_attack", "base_defense", "base_speed"}[j]).asInt());
                    statement.setBigDecimal(9 + j, item.get(new String[]{"growth_hp", "growth_attack", "growth_defense", "growth_speed"}[j]).decimalValue());
                }
                statement.setInt(13, item.get("ability_id").asInt());
                statement.setString(14, item.get("description").asText());
                statement.setString(15, item.get("image_url").asText());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private JsonNode read(String path) throws Exception {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing catalogue seed: " + path);
            }
            return mapper.readTree(stream);
        }
    }
}
