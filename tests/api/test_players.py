"""US-03 (profilo), US-04 (starter pool e scelta dei 3 starter), US-06 (modifica profilo)."""

import uuid

import pytest


def _is_uuid(value):
    try:
        uuid.UUID(str(value))
        return True
    except ValueError:
        return False


def _profile(api, player):
    r = api.get("/api/players/me", headers=player["headers"])
    assert r.status_code == 200, r.text
    return r.json()


def _starters_url():
    return "/api/players/me/starters"


# --- US-03: profilo ----------------------------------------------------------

def test_profile_of_new_player(api, new_player):
    p = _profile(api, new_player)
    assert p["player_id"] == new_player["player_id"]
    assert p["display_name"] == new_player["username"]
    assert p["avatar_monster_id"] is None
    assert p["stage"] == 1
    assert p["max_stage"] == 10
    assert p["game_completed"] is False
    assert p["starters_chosen"] is False
    assert p["created_at"]
    assert p["progress"] == {
        "collection_size": 0,
        "species_owned": 0,
        "encounters_total": 0,
        "encounters_won": 0,
    }


# --- US-04: starter pool -----------------------------------------------------

def test_starter_pool_is_ten_distinct_common_monsters(api, new_player, reference):
    pool = _profile(api, new_player)["starter_pool"]
    assert len(pool) == 10
    assert len(set(pool)) == 10
    common_ids = {m["monster_id"] for m in reference["monsters"] if m["rarity"] == "common"}
    assert set(pool) <= common_ids


def test_starter_pool_is_stable_across_calls_and_logins(api, new_player):
    first = _profile(api, new_player)["starter_pool"]
    assert _profile(api, new_player)["starter_pool"] == first

    relogin = api.post("/api/auth/login", json={"username": new_player["username"], "password": new_player["password"]})
    assert relogin.status_code == 200, relogin.text
    headers = {"Authorization": f"Bearer {relogin.json()['access_token']}"}
    after = api.get("/api/players/me", headers=headers)
    assert after.status_code == 200
    assert after.json()["starter_pool"] == first


@pytest.mark.parametrize("case", ["two_ids", "four_ids", "duplicates", "outside_pool", "empty"])
def test_choose_starters_rejects_invalid_selection(api, new_player, reference, case):
    pool = _profile(api, new_player)["starter_pool"]
    outside = next(m["monster_id"] for m in reference["monsters"] if m["monster_id"] not in pool)
    selections = {
        "two_ids": pool[:2],
        "four_ids": pool[:4],
        "duplicates": [pool[0], pool[0], pool[1]],
        "outside_pool": [pool[0], pool[1], outside],
        "empty": [],
    }
    r = api.post(_starters_url(), json={"monster_ids": selections[case]}, headers=new_player["headers"])
    api.assert_error(r, 400, "validation_error")


def test_failed_starter_choice_does_not_consume_the_choice(api, new_player):
    pool = _profile(api, new_player)["starter_pool"]
    bad = api.post(_starters_url(), json={"monster_ids": pool[:2]}, headers=new_player["headers"])
    assert bad.status_code == 400

    assert _profile(api, new_player)["starters_chosen"] is False
    good = api.post(_starters_url(), json={"monster_ids": pool[:3]}, headers=new_player["headers"])
    assert good.status_code == 201, good.text


def test_choose_starters_creates_three_level_one_monsters(player_with_starters):
    starters = player_with_starters["starters"]
    assert len(starters) == 3
    assert {s["monster_id"] for s in starters} == set(player_with_starters["starter_monster_ids"])
    for s in starters:
        assert _is_uuid(s["owned_id"])
        assert s["level"] == 1
        assert s["xp"] == 0
        assert s["source"] == "starter"
        assert s["encounter_id"] is None


def test_starters_chosen_flag_and_progress_after_choice(api, player_with_starters):
    p = _profile(api, player_with_starters)
    assert p["starters_chosen"] is True
    assert p["progress"]["collection_size"] == 3
    assert p["progress"]["species_owned"] == 3


def test_choose_starters_twice_is_conflict(api, player_with_starters):
    pool = player_with_starters["starter_pool"]
    r = api.post(_starters_url(), json={"monster_ids": pool[3:6]}, headers=player_with_starters["headers"])
    api.assert_error(r, 409, "conflict")

    collection = api.get("/api/collection", headers=player_with_starters["headers"]).json()
    assert len(collection) == 3


# --- US-06: modifica profilo -------------------------------------------------

def test_patch_display_name(api, new_player):
    r = api.patch("/api/players/me", json={"display_name": "Ash Ketchum_2"}, headers=new_player["headers"])
    assert r.status_code == 200, r.text
    assert r.json()["display_name"] == "Ash Ketchum_2"
    assert _profile(api, new_player)["display_name"] == "Ash Ketchum_2"


@pytest.mark.parametrize(
    "display_name",
    ["ab", "x" * 21, "bad-name!", "dot.name"],
    ids=["too-short", "too-long", "symbols", "dot"],
)
def test_patch_rejects_invalid_display_name(api, new_player, display_name):
    r = api.patch("/api/players/me", json={"display_name": display_name}, headers=new_player["headers"])
    api.assert_error(r, 400, "validation_error")
    assert _profile(api, new_player)["display_name"] == new_player["username"]


def test_patch_avatar_to_owned_monster(api, player_with_starters):
    monster_id = player_with_starters["starters"][0]["monster_id"]
    r = api.patch("/api/players/me", json={"avatar_monster_id": monster_id}, headers=player_with_starters["headers"])
    assert r.status_code == 200, r.text
    assert r.json()["avatar_monster_id"] == monster_id


def test_patch_avatar_to_unowned_monster_is_validation_error(api, player_with_starters, reference):
    owned = {s["monster_id"] for s in player_with_starters["starters"]}
    not_owned = next(m["monster_id"] for m in reference["monsters"] if m["monster_id"] not in owned)
    r = api.patch("/api/players/me", json={"avatar_monster_id": not_owned}, headers=player_with_starters["headers"])
    api.assert_error(r, 400, "validation_error")


def test_patch_avatar_without_any_monster_is_validation_error(api, new_player):
    r = api.patch("/api/players/me", json={"avatar_monster_id": 1}, headers=new_player["headers"])
    api.assert_error(r, 400, "validation_error")


def test_patch_profile_requires_token(api):
    assert api.patch("/api/players/me", json={"display_name": "nobody"}).status_code == 401
