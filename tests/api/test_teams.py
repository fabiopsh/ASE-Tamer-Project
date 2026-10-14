"""US-14 (creazione e lista dei team), US-15 (modifica ed eliminazione), US-16 (validazione)."""

import uuid

import pytest

TEAMS = "/api/teams"


def _is_uuid(value):
    try:
        uuid.UUID(str(value))
        return True
    except ValueError:
        return False


def _owned_ids(player):
    return [s["owned_id"] for s in player["starters"]]


def _create(api, player, name, owned_ids):
    return api.post(TEAMS, json={"name": name, "owned_ids": owned_ids}, headers=player["headers"])


def _create_ok(api, player, name, owned_ids):
    r = _create(api, player, name, owned_ids)
    assert r.status_code == 201, r.text
    return r.json()


# --- US-14: creazione e lista -----------------------------------------------------

def test_create_team_returns_201_with_members_in_battle_order(api, player_with_starters):
    ids = _owned_ids(player_with_starters)
    order = list(reversed(ids))
    team = _create_ok(api, player_with_starters, "Alpha", order)

    assert _is_uuid(team["team_id"])
    assert team["name"] == "Alpha"
    assert team["owned_ids"] == order
    assert [m["owned_id"] for m in team["members"]] == order
    assert team["valid"] is True
    assert team["errors"] == []
    assert team["created_at"]
    assert team["updated_at"]
    for member in team["members"]:
        assert {"monster_id", "name", "level", "stats"} <= set(member)


def test_created_team_appears_in_list_and_can_be_fetched(api, player_with_starters):
    team = _create_ok(api, player_with_starters, "Beta", _owned_ids(player_with_starters)[:1])
    headers = player_with_starters["headers"]

    listing = api.get(TEAMS, headers=headers)
    assert listing.status_code == 200, listing.text
    assert team["team_id"] in [t["team_id"] for t in listing.json()]

    fetched = api.get(f"{TEAMS}/{team['team_id']}", headers=headers)
    assert fetched.status_code == 200, fetched.text
    assert fetched.json()["team_id"] == team["team_id"]
    assert fetched.json()["owned_ids"] == team["owned_ids"]


@pytest.mark.parametrize(
    "body_factory",
    [
        lambda ids: {"name": "", "owned_ids": ids},
        lambda ids: {"name": "   ", "owned_ids": ids},
        lambda ids: {"name": "x" * 31, "owned_ids": ids},
        lambda ids: {"name": "Ok", "owned_ids": []},
        lambda ids: {"name": "Ok", "owned_ids": [ids[0], ids[0]]},
        lambda ids: {"name": "Ok", "owned_ids": [str(uuid.uuid4()) for _ in range(4)]},
    ],
    ids=["empty-name", "blank-name", "name-31-chars", "no-members", "duplicate-members", "four-members"],
)
def test_create_invalid_team_is_400_with_error_details(api, player_with_starters, body_factory):
    body = body_factory(_owned_ids(player_with_starters))
    r = api.post(TEAMS, json=body, headers=player_with_starters["headers"])
    api.assert_error(r, 400, "validation_error")
    details = r.json().get("details")
    assert isinstance(details, list) and details, r.text


def test_create_team_with_foreign_monster_is_rejected(api, player_with_starters, other_player_with_starters):
    foreign = _owned_ids(other_player_with_starters)[:1]
    r = _create(api, player_with_starters, "Stolen", foreign)
    api.assert_error(r, 400, "validation_error")


def test_create_team_requires_token(api):
    assert api.post(TEAMS, json={"name": "x", "owned_ids": []}).status_code == 401


# --- US-15: lettura, modifica, eliminazione ----------------------------------------

def test_update_team_changes_name_order_and_members(api, player_with_starters):
    ids = _owned_ids(player_with_starters)
    headers = player_with_starters["headers"]
    team = _create_ok(api, player_with_starters, "Gamma", ids[:2])

    new_order = [ids[2], ids[0], ids[1]]
    r = api.put(f"{TEAMS}/{team['team_id']}", json={"name": "Gamma II", "owned_ids": new_order}, headers=headers)
    assert r.status_code == 200, r.text
    updated = r.json()
    assert updated["name"] == "Gamma II"
    assert updated["owned_ids"] == new_order
    assert len(updated["members"]) == 3

    fetched = api.get(f"{TEAMS}/{team['team_id']}", headers=headers).json()
    assert fetched["owned_ids"] == new_order
    assert fetched["name"] == "Gamma II"


