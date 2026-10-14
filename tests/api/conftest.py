"""Fixture condivise per i test black-box del gateway Monster Tamers.

I test parlano solo HTTP con il gateway (BASE_URL, default https://localhost:8443)
e seguono il contratto in docs/architettura/api-contract.md.

Indipendenza: ogni fixture che modifica lo stato crea un proprio utente.
L'eccezione e' la fixture `reference` (session scope): dati di sola lettura
(catalogo, elementi) letti una sola volta con un account dedicato, per non
saturare il rate limit del gateway su /api/auth/.
"""

import os
import time
import uuid

import pytest
import requests
import urllib3
from urllib3.exceptions import InsecureRequestWarning

# Il gateway in sviluppo usa un certificato self-signed: niente verifica TLS, niente warning.
urllib3.disable_warnings(InsecureRequestWarning)

BASE_URL = os.environ.get("BASE_URL", "https://localhost:8443").rstrip("/")
PASSWORD = "Tamer-Pass-123"
TIMEOUT = 30


class Api:
    """Client HTTP minimale: i path sono relativi a BASE_URL, es. "/api/auth/me"."""

    def __init__(self, base_url):
        self.base_url = base_url
        self.session = requests.Session()
        self.session.verify = False

    def request(self, method, path, **kwargs):
        kwargs.setdefault("timeout", TIMEOUT)
        return self.session.request(method, self.base_url + path, **kwargs)

    def get(self, path, **kwargs):
        return self.request("GET", path, **kwargs)

    def post(self, path, **kwargs):
        return self.request("POST", path, **kwargs)

    def put(self, path, **kwargs):
        return self.request("PUT", path, **kwargs)

    def patch(self, path, **kwargs):
        return self.request("PATCH", path, **kwargs)

    def delete(self, path, **kwargs):
        return self.request("DELETE", path, **kwargs)

    @staticmethod
    def assert_error(response, status, code):
        """Verifica status e codice del corpo di errore {"error": ..., "message": ...}."""
        assert response.status_code == status, response.text
        body = response.json()
        assert body.get("error") == code, body
        assert isinstance(body.get("message"), str), body


def random_username():
    # Minuscolo: il gateway salva lo username in minuscolo (case-insensitive).
    return "t" + uuid.uuid4().hex[:11]


def _post_retry(api, path, body):
    """POST verso /api/auth/*: il gateway ha un rate limit, quindi riprova con backoff sui 429."""
    response = api.post(path, json=body)
    for attempt in range(4):
        if response.status_code != 429:
            break
        time.sleep(2 ** attempt)
        response = api.post(path, json=body)
    return response


def register_and_login(api, username=None, password=PASSWORD):
    username = username or random_username()
    body = {"username": username, "password": password}
    created = _post_retry(api, "/api/auth/register", body)
    assert created.status_code == 201, created.text
    logged = _post_retry(api, "/api/auth/login", body)
    assert logged.status_code == 200, logged.text
    token = logged.json()["access_token"]
    return {
        "token": token,
        "player_id": created.json()["player_id"],
        "username": username,
        "password": password,
        "headers": {"Authorization": f"Bearer {token}"},
    }


def add_starters(api, player):
    """Sceglie i primi 3 mostri del pool come starter e restituisce il giocatore arricchito."""
    pool = api.get("/api/players/me", headers=player["headers"]).json()["starter_pool"]
    chosen = pool[:3]
    r = api.post("/api/players/me/starters", json={"monster_ids": chosen}, headers=player["headers"])
    assert r.status_code == 201, r.text
    return {
        **player,
        "starter_pool": pool,
        "starter_monster_ids": chosen,
        "starters": r.json(),
    }


@pytest.fixture(scope="session")
def api():
    return Api(BASE_URL)


@pytest.fixture
def fresh_username():
    """Username mai registrato, per i test di registrazione."""
    return random_username()


@pytest.fixture
def new_player(api):
    """Giocatore nuovo, registrato e loggato, senza starter."""
    return register_and_login(api)


@pytest.fixture
def other_player(api):
    """Secondo giocatore, senza starter, per i test di ownership."""
    return register_and_login(api)


@pytest.fixture
def player_with_starters(api, new_player):
    """Giocatore nuovo con i 3 starter gia' scelti."""
    return add_starters(api, new_player)


@pytest.fixture
def other_player_with_starters(api, other_player):
    """Secondo giocatore con starter, per i test di ownership su mostri e team."""
    return add_starters(api, other_player)


@pytest.fixture(scope="session")
def reference(api):
    """Dati di riferimento di sola lettura: tutti i mostri del catalogo e gli elementi.

    Letti una volta sola con un account dedicato. Nessun test li modifica.
    """
    player = register_and_login(api)
    headers = player["headers"]
    monsters = []
    page = 0
    while True:
        r = api.get("/api/monsters", headers=headers, params={"page": page, "size": 100})
        assert r.status_code == 200, r.text
        body = r.json()
        monsters.extend(body["items"])
        page += 1
        if page >= body["total_pages"]:
            break
    elements = api.get("/api/monsters/elements", headers=headers)
    assert elements.status_code == 200, elements.text
    return {"headers": headers, "monsters": monsters, "elements": elements.json()}
