"""US-09 (catalogo, paginazione, filtri, ricerca), US-10 (dettaglio, abilita', elementi, immagini),
US-11 (collezione del giocatore), US-12 (nessun endpoint client per aggiungere mostri).

I test di sola lettura sul catalogo usano l'account condiviso della fixture `reference`;
i test sulla collezione usano utenti creati dal test.
"""

import uuid

import pytest

DETAIL_FIELDS = [
    "monster_id", "name", "element", "rarity", "description", "image_url",
    "base_hp", "base_attack", "base_defense", "base_speed",
    "growth_hp", "growth_attack", "growth_defense", "growth_speed",
    "ability_id", "ability", "strong_against", "weak_against",
]
ABILITY_FIELDS = ["ability_id", "name", "scope", "element", "effect_type", "effect_value", "trigger", "description"]
KNOWN_TRIGGERS = {"always", "first_turn", "hp_above_50", "hp_below_50", "on_hit", "on_attack", "once", "vs_faster"}
KNOWN_EFFECTS = {
    "damage_boost", "damage_reduction", "dodge", "heal", "speed_boost", "reflect", "stun",
    "weaken", "ignore_disadvantage", "revive", "random_boost", "invert_advantage", "copy_ability",
}
OWNED_FIELDS = [
    "owned_id", "monster_id", "name", "element", "rarity", "image_url",
    "level", "xp", "xp_to_next", "stats", "ability", "source", "encounter_id", "obtained_at",
]
OWNED_DETAIL_FIELDS = OWNED_FIELDS + ["description", "base_stats", "growth", "strong_against", "weak_against"]


def _ids(items):
    return [m["monster_id"] for m in items]


# --- US-09: catalogo -----------------------------------------------------------

def test_default_page_has_20_items_and_total_100(api, reference):
    r = api.get("/api/monsters", headers=reference["headers"])
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["page"] == 0
    assert body["size"] == 20
    assert body["total"] == 100
    assert body["total_pages"] == 5
    assert len(body["items"]) == 20
    ids = _ids(body["items"])
    assert ids == sorted(ids)


def test_pages_cover_the_whole_catalog_without_overlap(api, reference):
    seen = []
    for page in range(5):
        body = api.get("/api/monsters", headers=reference["headers"], params={"page": page, "size": 20}).json()
        assert body["page"] == page
        seen += _ids(body["items"])
    assert len(seen) == 100
    assert len(set(seen)) == 100
    assert seen == sorted(seen)

    beyond = api.get("/api/monsters", headers=reference["headers"], params={"page": 5, "size": 20}).json()
    assert beyond["items"] == []
    assert beyond["total"] == 100


def test_size_is_capped_at_100(api, reference):
    r = api.get("/api/monsters", headers=reference["headers"], params={"size": 500})
    assert r.status_code in (200, 400), r.text
    if r.status_code == 200:
        assert len(r.json()["items"]) <= 100


def test_summary_items_have_the_expected_fields(api, reference):
    item = api.get("/api/monsters", headers=reference["headers"], params={"size": 1}).json()["items"][0]
    assert set(item) >= {"monster_id", "name", "element", "rarity", "image_url"}


def test_filter_by_element(api, reference):
    element = reference["elements"]["cycle"][0]
    expected = [m["monster_id"] for m in reference["monsters"] if m["element"] == element]
    assert expected, "il catalogo deve contenere almeno un mostro per elemento"

    body = api.get("/api/monsters", headers=reference["headers"], params={"element": element, "size": 100}).json()
    assert body["total"] == len(expected)
    assert _ids(body["items"]) == expected
    assert all(m["element"] == element for m in body["items"])


@pytest.mark.parametrize("rarity", ["common", "rare", "legendary"])
def test_filter_by_rarity(api, reference, rarity):
    expected = [m["monster_id"] for m in reference["monsters"] if m["rarity"] == rarity]
    body = api.get("/api/monsters", headers=reference["headers"], params={"rarity": rarity, "size": 100}).json()
    assert body["total"] == len(expected)
    assert _ids(body["items"]) == expected
    assert all(m["rarity"] == rarity for m in body["items"])


