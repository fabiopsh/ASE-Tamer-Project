package it.unipi.ase.tamers.history.record;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.unipi.ase.tamers.history.record.HistoryDtos.HistoryDetail;
import it.unipi.ase.tamers.history.record.HistoryDtos.HistoryPage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the snake_case JSON produced for the API contract (same naming strategy as application.properties). */
class HistoryJsonShapeTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void detailIsSummaryFlattenedWithResultHashAndPrevHash() throws Exception {
        JsonNode result = EncounterPayload.parse(EncounterPayloadTest.WON_WITH_CAPTURE);
        HistoryDetail detail = new HistoryDetail(
                EncounterPayload.summary(4, result), result, "a".repeat(64), "b".repeat(64));

        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(detail));

        assertEquals(EncounterPayloadTest.RUN, json.get("run_id").asText());
        assertEquals(4, json.get("sequence").asLong());
        assertEquals("victory", json.get("outcome").asText());
        assertEquals(7, json.get("rounds").asInt());
        assertEquals(12, json.get("captured").get("monster_id").asInt());
        assertEquals("/images/monsters/005.png", json.get("enemy").get("image_url").asText());
        assertEquals("2026-10-14T10:15:30Z", json.get("occurred_at").asText());
        assertEquals("a".repeat(64), json.get("hash").asText());
        assertEquals("b".repeat(64), json.get("prev_hash").asText());
        assertEquals("wild", json.get("result").get("kind").asText());
        assertFalse(json.has("summary"), "summary must be unwrapped");
    }

    @Test
    void summaryWithoutCaptureSerializesNullCaptured() throws Exception {
        JsonNode result = EncounterPayload.parse(EncounterPayloadTest.LOST_NO_CAPTURE);
        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(
                new HistoryDetail(EncounterPayload.summary(1, result), result, "h", "GENESIS")));
        assertTrue(json.has("captured"));
        assertTrue(json.get("captured").isNull());
    }

    @Test
    void pageUsesContractFieldNames() throws Exception {
        HistoryPage page = new HistoryPage(List.of(), 0, 20, 0, 0);
        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(page));
        assertEquals(0, json.get("page").asInt());
        assertEquals(20, json.get("size").asInt());
        assertEquals(0, json.get("total").asInt());
        assertEquals(0, json.get("total_pages").asInt());
        assertTrue(json.get("items").isArray());
    }
}
