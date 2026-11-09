package it.unipi.ase.tamer.encounters.api;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import io.restassured.response.ValidatableResponse;
import it.unipi.ase.tamer.encounters.client.CountResponse;
import it.unipi.ase.tamer.encounters.client.HistoryClient;
import it.unipi.ase.tamer.encounters.client.MonsterCandidate;
import it.unipi.ase.tamer.encounters.client.MonstersClient;
import it.unipi.ase.tamer.encounters.client.NewRunResponse;
import it.unipi.ase.tamer.encounters.client.OwnedMonster;
import it.unipi.ase.tamer.encounters.client.PlayersClient;
import it.unipi.ase.tamer.encounters.client.RewardRequest;
import it.unipi.ase.tamer.encounters.client.SpeciesStats;
import it.unipi.ase.tamer.encounters.client.StatBlock;
import it.unipi.ase.tamer.encounters.client.TeamSnapshot;
import it.unipi.ase.tamer.encounters.client.TeamsClient;
import it.unipi.ase.tamer.encounters.client.XpGain;
import it.unipi.ase.tamer.encounters.client.XpRequest;
import it.unipi.ase.tamer.encounters.domain.BattleCompletion;
import it.unipi.ase.tamer.encounters.domain.Run;
import it.unipi.ase.tamer.encounters.service.BattleRegistry;
import it.unipi.ase.tamer.encounters.service.RunService;
import it.unipi.ase.tamer.encounters.support.FixedSeedSource;
import it.unipi.ase.tamer.encounters.support.TestTime;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;

/**
 * Shared setup of the {@code @QuarkusTest} classes: mocked clients of the four called services, a clean DB,
 * no battle in memory, a fixed seed and clock, and an active run for {@link #PLAYER}.
 *
 * <p>Stats are chosen so that the outcome does not depend on the seed: a "strong" team kills the wild monster
 * with one hit and is faster; a "weak" single member is killed by the first wild attack.
 */
abstract class ApiTestBase {

    static final String PLAYER = "7c0e8a52-3f43-4a8f-9a3e-2f1d1b2b9c10";
    static final UUID PLAYER_ID = UUID.fromString(PLAYER);
    static final UUID TEAM = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @InjectMock
    @RestClient
    MonstersClient monsters;

    @InjectMock
    @RestClient
    TeamsClient teams;

    @InjectMock
    @RestClient
    PlayersClient players;

    @InjectMock
    @RestClient
    HistoryClient history;

    @Inject
    BattleRegistry registry;

    @Inject
    RunService runService;

    @Inject
    CircuitBreakerMaintenance circuitBreakers;

    UUID runId;
    List<UUID> owned;

    @BeforeEach
    void baseSetup() {
        QuarkusTransaction.requiringNew().run(() -> {
            BattleCompletion.deleteAll();
            Run.deleteAll();
        });
        registry.clear();
        // the fault tolerance interceptors also wrap the mocks: start every test with closed circuits
        circuitBreakers.resetAll();
        TestTime.reset();
        FixedSeedSource.seed = 42L;
        runId = UUID.randomUUID();
        runService.open(runId, PLAYER_ID);
        owned = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        strongTeam();
        when(monsters.candidates(anyString(), anyString())).thenAnswer(inv -> {
            String element = inv.getArgument(0);
            return List.of(new MonsterCandidate(901, element, "common"), new MonsterCandidate(902, element, "rare"));
        });
        weakWild();
        when(monsters.reward(any(), any())).thenAnswer(inv -> {
            RewardRequest r = inv.getArgument(1);
            return new OwnedMonster(UUID.nameUUIDFromBytes(r.encounterId().toString().getBytes()), r.monsterId(),
                    r.level());
        });
        when(monsters.xp(any(), any())).thenAnswer(inv -> {
            XpRequest r = inv.getArgument(1);
            return r.ownedIds().stream().map(id -> new XpGain(id, 5, 6, 3)).toList();
        });
        when(monsters.archive(any(), any())).thenReturn(new CountResponse(3));
        when(teams.reset(any(), any())).thenReturn(new CountResponse(1));
        when(players.newRun(any(), any())).thenReturn(new NewRunResponse(UUID.randomUUID()));
    }

    // ---- mocks -----------------------------------------------------------------------------------------------

    void team(List<TeamSnapshot.Member> members) {
        when(teams.team(eq(TEAM), eq(PLAYER_ID))).thenReturn(new TeamSnapshot(TEAM, "Main", runId, members));
    }

    static TeamSnapshot.Member member(int position, UUID ownedId, StatBlock stats) {
        return new TeamSnapshot.Member(position, ownedId, 3 + position, "Member" + position, 5, "dream", "common",
                String.format("/images/monsters/%03d.png", 3 + position), stats, null);
    }

    /** Three members, huge attack, fastest: one hit wins. */
    void strongTeam() {
        StatBlock strong = new StatBlock(100, 5000, 100, 500);
        team(List.of(member(1, owned.get(0), strong), member(2, owned.get(1), strong),
                member(3, owned.get(2), strong)));
    }

    /** Two members (so that a switch is possible) that cannot win. */
    void slowTeamOfTwo() {
        StatBlock tank = new StatBlock(500, 1, 500, 1);
        team(List.of(member(1, owned.get(0), tank), member(2, owned.get(1), tank)));
    }

    /** One member that the wild monster kills with its first attack. */
    void weakTeam() {
        team(List.of(member(1, owned.get(0), new StatBlock(1, 1, 1, 1))));
    }

    void weakWild() {
        when(monsters.stats(anyInt(), anyInt())).thenAnswer(inv -> new SpeciesStats(inv.getArgument(0), "Wildling",
                "dream", inv.<Integer>getArgument(0) == 902 ? "rare" : "common", inv.getArgument(1),
                new StatBlock(10, 1, 1, 1), null));
    }

    void deadlyWild() {
        when(monsters.stats(anyInt(), anyInt())).thenAnswer(inv -> new SpeciesStats(inv.getArgument(0), "Wildling",
                "dream", "common", inv.getArgument(1), new StatBlock(5000, 5000, 5000, 5000), null));
    }

    // ---- DB --------------------------------------------------------------------------------------------------

    Run run() {
        return QuarkusTransaction.requiringNew().call(() -> Run.<Run>findById(runId));
    }

    void updateRun(Consumer<Run> change) {
        QuarkusTransaction.requiringNew().run(() -> change.accept(Run.<Run>findById(runId)));
    }

    BattleCompletion completion(String encounterId) {
        return QuarkusTransaction.requiringNew().call(
                () -> BattleCompletion.<BattleCompletion>findById(UUID.fromString(encounterId)));
    }

    // ---- HTTP ------------------------------------------------------------------------------------------------

    static ValidatableResponse start(int templateId) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("template_id", templateId, "team_id", TEAM.toString()))
                .post("/api/battles").then();
    }

    static ValidatableResponse act(String json) {
        return given().contentType(ContentType.JSON).body(json).post("/api/battles/current/actions").then();
    }

    static ValidatableResponse attack() {
        return act("{\"type\":\"attack\"}");
    }
}
