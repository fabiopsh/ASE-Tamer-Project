package it.unipi.ase.tamer.encounters.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Wild monster choice for a normal template (P-21), with the battle's RNG.
 *
 * <ol>
 *   <li>Candidates = catalogue species with the template's element, minus the boss species.</li>
 *   <li>Only rarities with at least one candidate (and a positive weight) are kept; weights are renormalised.</li>
 *   <li>RNG calls, always in this order: rarity ({@code nextInt(total weight)}), species
 *       ({@code nextInt(n)} over the candidates of that rarity sorted by {@code monster_id}),
 *       level ({@code levelMin + nextInt(levelMax − levelMin + 1)}).</li>
 * </ol>
 * Rarities are scanned in the order common, rare, legendary, then any other name alphabetically.
 */
public final class WildSelector {

    /** One catalogue entry, as far as selection is concerned. */
    public record Species(int monsterId, String element, String rarity) {
    }

    /** The chosen species and level; stats at that level are then fetched by the caller. */
    public record WildPick(int monsterId, String rarity, int level) {
    }

    private static final List<String> RARITY_ORDER = List.of("common", "rare", "legendary");

    private WildSelector() {
    }

    public static WildPick select(BattleRandom rng, List<Species> catalogue, String element, Set<Integer> bossIds,
                                  Map<String, Integer> rarityWeights, int levelMin, int levelMax) {
        if (levelMin < 1 || levelMax < levelMin) {
            throw new IllegalArgumentException("Invalid level range " + levelMin + "-" + levelMax + ".");
        }
        List<Species> candidates = catalogue.stream()
                .filter(s -> element.equals(s.element()))
                .filter(s -> !bossIds.contains(s.monsterId()))
                .sorted(Comparator.comparingInt(Species::monsterId))
                .toList();

        List<String> rarities = new ArrayList<>(RARITY_ORDER);
        new TreeSet<>(rarityWeights.keySet()).stream().filter(r -> !RARITY_ORDER.contains(r)).forEach(rarities::add);

        List<String> kept = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (String r : rarities) {
            int w = rarityWeights.getOrDefault(r, 0);
            if (w > 0 && candidates.stream().anyMatch(s -> r.equals(s.rarity()))) {
                kept.add(r);
                weights.add(w);
                total += w;
            }
        }
        if (total == 0) {
            throw new IllegalStateException("No wild candidate for element " + element + " with weights "
                    + rarityWeights + ".");
        }

        int roll = rng.nextInt(total);
        String rarity = kept.get(kept.size() - 1);
        for (int i = 0; i < kept.size(); i++) {
            if (roll < weights.get(i)) {
                rarity = kept.get(i);
                break;
            }
            roll -= weights.get(i);
        }
        String chosenRarity = rarity;
        List<Species> ofRarity = candidates.stream().filter(s -> chosenRarity.equals(s.rarity())).toList();
        Species species = ofRarity.get(rng.nextInt(ofRarity.size()));
        int level = levelMin + rng.nextInt(levelMax - levelMin + 1);
        return new WildPick(species.monsterId(), chosenRarity, level);
    }
}
