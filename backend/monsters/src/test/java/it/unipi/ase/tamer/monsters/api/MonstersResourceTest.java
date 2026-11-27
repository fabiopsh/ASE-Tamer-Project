package it.unipi.ase.tamer.monsters.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import it.unipi.ase.tamer.monsters.api.dto.ArchiveRequest;
import it.unipi.ase.tamer.monsters.api.dto.StarterGrantRequest;
import it.unipi.ase.tamer.monsters.domain.OwnedMonster;
import it.unipi.ase.tamer.monsters.domain.XpGrant;
import it.unipi.ase.tamer.monsters.service.CollectionService;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class MonstersResourceTest {

    private static final String PLAYER_ID = "22222222-2222-4222-8222-222222222222";
    private static final UUID OTHER_PLAYER_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Inject
    CollectionService collectionService;

    @BeforeEach
    void cleanOwnedData() {
        QuarkusTransaction.requiringNew().run(() -> {
            XpGrant.deleteAll();
            OwnedMonster.deleteAll();
        });
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void catalogueSeedPaginationFiltersAndDetails() {
        // V2 seed: 100 monsters, 28 abilities, 7 elements
        given().queryParam("page", 0).queryParam("size", 10)
                .get("/api/monsters")
                .then().statusCode(200)
                .body("total_items", equalTo(100))
                .body("total_pages", equalTo(10))
                .body("items", hasSize(10));

        given().get("/api/abilities")
                .then().statusCode(200)
                .body("$", hasSize(28));

        given().get("/api/abilities/1")
                .then().statusCode(200)
                .body("ability_id", equalTo(1));

        given().get("/api/abilities/9999")
                .then().statusCode(404)
                .body("error", equalTo("NOT_FOUND"));

        given().get("/api/elements")
                .then().statusCode(200)
                .body("cycle", hasSize(7));

        // Element & rarity filters
        given().queryParam("element", "dream").queryParam("rarity", "common").queryParam("size", 50)
                .get("/api/monsters")
                .then().statusCode(200)
                .body("total_items", greaterThan(0))
                .body("items.element", everyItem(equalTo("dream")))
                .body("items.rarity", everyItem(equalTo("common")));

        // Invalid filters -> 400
        given().queryParam("element", "fire")
                .get("/api/monsters")
                .then().statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"));

        given().queryParam("rarity", "mythic")
                .get("/api/monsters")
                .then().statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"));

        // ids filter
        given().queryParam("ids", "1,2,3")
                .get("/api/monsters")
                .then().statusCode(200)
                .body("total_items", equalTo(3))
                .body("items.monster_id", hasItems(1, 2, 3));

        // Detail valid & 404
        given().get("/api/monsters/1")
                .then().statusCode(200)
                .body("monster_id", equalTo(1))
                .body("name", equalTo("Snoozle"))
                .body("base_stats.hp", equalTo(38))
                .body("ability.ability_id", equalTo(3));

        given().get("/api/monsters/9999")
                .then().statusCode(404)
                .body("error", equalTo("NOT_FOUND"));
    }

    @Test
    void unauthenticatedCatalogueReturns401WithBearerChallenge() {
        given().get("/api/monsters")
                .then().statusCode(401)
                .header("WWW-Authenticate", containsString("Bearer"))
                .body("error", equalTo("UNAUTHORIZED"));
    }

    @Test
    void spritesArePublicCacheableAndValidateFilename() {
        byte[] bytes = given().get("/images/monsters/001.png")
                .then().statusCode(200)
                .header("Content-Type", containsString("image/png"))
                .header("Cache-Control", containsString("max-age=86400"))
                .extract().asByteArray();
        assertTrue(bytes.length > 0);

        given().head("/images/monsters/001.png")
                .then().statusCode(200)
                .header("Content-Type", containsString("image/png"))
                .header("Cache-Control", containsString("max-age=86400"));

        given().get("/images/monsters/999.png")
                .then().statusCode(404);

        given().get("/images/monsters/invalid.png")
                .then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "encounters", roles = "service")
    void internalCollectionOperationsAreIdempotentAndValidateErrors() {
        UUID pid = UUID.fromString(PLAYER_ID);
        UUID runId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        UUID xpEncounterId = UUID.randomUUID();

        // Grant starters (201 then 200 idempotent)
        String starterBody = "{\"run_id\": \"" + runId + "\", \"monster_ids\": [1, 2, 3]}";
        List<String> starterIds = given().contentType(ContentType.JSON).body(starterBody)
                .post("/internal/collections/" + pid + "/starters")
                .then().statusCode(201)
                .body("$", hasSize(3))
                .extract().jsonPath().getList("owned_id", String.class);

        List<String> repeatedStarterIds = given().contentType(ContentType.JSON).body(starterBody)
                .post("/internal/collections/" + pid + "/starters")
                .then().statusCode(200)
                .extract().jsonPath().getList("owned_id", String.class);
        assertEquals(starterIds, repeatedStarterIds);

        // Unknown monster id in starters -> 400 (not 404)
        given().contentType(ContentType.JSON)
                .body("{\"run_id\": \"" + UUID.randomUUID() + "\", \"monster_ids\": [1, 2, 9999]}")
                .post("/internal/collections/" + pid + "/starters")
                .then().statusCode(400);

        // Grant reward (201 then 200 idempotent on encounter_id)
        String rewardBody = "{\"run_id\": \"" + runId + "\", \"monster_id\": 4, \"level\": 3, "
                + "\"encounter_id\": \"" + encounterId + "\", \"source\": \"encounter\"}";
        String rewardOwnedId = given().contentType(ContentType.JSON).body(rewardBody)
                .post("/internal/collections/" + pid + "/monsters")
                .then().statusCode(201)
                .body("monster_id", equalTo(4))
                .body("level", equalTo(3))
                .extract().path("owned_id");

        String repeatedRewardId = given().contentType(ContentType.JSON).body(rewardBody)
                .post("/internal/collections/" + pid + "/monsters")
                .then().statusCode(200)
                .extract().path("owned_id");
        assertEquals(rewardOwnedId, repeatedRewardId);

        // Grant XP (levels up, idempotent on encounter_id)
        String targetOwned = starterIds.getFirst();
        String xpBody = "{\"encounter_id\": \"" + xpEncounterId + "\", \"owned_ids\": [\"" + targetOwned + "\"], \"xp\": 200}";
        int levelAfter = given().contentType(ContentType.JSON).body(xpBody)
                .post("/internal/collections/" + pid + "/xp")
                .then().statusCode(200)
                .body("[0].level_before", equalTo(1))
                .body("[0].level_after", greaterThan(1))
                .extract().path("[0].level_after");

        given().contentType(ContentType.JSON).body(xpBody)
                .post("/internal/collections/" + pid + "/xp")
                .then().statusCode(200)
                .body("[0].level_before", equalTo(levelAfter))
                .body("[0].level_after", equalTo(levelAfter));

        // Instances batch lookup & validation error codes
        given().queryParam("ids", targetOwned + "," + rewardOwnedId)
                .get("/internal/collections/" + pid + "/instances")
                .then().statusCode(200)
                .body("$", hasSize(2));

        given().queryParam("ids", "not-a-uuid")
                .get("/internal/collections/" + pid + "/instances")
                .then().statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"));

        String fiftyOneIds = IntStream.range(0, 51)
                .mapToObj(i -> UUID.randomUUID().toString())
                .collect(Collectors.joining(","));
        given().queryParam("ids", fiftyOneIds)
                .get("/internal/collections/" + pid + "/instances")
                .then().statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"));

        // Archive (4 active -> 4, repeat -> 0)
        String archiveBody = "{\"run_id\": \"" + runId + "\"}";
        given().contentType(ContentType.JSON).body(archiveBody)
                .post("/internal/collections/" + pid + "/archive")
                .then().statusCode(200)
                .body("count", equalTo(4));

        given().contentType(ContentType.JSON).body(archiveBody)
                .post("/internal/collections/" + pid + "/archive")
                .then().statusCode(200)
                .body("count", equalTo(0));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void playerCollectionViewsAndNicknamePatch() {
        UUID pid = UUID.fromString(PLAYER_ID);
        UUID runId = UUID.randomUUID();
        var ownGrant = collectionService.starters(pid, new StarterGrantRequest(runId, List.of(1, 2, 3)));
        var otherGrant = collectionService.starters(OTHER_PLAYER_ID,
                new StarterGrantRequest(UUID.randomUUID(), List.of(1, 2, 3)));
        UUID ownId = ownGrant.starters().getFirst().ownedId();
        UUID foreignId = otherGrant.starters().getFirst().ownedId();

        // Active collection & own vs foreign detail
        given().get("/api/collection")
                .then().statusCode(200)
                .body("total_items", equalTo(3))
                .body("items", hasSize(3));

        given().get("/api/collection/" + ownId)
                .then().statusCode(200)
                .body("owned_id", equalTo(ownId.toString()))
                .body("nickname", nullValue());

        given().get("/api/collection/" + foreignId)
                .then().statusCode(404)
                .body("error", equalTo("NOT_FOUND"));

        // PATCH nickname: set, clear with blank, set, clear with null
        given().contentType(ContentType.JSON)
                .body("{\"nickname\": \"  Snoozle's-1  \"}")
                .patch("/api/collection/" + ownId)
                .then().statusCode(200)
                .body("nickname", equalTo("Snoozle's-1"));

        given().contentType(ContentType.JSON)
                .body("{\"nickname\": \"   \"}")
                .patch("/api/collection/" + ownId)
                .then().statusCode(200)
                .body("nickname", nullValue());

        given().contentType(ContentType.JSON)
                .body("{\"nickname\": \"Buddy\"}")
                .patch("/api/collection/" + ownId)
                .then().statusCode(200)
                .body("nickname", equalTo("Buddy"));

        given().contentType(ContentType.JSON)
                .body("{\"nickname\": null}")
                .patch("/api/collection/" + ownId)
                .then().statusCode(200)
                .body("nickname", nullValue());

        // Invalid nicknames -> 400
        given().contentType(ContentType.JSON)
                .body("{\"nickname\": \"bad!@#\"}")
                .patch("/api/collection/" + ownId)
                .then().statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"));

        given().contentType(ContentType.JSON)
                .body("{\"nickname\": \"" + "a".repeat(31) + "\"}")
                .patch("/api/collection/" + ownId)
                .then().statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"));

        // Foreign monster -> 404
        given().contentType(ContentType.JSON)
                .body("{\"nickname\": \"Stolen\"}")
                .patch("/api/collection/" + foreignId)
                .then().statusCode(404)
                .body("error", equalTo("NOT_FOUND"));

        // Archive run and verify active vs run_id listing
        collectionService.archive(pid, new ArchiveRequest(runId));

        given().get("/api/collection")
                .then().statusCode(200)
                .body("total_items", equalTo(0));

        given().queryParam("run_id", runId.toString())
                .get("/api/collection")
                .then().statusCode(200)
                .body("total_items", equalTo(3))
                .body("items.status", everyItem(equalTo("archived")));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void playerTokenOnInternalEndpointsIs403() {
        given().get("/internal/monsters")
                .then().statusCode(403)
                .body("error", equalTo("FORBIDDEN"));

        given().get("/internal/collections/" + PLAYER_ID + "/instances")
                .then().statusCode(403)
                .body("error", equalTo("FORBIDDEN"));
    }

    @Test
    @TestSecurity(user = "players", roles = "service")
    void serviceTokenOnPublicEndpointsIs403() {
        given().get("/api/monsters")
                .then().statusCode(403)
                .body("error", equalTo("FORBIDDEN"));

        given().get("/api/collection")
                .then().statusCode(403)
                .body("error", equalTo("FORBIDDEN"));
    }
}
