package it.unipi.ase.tamer.encounters.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Balance check (EN-2, optional): for every stage, 1000 battles per normal template and for the boss, with a team
 * of 3 random commons at random levels of the stage's normal range, always attacking. Prints the win rates.
 *
 * <p>Run from {@code backend/encounters} after {@code mvn test-compile}:
 * {@code java -cp target/classes;target/test-classes;<jackson jars> it.unipi.ase.tamer.encounters.engine.BalanceSimulation [seedDir] [battles] [commons|mixed]}
 * ({@code seedDir} defaults to {@code ../monsters/seed}; {@code mixed} draws the team with the stage's rarity
 * weights instead of commons only, closer to a player who has been capturing monsters).
 */
public final class BalanceSimulation {

    record Species(int id, String name, String element, String rarity, int[] base, double[] growth, Ability ability) {
    }

    record Stage(int stage, List<String> elements, Map<String, Integer> weights, int bossId) {
    }

    static final List<Stage> STAGES = List.of(
            new Stage(1, List.of("dream", "echo", "silence"), weights(100, 0, 0), 31),
            new Stage(2, List.of("memory", "time", "gravity"), weights(90, 10, 0), 89),
            new Stage(3, List.of("dream", "echo", "silence"), weights(80, 20, 0), 74),
            new Stage(4, List.of("memory", "time", "gravity"), weights(70, 30, 0), 62),
            new Stage(5, List.of("dream", "silence", "chaos"), weights(60, 40, 0), 47),
            new Stage(6, List.of("echo", "memory", "gravity"), weights(50, 45, 5), 16),
            new Stage(7, List.of("time", "silence", "chaos"), weights(40, 50, 10), 92),
            new Stage(8, List.of("dream", "gravity", "chaos"), weights(30, 55, 15), 100));

    private static Map<String, Integer> weights(int common, int rare, int legendary) {
        return Map.of("common", common, "rare", rare, "legendary", legendary);
    }

    private BalanceSimulation() {
    }

    public static void main(String[] args) throws IOException {
        File dir = new File(args.length > 0 ? args[0] : "../monsters/seed");
        int battles = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
        boolean mixed = args.length > 2 && args[2].equals("mixed");
        Map<Integer, Species> species = load(dir);
        Set<Integer> bossIds = Set.copyOf(STAGES.stream().map(Stage::bossId).toList());
        List<Species> commons = species.values().stream().filter(s -> s.rarity().equals("common"))
                .sorted((a, b) -> Integer.compare(a.id(), b.id())).toList();
        List<WildSelector.Species> catalogue = species.values().stream()
                .map(s -> new WildSelector.Species(s.id(), s.element(), s.rarity())).toList();

        System.out.printf("Battles per template: %d; team: 3 random %s at levels of the stage range; always attack%n",
                battles, mixed ? "non-boss monsters drawn with the stage rarity weights" : "commons");
        System.out.printf("%-6s %-10s %-8s %-8s %-10s%n", "stage", "template", "win%", "rounds", "team KO/b");
        for (Stage st : STAGES) {
            int lmin = 3 * st.stage() - 2;
            int lmax = 3 * st.stage();
            for (int k = 0; k < 4; k++) {
                boolean boss = k == 3;
                String element = boss ? species.get(st.bossId()).element() : st.elements().get(k);
                int wins = 0;
                long rounds = 0;
                long knockouts = 0;
                for (int i = 0; i < battles; i++) {
                    long seed = st.stage() * 1_000_003L + k * 100_003L + i;
                    BattleRandom rng = new BattleRandom(seed);
                    Combatant wild;
                    if (boss) {
                        wild = combatant(species.get(st.bossId()), 3 * st.stage() + 2, null);
                    } else {
                        WildSelector.WildPick pick = WildSelector.select(rng, catalogue, element, bossIds,
                                st.weights(), lmin, lmax);
                        wild = combatant(species.get(pick.monsterId()), pick.level(), null);
                    }
                    SplittableRandom teamRng = new SplittableRandom(~seed);
                    List<Combatant> team = new ArrayList<>();
                    for (int m = 0; m < 3; m++) {
                        Species s = mixed ? mixedMember(teamRng, species, bossIds, st.weights())
                                : commons.get(teamRng.nextInt(commons.size()));
                        team.add(combatant(s, lmin + teamRng.nextInt(lmax - lmin + 1), "member-" + m));
                    }
                    TemplateInfo t = new TemplateInfo(st.stage() * 10 + k + 1, "sim", st.stage(), element, boss);
                    Battle b = new Battle(rng, new EncounterSpec(t, wild), team, ElementChart.defaults());
                    while (!b.isFinished()) {
                        b.apply(Action.attack());
                    }
                    if (b.outcome() == Outcome.VICTORY) {
                        wins++;
                    }
                    rounds += b.turn();
                    knockouts += b.view().team().stream().filter(BattleView.TeamMemberView::knockedOut).count();
                }
                System.out.printf("%-6d %-10s %-8.1f %-8.1f %-10.2f%n", st.stage(),
                        boss ? "boss " + st.bossId() : element, 100.0 * wins / battles,
                        (double) rounds / battles, (double) knockouts / battles);
            }
        }
    }

