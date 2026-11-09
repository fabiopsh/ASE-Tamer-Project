package it.unipi.ase.tamer.monsters.service;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import it.unipi.ase.tamer.monsters.api.dto.AbilityResponse;
import it.unipi.ase.tamer.monsters.api.dto.ElementsResponse;
import it.unipi.ase.tamer.monsters.api.dto.MonsterCandidate;
import it.unipi.ase.tamer.monsters.api.dto.MonsterDetail;
import it.unipi.ase.tamer.monsters.api.dto.MonsterSummary;
import it.unipi.ase.tamer.monsters.api.dto.PageResponse;
import it.unipi.ase.tamer.monsters.api.dto.SpeciesStatsResponse;
import it.unipi.ase.tamer.monsters.domain.Ability;
import it.unipi.ase.tamer.monsters.domain.Element;
import it.unipi.ase.tamer.monsters.domain.Monster;
import it.unipi.ase.tamer.monsters.error.ApiException;
import it.unipi.ase.tamer.monsters.error.ErrorCodes;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only catalogue shared by player operations and internal services. */
@ApplicationScoped
public class CatalogueService {

    private static final Set<String> ELEMENTS = Set.of(
            "silence", "echo", "memory", "time", "chaos", "gravity", "dream");
    private static final Set<String> RARITIES = Set.of("common", "rare", "legendary");

    public PageResponse<MonsterSummary> catalogue(String element, String rarity, String ids,
                                                   Integer page, Integer size) {
        PageSpec spec = pageSpec(page, size);
        PanacheQuery<Monster> query = filtered(element, rarity, ids, null, true);
        long total = query.count();
        List<MonsterSummary> items = query.page(Page.of(spec.page(), spec.size())).list()
                .stream().map(MonsterSummary::from).toList();
        return PageResponse.of(items, spec.page(), spec.size(), total);
    }

    public MonsterDetail detail(int id) {
        return MonsterMapper.detail(requireMonster(id));
    }

    public List<AbilityResponse> abilities() {
        return Ability.<Ability>listAll(Sort.by("abilityId")).stream()
                .map(AbilityResponse::from).toList();
    }

    public AbilityResponse ability(int id) {
        Ability ability = Ability.findById(id);
        if (ability == null) {
            throw ApiException.notFound("Ability not found.");
        }
        return AbilityResponse.from(ability);
    }

    public ElementsResponse elements() {
        List<Element> all = Element.listAll(Sort.by("cyclePosition"));
        Map<String, String> strong = new LinkedHashMap<>();
        Map<String, String> weak = new LinkedHashMap<>();
        for (Element element : all) {
            strong.put(element.name, element.strongAgainst);
            weak.put(element.name, element.weakAgainst);
        }
        return new ElementsResponse(new ArrayList<>(strong.keySet()), strong, weak);
    }

    public List<MonsterCandidate> candidates(String rarity, String element, String exclude) {
        return filtered(element, rarity, null, exclude, false).list().stream()
                .map(m -> new MonsterCandidate(m.monsterId, m.element, m.rarity)).toList();
    }

    public SpeciesStatsResponse stats(int monsterId, int level) {
        if (level < 1 || level > Leveling.MAX_LEVEL) {
            throw invalid("level", "Level must be between 1 and 50.");
        }
        return MonsterMapper.stats(requireMonster(monsterId), level);
    }

    public static PageSpec pageSpec(Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;
        if (p < 0 || s < 1 || s > 50) {
            throw invalid("pagination", "Page must be >= 0 and size must be between 1 and 50.");
        }
        return new PageSpec(p, s);
    }

    public record PageSpec(int page, int size) {
    }

    private static PanacheQuery<Monster> filtered(String element, String rarity, String ids,
                                                   String exclude, boolean boundedIds) {
        if (element != null && !ELEMENTS.contains(element)) {
            throw invalid("element", "Unknown element.");
        }
        if (rarity != null && !RARITIES.contains(rarity)) {
            throw invalid("rarity", "Unknown rarity.");
        }
        Set<Integer> selected = parseIds(ids, boundedIds ? 50 : 500);
        Set<Integer> excluded = parseIds(exclude, 500);
        StringBuilder where = new StringBuilder("1 = 1");
        Map<String, Object> params = new LinkedHashMap<>();
        if (element != null) {
            where.append(" and element = :element");
            params.put("element", element);
        }
        if (rarity != null) {
            where.append(" and rarity = :rarity");
            params.put("rarity", rarity);
        }
        if (selected != null) {
            where.append(" and monsterId in :selected");
            params.put("selected", selected);
        }
        if (excluded != null) {
            where.append(" and monsterId not in :excluded");
            params.put("excluded", excluded);
        }
        return Monster.find(where.toString(), Sort.by("monsterId"), params);
    }

    private static Set<Integer> parseIds(String value, int max) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split(",", -1);
        if (parts.length == 0 || parts.length > max) {
            throw invalid("ids", "Invalid number of monster ids.");
        }
        Set<Integer> ids = new java.util.LinkedHashSet<>();
        for (String part : parts) {
            try {
                if (part.isBlank()) {
                    throw new NumberFormatException();
                }
                int id = Integer.parseInt(part);
                if (id <= 0) {
                    throw new NumberFormatException();
                }
                ids.add(id);
            } catch (NumberFormatException e) {
                throw invalid("ids", "Monster ids must be positive integers.");
            }
        }
        return ids;
    }

    static Monster requireMonster(int id) {
        Monster monster = Monster.findById(id);
        if (monster == null) {
            throw ApiException.notFound("Monster not found.");
        }
        return monster;
    }

    static ApiException invalid(String field, String message) {
        return new ApiException(400, ErrorCodes.VALIDATION_ERROR, message,
                List.of(new it.unipi.ase.tamer.monsters.error.ErrorDetail(field, "INVALID_VALUE")));
    }
}