def test_update_with_invalid_members_is_validation_error(api, player_with_starters):
    ids = _owned_ids(player_with_starters)
    team = _create_ok(api, player_with_starters, "Delta", ids[:1])
    r = api.put(
        f"{TEAMS}/{team['team_id']}",
        json={"name": "Delta", "owned_ids": [ids[0], ids[0]]},
        headers=player_with_starters["headers"],
    )
    api.assert_error(r, 400, "validation_error")
    assert r.json()["details"]


def test_delete_team_returns_204_and_removes_it(api, player_with_starters):
    headers = player_with_starters["headers"]
    team = _create_ok(api, player_with_starters, "Epsilon", _owned_ids(player_with_starters)[:1])

    r = api.delete(f"{TEAMS}/{team['team_id']}", headers=headers)
    assert r.status_code == 204, r.text
    api.assert_error(api.get(f"{TEAMS}/{team['team_id']}", headers=headers), 404, "not_found")
    assert team["team_id"] not in [t["team_id"] for t in api.get(TEAMS, headers=headers).json()]


def test_unknown_team_is_not_found(api, new_player):
    api.assert_error(api.get(f"{TEAMS}/{uuid.uuid4()}", headers=new_player["headers"]), 404, "not_found")
    api.assert_error(api.delete(f"{TEAMS}/{uuid.uuid4()}", headers=new_player["headers"]), 404, "not_found")


def test_other_player_cannot_read_update_or_delete_team(api, player_with_starters, other_player_with_starters):
    owner = player_with_starters["headers"]
    intruder = other_player_with_starters["headers"]
    team = _create_ok(api, player_with_starters, "Private", _owned_ids(player_with_starters)[:1])
    path = f"{TEAMS}/{team['team_id']}"

    api.assert_error(api.get(path, headers=intruder), 403, "forbidden")
    intruder_ids = _owned_ids(other_player_with_starters)[:1]
    api.assert_error(api.put(path, json={"name": "Hijack", "owned_ids": intruder_ids}, headers=intruder), 403, "forbidden")
    api.assert_error(api.delete(path, headers=intruder), 403, "forbidden")

    # Il team resta dell'owner e non compare nella lista dell'altro giocatore.
    assert api.get(path, headers=owner).status_code == 200
    assert team["team_id"] not in [t["team_id"] for t in api.get(TEAMS, headers=intruder).json()]


def test_player_can_have_at_most_five_teams(api, player_with_starters):
    ids = _owned_ids(player_with_starters)
    for i in range(5):
        _create_ok(api, player_with_starters, f"Team {i}", [ids[i % 3]])

    r = _create(api, player_with_starters, "Team 6", [ids[0]])
    assert r.status_code in (400, 409), r.text
    assert len(api.get(TEAMS, headers=player_with_starters["headers"]).json()) == 5


# --- US-16: validazione ---------------------------------------------------------

def test_validate_accepts_a_valid_team(api, player_with_starters):
    r = api.post(
        f"{TEAMS}/validate",
        json={"name": "Check", "owned_ids": _owned_ids(player_with_starters)[:1]},
        headers=player_with_starters["headers"],
    )
    assert r.status_code == 200, r.text
    assert r.json() == {"valid": True, "errors": []}


@pytest.mark.parametrize("case", ["empty", "four_members", "duplicates", "foreign_monster"])
def test_validate_reports_errors_without_failing(api, player_with_starters, other_player_with_starters, case):
    own = _owned_ids(player_with_starters)
    selections = {
        "empty": [],
        "four_members": own + [str(uuid.uuid4())],
        "duplicates": [own[0], own[0]],
        "foreign_monster": _owned_ids(other_player_with_starters)[:1],
    }
    r = api.post(
        f"{TEAMS}/validate",
        json={"name": "Check", "owned_ids": selections[case]},
        headers=player_with_starters["headers"],
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["valid"] is False
    assert isinstance(body["errors"], list) and body["errors"]
    if case == "duplicates":
        assert any("duplicate" in e.lower() for e in body["errors"])
