"""US-17..20 (bacheca, battaglia, ricompense), US-23..25 (storico, catena di hash),
US-12 (i servizi interni non sono esposti dal gateway).

Le battaglie sono casuali: i test che richiedono un esito specifico (cattura, vittoria)
ripetono l'encounter finche' la condizione non si verifica e poi saltano se non accade.
"""

import re
import uuid

import pytest

ENCOUNTERS = "/api/encounters"
HISTORY = "/api/history"
OFFER_FIELDS = ["encounter_id", "kind", "stage", "status", "difficulty", "enemy", "rewards", "created_at"]
ENEMY_FIELDS = ["monster_id", "name", "element", "rarity", "image_url", "level", "stats", "ability"]
RESULT_FIELDS = ["run_id", "encounter_id", "kind", "stage", "outcome", "rounds", "seed", "team", "enemy", "log", "rewards", "occurred_at"]
# Campi sempre presenti; gli altri (damage, critical, effectiveness, ...) sono omessi quando non pertinenti.
EVENT_FIELDS = ["round", "type", "actor", "text"]
EVENT_TYPES = {"enter", "attack", "dodge", "ability", "stun", "skip", "heal", "revive", "faint", "victory", "defeat"}
SUMMARY_FIELDS = ["run_id", "encounter_id", "sequence", "kind", "stage", "outcome", "rounds", "enemy", "team", "captured", "occurred_at"]


def _is_uuid(value):
    try:
        uuid.UUID(str(value))
        return True
    except ValueError:
        return False


def _owned_ids(player):
    return [s["owned_id"] for s in player["starters"]]


def _board(api, player):
    r = api.get(ENCOUNTERS, headers=player["headers"])
    assert r.status_code == 200, r.text
    return r.json()


def _open_wild(board):
    return [e for e in board["encounters"] if e["kind"] == "wild" and e["status"] == "open"]


def _start(api, player, encounter_id, team_id):
    return api.post(f"{ENCOUNTERS}/{encounter_id}/start", json={"team_id": team_id}, headers=player["headers"])


def _start_ok(api, player, encounter_id, team_id):
    r = _start(api, player, encounter_id, team_id)
    assert r.status_code == 201, r.text
    return r.json()


def _start_any_wild(api, player, team_id):
    wild = _open_wild(_board(api, player))
    assert wild, "la bacheca deve avere almeno un wild aperto"
    return _start_ok(api, player, wild[0]["encounter_id"], team_id)


def _start_until(api, player, team_id, predicate, attempts=20):
    """Ripete battaglie wild finche' predicate(result) e' vero; altrimenti salta il test."""
    for _ in range(attempts):
        result = _start_any_wild(api, player, team_id)
        if predicate(result):
            return result
    pytest.skip(f"condizione non raggiunta in {attempts} battaglie (esito casuale)")


def _collection_by_owned_id(api, player):
    r = api.get("/api/collection", headers=player["headers"])
    assert r.status_code == 200, r.text
    return {o["owned_id"]: o for o in r.json()}


@pytest.fixture
def ready_player(api, player_with_starters):
    """Giocatore con 3 starter e un team gia' creato, pronto per le battaglie."""
    r = api.post(
        "/api/teams",
        json={"name": "Hunters", "owned_ids": _owned_ids(player_with_starters)},
        headers=player_with_starters["headers"],
    )
    assert r.status_code == 201, r.text
    return {**player_with_starters, "team_id": r.json()["team_id"]}


# --- US-17: bacheca ---------------------------------------------------------------

def test_board_at_stage_one_has_three_wild_and_one_guardian(api, new_player):
    body = _board(api, new_player)
    assert body["stage"] == 1
    assert body["game_completed"] is False
    assert sorted(e["kind"] for e in body["encounters"]) == ["guardian", "wild", "wild", "wild"]


def test_offers_have_contract_fields(api, new_player):
    for offer in _board(api, new_player)["encounters"]:
        for field in OFFER_FIELDS:
            assert field in offer, field
        assert _is_uuid(offer["encounter_id"])
        assert offer["stage"] == 1
        assert offer["status"] == "open"
        for field in ENEMY_FIELDS:
            assert field in offer["enemy"], field
        assert set(offer["enemy"]["stats"]) == {"hp", "attack", "defense", "speed"}
        assert "xp" in offer["rewards"]
        assert "capture_chance" in offer["rewards"]
        assert "advances_stage" in offer["rewards"]


