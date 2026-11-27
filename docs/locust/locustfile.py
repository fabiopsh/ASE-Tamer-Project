"""Locust performance tests against the gateway only (TEST-3, requirements §5).

Every simulated user registers its own account, chooses starters and saves a team, then
repeats the core functionalities: catalogue, collection, encounters, battles (wild + boss,
attack/switch/flee), team editing/validation, and history + integrity verification.

    pip install -r docs/locust/requirements.txt
    docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --build --wait
    scripts/test-env.sh
    CA_PATH=.test-secrets/ca.crt locust -f docs/locust/locustfile.py --host https://localhost:8443 \
        --headless -u 50 -r 5 -t 3m --csv docs/locust/results/run

The test override raises the gateway rate limits so concurrent virtual users from one host
are not throttled with 429.
"""
import os
import random
import uuid
from pathlib import Path

from locust import HttpUser, between, task
from locust.exception import StopUser

ROOT = Path(__file__).resolve().parents[2]
CA_PATH = os.environ.get("CA_PATH", str(ROOT / ".test-secrets" / "ca.crt"))
PASSWORD = "locust-pass-123"
MAX_ROUNDS = 100


class Tamer(HttpUser):
    wait_time = between(1, 3)

    def on_start(self):
        self.client.verify = CA_PATH
        self.username = "locust_" + uuid.uuid4().hex[:12]
        self.token = None
        self.refresh_token = None
        self.owned = []
        self.team_id = None
        self.monster_ids = []
        with self.client.post("/api/auth/register", json={"username": self.username, "password": PASSWORD},
                              name="/api/auth/register", catch_response=True) as res:
            if res.status_code != 201:
                res.failure(f"register: {res.status_code} {res.text[:200]}")
                raise StopUser()
        self.login()
        if not self.token:
            raise StopUser()
        self.start_run()

    # ---- helpers --------------------------------------------------------------------------

    def login(self):
        with self.client.post("/api/auth/token", name="/api/auth/token [password]", catch_response=True,
                              data={"grant_type": "password", "username": self.username, "password": PASSWORD}) as res:
            if res.status_code != 200:
                res.failure(f"token: {res.status_code}")
                return False
            data = res.json()
            self.token = data["access_token"]
            self.refresh_token = data.get("refresh_token")
            return True

    def refresh(self):
        if not self.refresh_token:
            return self.login()
        with self.client.post("/api/auth/token", name="/api/auth/token [refresh]", catch_response=True,
                              data={"grant_type": "refresh_token", "refresh_token": self.refresh_token}) as res:
            if res.status_code != 200:
                res.failure(f"refresh: {res.status_code}")
                return self.login()
            data = res.json()
            self.token = data["access_token"]
            self.refresh_token = data.get("refresh_token")
            return True

    @property
    def auth(self):
        return {"Authorization": f"Bearer {self.token}"}

    def get(self, path, name=None, ok=(200,), retry_auth=True):
        """GET with the player token; refreshes once and retries on 401 instead of hiding errors."""
        with self.client.get(path, headers=self.auth, name=name or path, catch_response=True) as res:
            if res.status_code == 401 and retry_auth:
                res.success()
                if self.refresh():
                    return self.get(path, name=name, ok=ok, retry_auth=False)
                return None
            if res.status_code not in ok:
                res.failure(f"{res.status_code} {res.text[:200]}")
                return None
            res.success()
            return res.json() if res.status_code == 200 and res.content else None

    def start_run(self):
        """Choose three starters from the pool and save a team (also after a defeat resets the run)."""
        pool = self.get("/api/players/me/starters")
        if not pool:
            return False
        with self.client.post("/api/players/me/starters", headers=self.auth, json={"monster_ids": pool["pool"][:3]},
                              name="/api/players/me/starters [choose]", catch_response=True) as res:
            if res.status_code == 409:
                res.success()
                return self.load_team()
            if res.status_code != 201:
                res.failure(f"choose: {res.status_code} {res.text[:200]}")
                return False
            self.owned = [s["owned_id"] for s in res.json()["starters"]]
        with self.client.post("/api/teams", headers=self.auth, json={"name": "Locust", "members": self.owned},
                              name="/api/teams [create]", catch_response=True) as res:
            if res.status_code != 201:
                res.failure(f"team: {res.status_code} {res.text[:200]}")
                return False
            self.team_id = res.json()["team_id"]
        return True

    def load_team(self):
        teams = self.get("/api/teams")
        if teams:
            self.team_id = teams[0]["team_id"]
            self.owned = [m["owned_id"] for m in teams[0]["members"]]
            return True
        return False

    # ---- tasks ----------------------------------------------------------------------------

    @task(5)
    def browse_catalogue(self):
        page = self.get(f"/api/monsters?page={random.randint(0, 3)}&size=20", name="/api/monsters")
        if page and page.get("items"):
            self.monster_ids = [m["monster_id"] for m in page["items"]]
        if self.monster_ids:
            monster_id = random.choice(self.monster_ids)
            self.get(f"/api/monsters/{monster_id}", name="/api/monsters/[id]")
            self.client.get(f"/images/monsters/{monster_id:03d}.png", name="/images/monsters/[file]")
        if random.random() < 0.3:
            self.get("/api/elements")
        if random.random() < 0.3:
            self.get("/api/abilities")

    @task(3)
    def view_collection(self):
        page = self.get("/api/collection")
        if page and page.get("items"):
            owned_id = random.choice(page["items"])["owned_id"]
            self.get(f"/api/collection/{owned_id}", name="/api/collection/[owned_id]")

    @task(2)
    def list_encounters(self):
        self.get("/api/encounters", ok=(200, 409))
        self.get("/api/runs/current", ok=(200, 404))

    @task(2)
    def full_battle(self):
        if not self.team_id and not self.start_run():
            return
        templates = self.get("/api/encounters", ok=(200, 409))
        if not templates:
            self.team_id = None
            return
        # Prefer boss encounter when unlocked to cover both wild and boss flows
        boss = next((t for t in templates if t["is_boss"]), None)
        template = boss if boss else random.choice(templates)
        with self.client.post("/api/battles", headers=self.auth, name="/api/battles [start]", catch_response=True,
                              json={"template_id": template["template_id"], "team_id": self.team_id}) as res:
            if res.status_code == 401:
                res.success()
                self.refresh()
                return
            if res.status_code == 409:
                code = res.json().get("error")
                res.success()
                if code == "NO_ACTIVE_RUN":
                    self.team_id = None
                    self.start_run()
                    return
                if code != "BATTLE_IN_PROGRESS":
                    return
                battle = self.get("/api/battles/current", ok=(200, 404))
                if not battle:
                    return
            elif res.status_code != 201:
                res.failure(f"start: {res.status_code} {res.text[:200]}")
                return
            else:
                battle = res.json()

        switched = False
        for round_idx in range(MAX_ROUNDS):
            payload = {"type": "attack"}
            if not switched and round_idx == 1 and random.random() < 0.15:
                bench = next((m["position"] for m in battle.get("team", [])
                              if not m["active"] and not m["knocked_out"]), None)
                if bench is not None:
                    payload = {"type": "switch", "slot": bench}
                    switched = True
            elif round_idx == 2 and not template["is_boss"] and random.random() < 0.05:
                payload = {"type": "flee"}

            with self.client.post("/api/battles/current/actions", headers=self.auth, json=payload,
                                  name="/api/battles/current/actions", catch_response=True) as res:
                if res.status_code == 401:
                    res.success()
                    self.refresh()
                    return
                if res.status_code != 200:
                    res.failure(f"action: {res.status_code} {res.text[:200]}")
                    return
                battle = res.json()
            if battle["status"] == "finished":
                if battle["result"]["outcome"] == "defeat":
                    self.team_id = None
                return

    @task(2)
    def history(self):
        page = self.get("/api/history")
        if page and page.get("items"):
            encounter_id = random.choice(page["items"])["encounter_id"]
            self.get(f"/api/history/{encounter_id}", name="/api/history/[encounter_id]")
        if random.random() < 0.25:
            self.get("/api/history/verify")

    @task(1)
    def validate_and_update_team(self):
        if not self.owned:
            return
        with self.client.post("/api/teams/validate", headers=self.auth, json={"members": self.owned},
                              name="/api/teams/validate", catch_response=True) as res:
            if res.status_code == 401:
                res.success()
                self.refresh()
                return
            if res.status_code != 200:
                res.failure(f"validate: {res.status_code}")
                return
            if not res.json().get("valid"):
                res.success()
                return
        if self.team_id and random.random() < 0.2:
            with self.client.put(f"/api/teams/{self.team_id}", headers=self.auth,
                                 json={"name": "Locust Squad", "members": list(reversed(self.owned))},
                                 name="/api/teams/[id] [update]", catch_response=True) as res:
                if res.status_code in (200, 404, 409):
                    res.success()
                else:
                    res.failure(f"update: {res.status_code}")