def test_filter_by_element_and_rarity_combined(api, reference):
    element = reference["elements"]["cycle"][0]
    expected = [
        m["monster_id"] for m in reference["monsters"]
        if m["element"] == element and m["rarity"] == "common"
    ]
    body = api.get(
        "/api/monsters", headers=reference["headers"],
        params={"element": element, "rarity": "common", "size": 100},
    ).json()
    assert _ids(body["items"]) == expected


def test_filter_by_ids_returns_exactly_those_monsters_in_id_order(api, reference):
    body = api.get("/api/monsters", headers=reference["headers"], params={"ids": "3,1,2"}).json()
    assert _ids(body["items"]) == [1, 2, 3]
    assert body["total"] == 3


def test_search_by_name_is_case_insensitive(api, reference):
    target = reference["monsters"][0]
    fragment = target["name"][:4]
    body = api.get("/api/monsters", headers=reference["headers"], params={"q": fragment.upper(), "size": 100}).json()
    assert target["monster_id"] in _ids(body["items"])
    assert all(fragment.lower() in m["name"].lower() for m in body["items"])


def test_search_without_matches_returns_empty_page(api, reference):
    body = api.get("/api/monsters", headers=reference["headers"], params={"q": "zzzzqqqqxxxx"}).json()
    assert body["total"] == 0
    assert body["items"] == []


@pytest.mark.parametrize(
    "params",
    [{"element": "not-an-element"}, {"rarity": "mythic"}, {"ids": "one,two"}],
    ids=["unknown-element", "unknown-rarity", "non-numeric-ids"],
)
def test_invalid_filters_are_validation_errors(api, reference, params):
    r = api.get("/api/monsters", headers=reference["headers"], params=params)
    api.assert_error(r, 400, "validation_error")


def test_monsters_catalog_requires_token(api):
    assert api.get("/api/monsters").status_code == 401


# --- US-10: dettaglio, abilita', elementi, immagini ---------------------------------

@pytest.mark.parametrize("monster_id", [1, 50, 100])
def test_monster_detail_has_contract_fields(api, reference, monster_id):
    r = api.get(f"/api/monsters/{monster_id}", headers=reference["headers"])
    assert r.status_code == 200, r.text
    m = r.json()
    for field in DETAIL_FIELDS:
        assert field in m, field
    assert m["monster_id"] == monster_id
    assert m["image_url"] == f"/images/monsters/{monster_id:03d}.png"
    for stat in ("base_hp", "base_attack", "base_defense", "base_speed"):
        assert isinstance(m[stat], (int, float))
    assert m["ability"]["ability_id"] == m["ability_id"]
    assert set(ABILITY_FIELDS) <= set(m["ability"])


def test_monster_matchups_follow_the_element_cycle(api, reference):
    m = api.get("/api/monsters/1", headers=reference["headers"]).json()
    elements = reference["elements"]
    assert m["strong_against"] == elements["strong_against"][m["element"]]
    assert m["weak_against"] == elements["weak_against"][m["element"]]


def test_unknown_monster_is_not_found(api, reference):
    api.assert_error(api.get("/api/monsters/999", headers=reference["headers"]), 404, "not_found")


def test_abilities_list_has_28_unique_entries(api, reference):
    r = api.get("/api/monsters/abilities", headers=reference["headers"])
    assert r.status_code == 200, r.text
    items = r.json()
    assert len(items) == 28
    assert len({a["ability_id"] for a in items}) == 28
    for a in items:
        assert set(ABILITY_FIELDS) <= set(a)
        assert a["trigger"] in KNOWN_TRIGGERS
        assert a["effect_type"] in KNOWN_EFFECTS


def test_elements_endpoint_exposes_cycle_and_matchups(api, reference):
    r = api.get("/api/monsters/elements", headers=reference["headers"])
    assert r.status_code == 200, r.text
    e = r.json()
    cycle = e["cycle"]
    assert len(cycle) == 7
    assert set(e["strong_against"]) == set(cycle)
    assert set(e["weak_against"]) == set(cycle)
    for element in cycle:
        assert e["strong_against"][element] in cycle
        assert e["weak_against"][element] in cycle
        assert e["strong_against"][element] != element