def test_stage_one_levels_difficulty_and_rewards(api, new_player):
    encounters = _board(api, new_player)["encounters"]
    guardian = next(e for e in encounters if e["kind"] == "guardian")
    wilds = [e for e in encounters if e["kind"] == "wild"]

    # Guardian dello stage s: livello 3s+2, boss, specie rare fino allo stage 5, avanza lo stage.
    assert guardian["enemy"]["level"] == 5
    assert guardian["difficulty"] == "boss"
    assert guardian["enemy"]["rarity"] == "rare"
    assert guardian["rewards"]["advances_stage"] is True
    assert guardian["rewards"]["xp"] == 20 * guardian["enemy"]["level"]  # 10 x livello, guardian x2

    # Wild dello stage s: livello in [3s-2, 3s], solo common fino allo stage 3, nessun avanzamento.
    for wild in wilds:
        level = wild["enemy"]["level"]
        assert 1 <= level <= 3
        assert wild["difficulty"] == {1: "easy", 2: "normal", 3: "hard"}[level]
        assert wild["enemy"]["rarity"] == "common"
        assert wild["rewards"]["xp"] == 10 * level
        assert wild["rewards"]["advances_stage"] is False
        assert 0 < wild["rewards"]["capture_chance"] <= 1


def test_board_is_stable_until_an_offer_is_completed(api, new_player):
    first = sorted(e["encounter_id"] for e in _board(api, new_player)["encounters"])
    second = sorted(e["encounter_id"] for e in _board(api, new_player)["encounters"])
    assert first == second


def test_offer_detail_is_owner_only(api, new_player, other_player):
    offer = _board(api, new_player)["encounters"][0]
    path = f"{ENCOUNTERS}/{offer['encounter_id']}"

    r = api.get(path, headers=new_player["headers"])
    assert r.status_code == 200, r.text
    assert r.json()["encounter_id"] == offer["encounter_id"]

    api.assert_error(api.get(path, headers=other_player["headers"]), 403, "forbidden")
    api.assert_error(api.get(f"{ENCOUNTERS}/{uuid.uuid4()}", headers=new_player["headers"]), 404, "not_found")


# --- US-18, US-19, US-20: battaglia, ricompense, cattura ---------------------------

def test_start_encounter_returns_result_with_battle_log(api, ready_player):
    board = _board(api, ready_player)
    offer = _open_wild(board)[0]
    r = _start(api, ready_player, offer["encounter_id"], ready_player["team_id"])
    assert r.status_code == 201, r.text
    result = r.json()

    for field in RESULT_FIELDS:
        assert field in result, field
    assert _is_uuid(result["run_id"])
    assert result["encounter_id"] == offer["encounter_id"]
    assert result["kind"] == "wild"
    assert result["stage"] == 1
    assert result["outcome"] in {"victory", "defeat"}
    assert isinstance(result["rounds"], int) and 1 <= result["rounds"] <= 100
    assert isinstance(result["seed"], int)

    assert [m["owned_id"] for m in result["team"]] == _owned_ids(ready_player)
    for member in result["team"]:
        assert {"owned_id", "monster_id", "name", "element", "image_url", "level", "max_hp", "stats"} <= set(member)
        # HP di battaglia = 2 x hp + 10
        assert member["max_hp"] == 2 * member["stats"]["hp"] + 10

    enemy = result["enemy"]
    assert enemy["monster_id"] == offer["enemy"]["monster_id"]
    assert enemy["level"] == offer["enemy"]["level"]
    assert enemy["stats"] == offer["enemy"]["stats"]
    # nemici: HP x1.5 (wild) o x1.4 (guardian), vedi backend/encounters/README.md
    multiplier = 1.4 if result["kind"] == "guardian" else 1.5
    assert enemy["max_hp"] == round((2 * enemy["stats"]["hp"] + 10) * multiplier)

    assert result["log"], "il log della battaglia non puo' essere vuoto"
    for event in result["log"]:
        for field in EVENT_FIELDS:
            assert field in event, field
        assert event["type"] in EVENT_TYPES
        assert event["actor"] == "wild" or event["actor"].startswith("team:")
    assert result["outcome"] in {e["type"] for e in result["log"]}

    assert len(result["rewards"]["xp"]) == len(result["team"])
    assert isinstance(result["rewards"]["stage_advanced"], bool)
    assert result["rewards"]["stage_advanced"] is False
    assert result["rewards"]["new_stage"] == 1
    assert result["occurred_at"]