    /** A team member drawn like a wild monster of the stage (any element): rarity by weight, then species. */
    static Species mixedMember(SplittableRandom rng, Map<Integer, Species> species, Set<Integer> bossIds,
                               Map<String, Integer> weights) {
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        int roll = rng.nextInt(total);
        String rarity = "common";
        for (String r : List.of("common", "rare", "legendary")) {
            int w = weights.getOrDefault(r, 0);
            if (roll < w) {
                rarity = r;
                break;
            }
            roll -= w;
        }
        String chosen = rarity;
        List<Species> pool = species.values().stream()
                .filter(s -> s.rarity().equals(chosen) && !bossIds.contains(s.id()))
                .sorted((a, b) -> Integer.compare(a.id(), b.id())).toList();
        return pool.get(rng.nextInt(pool.size()));
    }

    static Combatant combatant(Species s, int level, String id) {
        return new Combatant(id, s.id(), s.name(), s.element(), s.rarity(), level,
                stat(s, 0, level), stat(s, 1, level), stat(s, 2, level), stat(s, 3, level), s.ability());
    }

    /** Same rule as monsters' StatCalculator: floor(base + growth × (level − 1)). */
    static int stat(Species s, int i, int level) {
        return BigDecimal.valueOf(s.base()[i]).add(BigDecimal.valueOf(s.growth()[i])
                .multiply(BigDecimal.valueOf(level - 1))).setScale(0, RoundingMode.FLOOR).intValueExact();
    }

    static Map<Integer, Species> load(File dir) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<Integer, Ability> abilities = new HashMap<>();
        for (JsonNode a : mapper.readTree(new File(dir, "abilities.json"))) {
            abilities.put(a.get("ability_id").asInt(), new Ability(a.get("ability_id").asInt(), a.get("name").asText(),
                    a.get("description").asText(), a.get("effect_type").asText(), a.get("effect_value").asInt(),
                    a.get("trigger").asText()));
        }
        Map<Integer, Species> species = new HashMap<>();
        String[] stats = {"hp", "attack", "defense", "speed"};
        for (JsonNode m : mapper.readTree(new File(dir, "monsters.json"))) {
            int[] base = new int[4];
            double[] growth = new double[4];
            for (int i = 0; i < 4; i++) {
                base[i] = m.get("base_" + stats[i]).asInt();
                growth[i] = m.get("growth_" + stats[i]).asDouble();
            }
            JsonNode ab = m.get("ability_id");
            species.put(m.get("monster_id").asInt(), new Species(m.get("monster_id").asInt(), m.get("name").asText(),
                    m.get("element").asText(), m.get("rarity").asText(), base, growth,
                    ab == null || ab.isNull() ? null : abilities.get(ab.asInt())));
        }
        return species;
    }
}
