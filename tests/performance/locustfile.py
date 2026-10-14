"""Test di carico Locust per Monster Tamers, attraverso il gateway HTTPS (certificato self-signed).

Esempi (dalla radice del repo):
    locust -f tests/performance/locustfile.py --host https://localhost:8443
    locust -f tests/performance/locustfile.py --host https://localhost:8443 --headless -u 50 -r 5 -t 1m

Ogni utente virtuale si registra, fa login, sceglie gli starter e crea un team
in on_start, poi esegue i task in base ai pesi.

Nota: il gateway applica un rate limit su /api/auth/. Con molti utenti che si
registrano insieme la registrazione puo' ricevere 429: il client riprova con backoff.
"""

import random
import time
import uuid

# Locust va importato prima di urllib3/ssl: fa il monkey patch di gevent sul modulo ssl.
from locust import HttpUser, between, task
from locust.exception import StopUser

import urllib3

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

PASSWORD = "Tamer-Pass-123"
RARITIES = ["common", "rare", "legendary"]
SEARCH_FRAGMENTS = ["a", "e", "o", "ar", "on", "ra"]
MAX_RETRIES = 3


class MonsterTamerUser(HttpUser):
    wait_time = between(1, 3)

    def on_start(self):
        # Certificato self-signed del gateway in sviluppo.
        self.client.verify = False
        self.team_id = None
        self.elements = []
        self.username = f"load_{uuid.uuid4().hex[:10]}"
        self._register_and_login()
        self._load_reference_data()
        self._prepare_player()

    # --- setup -------------------------------------------------------------

    @staticmethod
    def _with_retry(send):
        """Ritenta la chiamata se il gateway risponde 429 (rate limit)."""
        response = send()
        for attempt in range(MAX_RETRIES):
            if response.status_code != 429:
                break
            time.sleep(2 ** attempt)
            response = send()
        return response

    @staticmethod
    def _json(response, default=None):
        try:
            return response.json()
        except ValueError:
            return default

    def _register_and_login(self):
        body = {"username": self.username, "password": PASSWORD}
        registered = self._with_retry(
            lambda: self.client.post("/api/auth/register", json=body, name="/api/auth/register")
        )
        if registered.status_code != 201:
            raise StopUser()
        logged = self._with_retry(
            lambda: self.client.post("/api/auth/login", json=body, name="/api/auth/login")
        )
        if logged.status_code != 200:
            raise StopUser()
        self.client.headers["Authorization"] = f"Bearer {logged.json()['access_token']}"

    def _load_reference_data(self):
        r = self.client.get("/api/monsters/elements", name="/api/monsters/elements")
        self.elements = (self._json(r, {}) or {}).get("cycle", [])

    def _prepare_player(self):
        profile = self._json(self.client.get("/api/players/me", name="/api/players/me"), {}) or {}
        pool = profile.get("starter_pool") or []
        if len(pool) < 3:
            raise StopUser()

        starters = self.client.post(
            "/api/players/me/starters",
            json={"monster_ids": random.sample(pool, 3)},
            name="/api/players/me/starters",
        )
        if starters.status_code != 201:
            raise StopUser()
        owned_ids = [m["owned_id"] for m in starters.json()]

        team = self.client.post(
            "/api/teams",
            json={"name": f"Team {uuid.uuid4().hex[:6]}", "owned_ids": owned_ids},
            name="/api/teams",
        )
        if team.status_code == 201:
            self.team_id = team.json()["team_id"]

    # --- tasks ---------------------------------------------------------------

    @task(4)
    def browse_catalog(self):
        roll = random.random()
        if roll < 0.4:
            self.client.get(
                "/api/monsters",
                params={"page": random.randint(0, 4), "size": 20},
                name="/api/monsters [paged]",
            )
        elif roll < 0.6 and self.elements:
            self.client.get(
                "/api/monsters",
                params={"element": random.choice(self.elements), "size": 20},
                name="/api/monsters [element]",
            )
        elif roll < 0.8:
            self.client.get(
                "/api/monsters",
                params={"rarity": random.choice(RARITIES), "size": 20},
                name="/api/monsters [rarity]",
            )
        elif roll < 0.9:
            self.client.get(
                "/api/monsters",
                params={"q": random.choice(SEARCH_FRAGMENTS), "size": 20},
                name="/api/monsters [search]",
            )
        else:
            self.client.get(
                f"/api/monsters/{random.randint(1, 100)}",
                name="/api/monsters/[id]",
            )

    @task(3)
    def view_collection(self):
        self.client.get(
            "/api/collection",
            params={"sort": random.choice(["level", "recent", "species"])},
            name="/api/collection [sort]",
        )

    @task(3)
    def view_board(self):
        self.client.get("/api/encounters", name="/api/encounters")

    @task(1)
    def start_encounter(self):
        if not self.team_id:
            return
        board = self.client.get("/api/encounters", name="/api/encounters")
        if board.status_code != 200:
            return
        open_wild = [
            e for e in (self._json(board, {}) or {}).get("encounters", [])
            if e.get("kind") == "wild" and e.get("status") == "open"
        ]
        if not open_wild:
            return
        offer = random.choice(open_wild)
        self.client.post(
            f"/api/encounters/{offer['encounter_id']}/start",
            json={"team_id": self.team_id},
            name="/api/encounters/[id]/start",
        )

    @task(2)
    def view_history(self):
        self.client.get(
            "/api/history",
            params={"page": 0, "size": 20},
            name="/api/history",
        )