def test_battle_awards_xp_to_every_team_member(api, ready_player):
    before = _collection_by_owned_id(api, ready_player)
    result = _start_any_wild(api, ready_player, ready_player["team_id"])
    after = _collection_by_owned_id(api, ready_player)

    assert len(result["rewards"]["xp"]) == 3
    for reward in result["rewards"]["xp"]:
        assert reward["xp"] > 0
        start = before[reward["owned_id"]]
        assert reward["old_level"] == start["level"]
        assert after[reward["owned_id"]]["level"] == reward["new_level"]
        assert reward["new_level"] >= reward["old_level"]
        assert (after[reward["owned_id"]]["level"], after[reward["owned_id"]]["xp"]) != (start["level"], start["xp"])


def test_captured_monster_joins_collection_with_encounter_source(api, ready_player):
    result = _start_until(
        api, ready_player, ready_player["team_id"],
        lambda r: r["rewards"]["captured"] is not None,
        attempts=15,
    )
    captured = result["rewards"]["captured"]
    assert captured["source"] == "encounter"
    assert captured["encounter_id"] == result["run_id"]
    assert captured["level"] == 1
    assert captured["xp"] == 0
    assert captured["monster_id"] == result["enemy"]["monster_id"]

    owned = _collection_by_owned_id(api, ready_player)
    assert captured["owned_id"] in owned
    entry = owned[captured["owned_id"]]
    assert entry["source"] == "encounter"
    assert entry["encounter_id"] == result["run_id"]


def test_completed_offer_cannot_be_started_again(api, ready_player):
    result = _start_until(
        api, ready_player, ready_player["team_id"],
        lambda r: r["outcome"] == "victory",
    )
    offer = api.get(f"{ENCOUNTERS}/{result['encounter_id']}", headers=ready_player["headers"]).json()
    assert offer["status"] == "completed"

    again = _start(api, ready_player, result["encounter_id"], ready_player["team_id"])
    api.assert_error(again, 409, "conflict")


def test_start_with_unknown_team_is_rejected(api, ready_player):
    offer = _open_wild(_board(api, ready_player))[0]
    r = _start(api, ready_player, offer["encounter_id"], str(uuid.uuid4()))
    assert r.status_code == 400, r.text
    assert "team not found" in r.json()["message"].lower()


def test_start_with_another_players_team_is_rejected(api, ready_player, other_player_with_starters):
    team = api.post(
        "/api/teams",
        json={"name": "Theirs", "owned_ids": _owned_ids(other_player_with_starters)},
        headers=other_player_with_starters["headers"],
    )
    assert team.status_code == 201, team.text
    offer = _open_wild(_board(api, ready_player))[0]
    r = _start(api, ready_player, offer["encounter_id"], team.json()["team_id"])
    assert r.status_code == 400, r.text
    assert "team not found" in r.json()["message"].lower()


def test_start_another_players_encounter_is_forbidden(api, ready_player, other_player_with_starters):
    # Il giocatore B usa un proprio team valido, cosi' l'unico motivo di rifiuto e' l'ownership dell'encounter.
    team = api.post(
        "/api/teams",
        json={"name": "Mine", "owned_ids": _owned_ids(other_player_with_starters)},
        headers=other_player_with_starters["headers"],
    )
    assert team.status_code == 201, team.text
    offer = _open_wild(_board(api, ready_player))[0]
    r = _start(api, other_player_with_starters, offer["encounter_id"], team.json()["team_id"])
    api.assert_error(r, 403, "forbidden")


def test_run_can_be_fetched_by_owner_only(api, ready_player, other_player):
    result = _start_any_wild(api, ready_player, ready_player["team_id"])
    path = f"{ENCOUNTERS}/runs/{result['run_id']}"

    r = api.get(path, headers=ready_player["headers"])
    assert r.status_code == 200, r.text
    stored = r.json()
    assert stored["run_id"] == result["run_id"]
    assert stored["outcome"] == result["outcome"]
    assert stored["rounds"] == result["rounds"]
    assert stored["log"] == result["log"]

    api.assert_error(api.get(path, headers=other_player["headers"]), 403, "forbidden")
    api.assert_error(api.get(f"{ENCOUNTERS}/runs/{uuid.uuid4()}", headers=ready_player["headers"]), 404, "not_found")


# --- US-23, US-24, US-25: storico e integrita' ----------------------------------------

