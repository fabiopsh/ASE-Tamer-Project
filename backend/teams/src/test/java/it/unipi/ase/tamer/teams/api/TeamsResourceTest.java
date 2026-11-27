package it.unipi.ase.tamer.teams.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import it.unipi.ase.tamer.teams.api.dto.TeamRequest;
import it.unipi.ase.tamer.teams.client.MonsterInstance;
import it.unipi.ase.tamer.teams.client.MonstersClient;
import it.unipi.ase.tamer.teams.domain.Team;
import it.unipi.ase.tamer.teams.domain.TeamSlot;
import it.unipi.ase.tamer.teams.service.TeamsService;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TeamsResourceTest {

    private static final String PLAYER_ID = "44444444-4444-4444-8444-444444444444";
    private static final String OTHER_PLAYER_ID = "55555555-5555-4555-8555-555555555555";

    private static final UUID RUN_1 = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID MONSTER_1 = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID MONSTER_2 = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID MONSTER_3 = UUID.fromString("10000000-0000-4000-8000-000000000003");
    private static final UUID FOREIGN_MONSTER = UUID.fromString("99999999-9999-4999-8999-999999999999");

    @Inject
    TeamsService teamsService;

    @InjectMock
    @RestClient
    MonstersClient monsters;

    @BeforeEach
    void cleanTeams() {
        QuarkusTransaction.requiringNew().run(() -> {
            TeamSlot.deleteAll();
            Team.deleteAll();
        });
    }

    private void stubOwnedInstances(UUID playerId, MonsterInstance... instances) {
        Map<UUID, MonsterInstance> byId = new HashMap<>();
        for (MonsterInstance inst : instances) {
            byId.put(inst.ownedId(), inst);
        }
        when(monsters.instances(eq(playerId), anyString())).thenAnswer(invocation -> {
            String csv = invocation.getArgument(1, String.class);
            List<MonsterInstance> result = new ArrayList<>();
            for (String part : csv.split(",")) {
                MonsterInstance found = byId.get(UUID.fromString(part));
                if (found != null) {
                    result.add(found);
                }
            }
            return result;
        });
    }

    private static MonsterInstance instance(UUID ownedId, int monsterId, String name, UUID runId, String status) {
        return new MonsterInstance(ownedId, monsterId, name, "dream", "common",
                "/images/monsters/00" + monsterId + ".png", 5, runId, status,
                new MonsterInstance.Stats(50, 30, 25, 20),
                new MonsterInstance.Ability(1, "Thick Hide", "Reduces damage", "damage_reduction", 10, "always"));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void crudRenameReorderAndBatchListFetch() {
        UUID pid = UUID.fromString(PLAYER_ID);
        stubOwnedInstances(pid,
                instance(MONSTER_1, 1, "Snoozle", RUN_1, "active"),
                instance(MONSTER_2, 2, "Pillowpup", RUN_1, "active"),
                instance(MONSTER_3, 3, "Moonmote", RUN_1, "active"));

        String team1Id = given().contentType(ContentType.JSON)
                .body("{\"name\": \"Alpha\", \"members\": [\"" + MONSTER_1 + "\", \"" + MONSTER_2 + "\"]}")
                .post("/api/teams")
                .then().statusCode(201)
                .body("name", equalTo("Alpha"))
                .body("members", hasSize(2))
                .body("members[0].owned_id", equalTo(MONSTER_1.toString()))
                .body("members[1].owned_id", equalTo(MONSTER_2.toString()))
                .extract().path("team_id");

        given().contentType(ContentType.JSON)
                .body("{\"name\": \"Beta\", \"members\": [\"" + MONSTER_2 + "\", \"" + MONSTER_3 + "\"]}")
                .post("/api/teams")
                .then().statusCode(201);

        // Listing multiple teams must batch into a single MonstersClient call.
        clearInvocations(monsters);
        given().get("/api/teams")
                .then().statusCode(200)
                .body("$", hasSize(2));
        verify(monsters, times(1)).instances(eq(pid), anyString());

        // Single GET
        given().get("/api/teams/" + team1Id)
                .then().statusCode(200)
                .body("team_id", equalTo(team1Id))
                .body("name", equalTo("Alpha"));

        // PUT rename & reorder slots (single MonstersClient call)
        clearInvocations(monsters);
        given().contentType(ContentType.JSON)
                .body("{\"name\": \"Alpha Prime\", \"members\": [\"" + MONSTER_3 + "\", \"" + MONSTER_1 + "\", \"" + MONSTER_2 + "\"]}")
                .put("/api/teams/" + team1Id)
                .then().statusCode(200)
                .body("name", equalTo("Alpha Prime"))
                .body("members", hasSize(3))
                .body("members[0].position", equalTo(1))
                .body("members[0].owned_id", equalTo(MONSTER_3.toString()))
                .body("members[1].position", equalTo(2))
                .body("members[1].owned_id", equalTo(MONSTER_1.toString()))
                .body("members[2].position", equalTo(3))
                .body("members[2].owned_id", equalTo(MONSTER_2.toString()));
        verify(monsters, times(1)).instances(eq(pid), anyString());

        // DELETE and verify 404 afterwards
        given().delete("/api/teams/" + team1Id)
                .then().statusCode(204);

        given().get("/api/teams/" + team1Id)
                .then().statusCode(404)
                .body("error", equalTo("NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void fiveTeamLimitEnforced() {
        UUID pid = UUID.fromString(PLAYER_ID);
        stubOwnedInstances(pid, instance(MONSTER_1, 1, "Snoozle", RUN_1, "active"));

        for (int i = 1; i <= 5; i++) {
            given().contentType(ContentType.JSON)
                    .body("{\"name\": \"Team " + i + "\", \"members\": [\"" + MONSTER_1 + "\"]}")
                    .post("/api/teams")
                    .then().statusCode(201);
        }

        given().contentType(ContentType.JSON)
                .body("{\"name\": \"Team 6\", \"members\": [\"" + MONSTER_1 + "\"]}")
                .post("/api/teams")
                .then().statusCode(400)
                .body("error", equalTo("TEAM_INVALID"))
                .body("details.code", hasItem("TEAM_LIMIT"));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void validateAgreesWithCreateAndRejectsForeignMonster() {
        UUID pid = UUID.fromString(PLAYER_ID);
        stubOwnedInstances(pid, instance(MONSTER_1, 1, "Snoozle", RUN_1, "active"));

        given().contentType(ContentType.JSON)
                .body("{\"members\": [\"" + MONSTER_1 + "\"]}")
                .post("/api/teams/validate")
                .then().statusCode(200)
                .body("valid", equalTo(true))
                .body("errors", empty());

        // Foreign monster
        String foreignMembers = "[\"" + MONSTER_1 + "\", \"" + FOREIGN_MONSTER + "\"]";
        given().contentType(ContentType.JSON)
                .body("{\"members\": " + foreignMembers + "}")
                .post("/api/teams/validate")
                .then().statusCode(200)
                .body("valid", equalTo(false))
                .body("errors.code", hasItem("NOT_OWNED"));

        given().contentType(ContentType.JSON)
                .body("{\"name\": \"Bad\", \"members\": " + foreignMembers + "}")
                .post("/api/teams")
                .then().statusCode(400)
                .body("error", equalTo("TEAM_INVALID"))
                .body("details.code", hasItem("NOT_OWNED"));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void overFiftyMemberIdsRejectedLocallyWithoutInvokingMonstersClient() {
        String fiftyOneIds = IntStream.range(0, 51)
                .mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
                .collect(Collectors.joining(","));

        given().contentType(ContentType.JSON)
                .body("{\"name\": \"Huge\", \"members\": [" + fiftyOneIds + "]}")
                .post("/api/teams")
                .then().statusCode(400)
                .body("error", equalTo("TEAM_INVALID"))
                .body("details.code", hasItem("TEAM_SIZE"));

        given().contentType(ContentType.JSON)
                .body("{\"members\": [" + fiftyOneIds + "]}")
                .post("/api/teams/validate")
                .then().statusCode(200)
                .body("valid", equalTo(false))
                .body("errors.code", hasItem("TEAM_SIZE"));

        verifyNoInteractions(monsters);
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void nullElementInMembersRejectedWithoutFalselyFlaggingValidMembers() {
        String membersWithNull = "[\"" + MONSTER_1 + "\", null]";

        given().contentType(ContentType.JSON)
                .body("{\"members\": " + membersWithNull + "}")
                .post("/api/teams/validate")
                .then().statusCode(200)
                .body("valid", equalTo(false))
                .body("errors.code", hasItem("INVALID_MEMBER"))
                .body("errors.code", not(hasItem("NOT_OWNED")));

        given().contentType(ContentType.JSON)
                .body("{\"name\": \"HasNull\", \"members\": " + membersWithNull + "}")
                .post("/api/teams")
                .then().statusCode(400)
                .body("error", equalTo("TEAM_INVALID"))
                .body("details.code", hasItem("INVALID_MEMBER"))
                .body("details.code", not(hasItem("NOT_OWNED")));

        verifyNoInteractions(monsters);
    }

    @Test
    @TestSecurity(user = "misty", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = OTHER_PLAYER_ID))
    void foreignTeamReturns404() {
        UUID ownerId = UUID.fromString(PLAYER_ID);
        stubOwnedInstances(ownerId, instance(MONSTER_1, 1, "Snoozle", RUN_1, "active"));
        UUID teamId = teamsService.create(ownerId, new TeamRequest("OwnerTeam", List.of(MONSTER_1))).teamId();

        given().get("/api/teams/" + teamId)
                .then().statusCode(404)
                .body("error", equalTo("NOT_FOUND"));

        given().contentType(ContentType.JSON)
                .body("{\"name\": \"Hijack\", \"members\": [\"" + MONSTER_1 + "\"]}")
                .put("/api/teams/" + teamId)
                .then().statusCode(404)
                .body("error", equalTo("NOT_FOUND"));

        given().delete("/api/teams/" + teamId)
                .then().statusCode(404)
                .body("error", equalTo("NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = "encounters", roles = "service")
    void internalTeamValidStaleAndIdempotentReset() {
        UUID pid = UUID.fromString(PLAYER_ID);
        stubOwnedInstances(pid,
                instance(MONSTER_1, 1, "Snoozle", RUN_1, "active"),
                instance(MONSTER_2, 2, "Pillowpup", RUN_1, "active"));

        UUID team1 = teamsService.create(pid, new TeamRequest("Main", Arrays.asList(MONSTER_1, MONSTER_2))).teamId();
        teamsService.create(pid, new TeamRequest("Backup", List.of(MONSTER_1)));

        // Valid internal team fetch uses a single MonstersClient call.
        clearInvocations(monsters);
        given().queryParam("player_id", pid.toString())
                .get("/internal/teams/" + team1)
                .then().statusCode(200)
                .body("team_id", equalTo(team1.toString()))
                .body("members", hasSize(2))
                .body("members[0].stats.hp", equalTo(50))
                .body("members[0].ability.name", equalTo("Thick Hide"));
        verify(monsters, times(1)).instances(eq(pid), anyString());

        // Stale / archived monster -> 409 TEAM_INVALID
        stubOwnedInstances(pid,
                instance(MONSTER_1, 1, "Snoozle", RUN_1, "archived"),
                instance(MONSTER_2, 2, "Pillowpup", RUN_1, "active"));
        given().queryParam("player_id", pid.toString())
                .get("/internal/teams/" + team1)
                .then().statusCode(409)
                .body("error", equalTo("TEAM_INVALID"));

        // Reset removes both teams and is idempotent.
        String resetBody = "{\"run_id\": \"" + RUN_1 + "\"}";
        given().contentType(ContentType.JSON).body(resetBody)
                .post("/internal/teams/" + pid + "/reset")
                .then().statusCode(200)
                .body("count", equalTo(2));

        given().contentType(ContentType.JSON).body(resetBody)
                .post("/internal/teams/" + pid + "/reset")
                .then().statusCode(200)
                .body("count", equalTo(0));
    }

    @Test
    void unauthenticatedReturns401WithBearerChallenge() {
        given().get("/api/teams")
                .then().statusCode(401)
                .header("WWW-Authenticate", containsString("Bearer"))
                .body("error", equalTo("UNAUTHORIZED"));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void playerTokenOnInternalEndpointsIs403() {
        given().queryParam("player_id", PLAYER_ID)
                .get("/internal/teams/" + UUID.randomUUID())
                .then().statusCode(403)
                .body("error", equalTo("FORBIDDEN"));

        given().contentType(ContentType.JSON)
                .body("{\"run_id\": \"" + RUN_1 + "\"}")
                .post("/internal/teams/" + PLAYER_ID + "/reset")
                .then().statusCode(403)
                .body("error", equalTo("FORBIDDEN"));
    }

    @Test
    @TestSecurity(user = "encounters", roles = "service")
    void serviceTokenOnPlayerEndpointIs403() {
        given().get("/api/teams")
                .then().statusCode(403)
                .body("error", equalTo("FORBIDDEN"));
    }
}