def test_monster_sprite_is_served_without_auth(api):
    r = api.get("/images/monsters/001.png")
    assert r.status_code == 200, r.text
    assert r.headers["Content-Type"].startswith("image/png")
    assert r.content[:8] == b"\x89PNG\r\n\x1a\n"


def test_image_url_from_catalog_is_servable(api, reference):
    url = reference["monsters"][41]["image_url"]
    r = api.get(url)
    assert r.status_code == 200, r.text
    assert r.headers["Content-Type"].startswith("image/png")


# --- US-11: collezione del giocatore -------------------------------------------------

def test_collection_lists_only_my_monsters(api, player_with_starters, other_player):
    mine = api.get("/api/collection", headers=player_with_starters["headers"])
    assert mine.status_code == 200, mine.text
    assert sorted(o["owned_id"] for o in mine.json()) == sorted(
        s["owned_id"] for s in player_with_starters["starters"]
    )
    for o in mine.json():
        assert set(OWNED_FIELDS) <= set(o)
        assert o["source"] == "starter"

    theirs = api.get("/api/collection", headers=other_player["headers"])
    assert theirs.status_code == 200
    assert theirs.json() == []


@pytest.mark.parametrize("sort", ["level", "recent", "species"])
def test_collection_accepts_sort_options(api, player_with_starters, sort):
    r = api.get("/api/collection", headers=player_with_starters["headers"], params={"sort": sort})
    assert r.status_code == 200, r.text
    assert len(r.json()) == 3


def test_owned_monster_detail_has_stats_at_level_one(api, player_with_starters, reference):
    owned = player_with_starters["starters"][0]
    r = api.get(f"/api/collection/{owned['owned_id']}", headers=player_with_starters["headers"])
    assert r.status_code == 200, r.text
    d = r.json()
    for field in OWNED_DETAIL_FIELDS:
        assert field in d, field
    assert d["owned_id"] == owned["owned_id"]
    assert d["monster_id"] == owned["monster_id"]
    assert d["level"] == 1
    assert d["xp"] == 0
    assert d["xp_to_next"] == 25  # xp_to_next(level) = 25 * level
    assert d["source"] == "starter"
    assert d["encounter_id"] is None

    monster = api.get(f"/api/monsters/{owned['monster_id']}", headers=player_with_starters["headers"]).json()
    assert d["base_stats"] == {
        "hp": monster["base_hp"], "attack": monster["base_attack"],
        "defense": monster["base_defense"], "speed": monster["base_speed"],
    }
    assert d["growth"] == {
        "hp": monster["growth_hp"], "attack": monster["growth_attack"],
        "defense": monster["growth_defense"], "speed": monster["growth_speed"],
    }
    # Al livello 1 la statistica effettiva coincide con la base.
    assert d["stats"] == {
        "hp": round(monster["base_hp"]), "attack": round(monster["base_attack"]),
        "defense": round(monster["base_defense"]), "speed": round(monster["base_speed"]),
    }


def test_other_players_owned_monster_is_forbidden(api, player_with_starters, other_player):
    owned_id = player_with_starters["starters"][0]["owned_id"]
    r = api.get(f"/api/collection/{owned_id}", headers=other_player["headers"])
    api.assert_error(r, 403, "forbidden")


def test_unknown_owned_monster_is_not_found(api, new_player):
    r = api.get(f"/api/collection/{uuid.uuid4()}", headers=new_player["headers"])
    api.assert_error(r, 404, "not_found")


def test_client_cannot_add_monsters_to_collection(api, new_player):
    body = {"player_id": new_player["player_id"], "monster_id": 1, "source": "starter"}
    r = api.post("/api/collection", json=body, headers=new_player["headers"])
    assert r.status_code in (404, 405), r.text
    assert api.get("/api/collection", headers=new_player["headers"]).json() == []