def test_history_lists_the_run_first(api, ready_player):
    result = _start_any_wild(api, ready_player, ready_player["team_id"])
    body = api.get(HISTORY, headers=ready_player["headers"])
    assert body.status_code == 200, body.text
    body = body.json()
    assert body["page"] == 0
    assert body["total"] >= 1

    first = body["items"][0]
    for field in SUMMARY_FIELDS:
        assert field in first, field
    assert first["run_id"] == result["run_id"]
    assert first["encounter_id"] == result["encounter_id"]
    assert first["kind"] == result["kind"]
    assert first["stage"] == result["stage"]
    assert first["outcome"] == result["outcome"]
    assert first["rounds"] == result["rounds"]
    assert first["enemy"]["monster_id"] == result["enemy"]["monster_id"]
    assert [t["monster_id"] for t in first["team"]] == [m["monster_id"] for m in result["team"]]


def test_history_can_be_filtered_by_outcome(api, ready_player):
    result = _start_any_wild(api, ready_player, ready_player["team_id"])
    r = api.get(HISTORY, headers=ready_player["headers"], params={"outcome": result["outcome"]})
    assert r.status_code == 200, r.text
    items = r.json()["items"]
    assert result["run_id"] in [i["run_id"] for i in items]
    assert all(i["outcome"] == result["outcome"] for i in items)


def test_history_detail_contains_full_result_and_hash(api, ready_player):
    result = _start_any_wild(api, ready_player, ready_player["team_id"])
    r = api.get(f"{HISTORY}/{result['run_id']}", headers=ready_player["headers"])
    assert r.status_code == 200, r.text
    detail = r.json()
    assert detail["run_id"] == result["run_id"]
    assert detail["result"]["run_id"] == result["run_id"]
    assert detail["result"]["outcome"] == result["outcome"]
    assert detail["result"]["log"] == result["log"]
    assert re.fullmatch(r"[0-9a-f]{64}", detail["hash"])
    # Primo record del giocatore: sequenza 1 e hash precedente GENESIS.
    assert detail["sequence"] == 1
    assert detail["prev_hash"] == "GENESIS"


def test_history_detail_is_owner_only(api, ready_player, other_player):
    result = _start_any_wild(api, ready_player, ready_player["team_id"])
    path = f"{HISTORY}/{result['run_id']}"
    api.assert_error(api.get(path, headers=other_player["headers"]), 403, "forbidden")
    api.assert_error(api.get(f"{HISTORY}/{uuid.uuid4()}", headers=ready_player["headers"]), 404, "not_found")


def test_history_chain_is_sequential_and_linked(api, ready_player):
    _start_any_wild(api, ready_player, ready_player["team_id"])
    _start_any_wild(api, ready_player, ready_player["team_id"])

    records = []
    page = 0
    while True:
        body = api.get(HISTORY, headers=ready_player["headers"], params={"page": page, "size": 100}).json()
        records.extend(body["items"])
        page += 1
        if page >= body["total_pages"]:
            break
    sequences = sorted(r["sequence"] for r in records)
    assert sequences == list(range(1, len(records) + 1))

    by_sequence = {r["sequence"]: r for r in records}
    first = api.get(f"{HISTORY}/{by_sequence[1]['run_id']}", headers=ready_player["headers"]).json()
    second = api.get(f"{HISTORY}/{by_sequence[2]['run_id']}", headers=ready_player["headers"]).json()
    assert first["prev_hash"] == "GENESIS"
    assert second["prev_hash"] == first["hash"]


def test_history_verify_reports_intact_chain(api, ready_player):
    _start_any_wild(api, ready_player, ready_player["team_id"])
    r = api.get(f"{HISTORY}/verify", headers=ready_player["headers"])
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["valid"] is True
    assert body["records"] >= 1
    assert body["first_invalid_sequence"] is None


# --- Gateway: i servizi interni non sono esposti ----------------------------------------

@pytest.mark.parametrize(
    "method, path",
    [
        ("GET", "/internal/monsters"),
        ("GET", f"/internal/collection/{uuid.uuid4()}"),
        ("GET", f"/internal/teams/{uuid.uuid4()}?player_id={uuid.uuid4()}"),
        ("GET", f"/api/players/internal/players/{uuid.uuid4()}"),
        ("GET", f"/api/history/internal/history/{uuid.uuid4()}/stats"),
        ("POST", "/internal/history"),
        ("POST", "/internal/collection"),
        ("GET", "/q/health/ready"),
    ],
    ids=[
        "monsters", "collection", "teams", "players-via-api",
        "history-stats-via-api", "post-history", "post-collection", "quarkus-health",
    ],
)
def test_internal_paths_are_not_reachable_through_the_gateway(api, method, path):
    kwargs = {"json": {}} if method == "POST" else {}
    r = api.request(method, path, **kwargs)
    assert r.status_code in (403, 404), f"{method} {path} -> {r.status_code}"
