"""US-01 (registrazione), US-02 (login), US-05 (JWT e protezione degli endpoint)."""

import uuid

import pytest

PASSWORD = "Tamer-Pass-123"
WRONG_PASSWORD = "Wrong-Pass-999"


def _is_uuid(value):
    try:
        uuid.UUID(str(value))
        return True
    except ValueError:
        return False


# --- US-01: registrazione -------------------------------------------------

def test_register_creates_account_and_returns_201(api, fresh_username):
    r = api.post("/api/auth/register", json={"username": fresh_username, "password": PASSWORD})
    assert r.status_code == 201, r.text
    body = r.json()
    assert _is_uuid(body["player_id"])
    assert body["username"] == fresh_username
    assert "password" not in body
    assert "password_hash" not in body


def test_register_duplicate_username_is_conflict(api, fresh_username):
    body = {"username": fresh_username, "password": PASSWORD}
    assert api.post("/api/auth/register", json=body).status_code == 201
    api.assert_error(api.post("/api/auth/register", json=body), 409, "conflict")


def test_register_username_is_case_insensitive(api, fresh_username):
    assert api.post("/api/auth/register", json={"username": fresh_username, "password": PASSWORD}).status_code == 201
    r = api.post("/api/auth/register", json={"username": fresh_username.upper(), "password": PASSWORD})
    api.assert_error(r, 409, "conflict")


@pytest.mark.parametrize("password", ["Abcdefg1", "y" * 72], ids=["8-chars", "72-chars"])
def test_register_accepts_password_length_boundaries(api, fresh_username, password):
    r = api.post("/api/auth/register", json={"username": fresh_username, "password": password})
    assert r.status_code == 201, r.text


@pytest.mark.parametrize("password", ["Short1!", "x" * 73], ids=["7-chars", "73-chars"])
def test_register_rejects_password_outside_8_to_72(api, fresh_username, password):
    r = api.post("/api/auth/register", json={"username": fresh_username, "password": password})
    api.assert_error(r, 400, "validation_error")


@pytest.mark.parametrize(
    "username",
    ["ab", "a" * 21, "has space", "dash-name", "dot.name", ""],
    ids=["too-short", "too-long", "space", "dash", "dot", "empty"],
)
def test_register_rejects_invalid_username(api, username):
    r = api.post("/api/auth/register", json={"username": username, "password": PASSWORD})
    api.assert_error(r, 400, "validation_error")


def test_register_missing_fields_is_validation_error(api):
    api.assert_error(api.post("/api/auth/register", json={}), 400, "validation_error")


# --- US-02: login ----------------------------------------------------------

def test_login_returns_bearer_token(api, new_player):
    r = api.post("/api/auth/login", json={"username": new_player["username"], "password": PASSWORD})
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["token_type"] == "Bearer"
    assert body["expires_in"] == 3600
    assert body["access_token"].count(".") == 2  # JWT compatto: header.payload.signature
    assert body["player_id"] == new_player["player_id"]
    assert body["username"] == new_player["username"]


def test_login_wrong_password_is_unauthorized_with_generic_message(api, new_player):
    r = api.post("/api/auth/login", json={"username": new_player["username"], "password": WRONG_PASSWORD})
    assert r.status_code == 401, r.text
    body = r.json()
    assert body["error"] == "unauthorized"
    assert body["message"] == "invalid username or password"


def test_unknown_user_and_wrong_password_look_identical(api, new_player, fresh_username):
    wrong = api.post("/api/auth/login", json={"username": new_player["username"], "password": WRONG_PASSWORD})
    unknown = api.post("/api/auth/login", json={"username": fresh_username, "password": WRONG_PASSWORD})
    assert wrong.status_code == unknown.status_code == 401
    # Nessuna fuga di informazioni: l'utente esiste o no, la risposta non cambia.
    assert wrong.json()["error"] == unknown.json()["error"]
    assert wrong.json()["message"] == unknown.json()["message"]


def test_account_locks_after_five_failed_logins(api, new_player):
    bad = {"username": new_player["username"], "password": WRONG_PASSWORD}
    for _ in range(4):
        assert api.post("/api/auth/login", json=bad).status_code == 401
    # Il quinto tentativo fallito puo' gia' rispondere 429, il successivo certamente.
    assert api.post("/api/auth/login", json=bad).status_code in (401, 429)

    locked = api.post("/api/auth/login", json={"username": new_player["username"], "password": PASSWORD})
    assert locked.status_code == 429, locked.text
    assert locked.json()["error"] == "too_many_requests"


# --- US-05: JWT e protezione -------------------------------------------------

@pytest.mark.parametrize(
    "path",
    ["/api/auth/me", "/api/players/me", "/api/monsters", "/api/collection", "/api/teams", "/api/encounters", "/api/history"],
)
def test_protected_endpoints_require_token(api, path):
    assert api.get(path).status_code == 401


def test_tampered_token_is_rejected(api, new_player):
    head, payload, signature = new_player["token"].split(".")
    # Cambio il primo carattere della firma: porta bit reali, quindi la firma diventa invalida.
    flipped = "A" if signature[0] != "A" else "B"
    tampered = f"{head}.{payload}.{flipped}{signature[1:]}"
    r = api.get("/api/auth/me", headers={"Authorization": f"Bearer {tampered}"})
    assert r.status_code == 401


@pytest.mark.parametrize("authorization", ["Bearer not-a-jwt", "Bearer ", "Token abc"])
def test_malformed_authorization_header_is_rejected(api, authorization):
    assert api.get("/api/auth/me", headers={"Authorization": authorization}).status_code == 401


def test_me_returns_the_authenticated_player(api, new_player):
    r = api.get("/api/auth/me", headers=new_player["headers"])
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["player_id"] == new_player["player_id"]
    assert body["username"] == new_player["username"]
    assert body["created_at"]
    assert body["last_login"]  # valorizzato dal login fatto dalla fixture
