"""Locust performance tests against the gateway only (TEST-3, requirements §5).

Every simulated user registers its own account, chooses starters and saves a team, then
repeats the core functionalities: catalogue, collection, encounters, battles, history.

    pip install -r docs/locust/requirements.txt
    docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --build --wait
    scripts/test-env.sh
    CA_PATH=.test-secrets/ca.crt locust -f docs/locust/locustfile.py --host https://localhost:8443 \
        --headless -u 50 -r 5 -t 3m --csv docs/locust/results/run

The test override raises the gateway rate limit on /api/auth/*: without it the registrations
of many users from one IP are throttled (429).
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
        self.owned = []
        self.team_id = None
        self.monster_ids = []
        self.encounter_ids = []
        with self.client.post("/api/auth/register", json={"username": self.username, "password": PASSWORD},
                              name="/api/auth/register", catch_response=True) as res:
            if res.status_code != 201:
                res.failure(f"register: {res.status_code} {res.text[:200]}")
                raise StopUser()  # without an account every other request would fail
        self.login()
        if not self.token:
            raise StopUser()
        self.start_run()

    # ---- helpers --------------------------------------------------------------------------

    def login(self):
        with self.client.post("/api/auth/token", name="/api/auth/token", catch_response=True,
                              data={"grant_type": "password", "username": self.username, "password": PASSWORD}) as res:
            if res.status_code != 200:
                res.failure(f"token: {res.status_code}")
                return
            self.token = res.json()["access_token"]

    @property
    def auth(self):
        return {"Authorization": f"Bearer {self.token}"}

    def get(self, path, name=None, ok=(200,)):
        """GET with the player token; re-login once on 401 (token expiry during long runs)."""
        with self.client.get(path, headers=self.auth, name=name or path, catch_response=True) as res:
            if res.status_code == 401:
                self.login()
                res.success()
                return None
            if res.status_code not in ok:
                res.failure(f"{res.status_code} {res.text[:200]}")
                return None
            res.success()  # expected statuses other than 2xx (e.g. 404 with no active run)
            return res.json() if res.status_code == 200 and res.content else None

    def start_run(self):
        """Choose three starters from the pool and save a team (also after a defeat resets the run)."""
        pool = self.get("/api/players/me/starters")
        if not pool:
            return False
        with self.client.post("/api/players/me/starters", headers=self.auth, json={"monster_ids": pool["pool"][:3]},
                              name="/api/players/me/starters [choose]", catch_response=True) as res:
            if res.status_code == 409:  # already chosen for this run
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
            # Sprites are public (P-16): no token
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
        # 409 NO_ACTIVE_RUN / 404: a defeat reset the run and the user has not chosen new starters yet
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
        template = next((t for t in templates if not t["is_boss"]), templates[0])
        with self.client.post("/api/battles", headers=self.auth, name="/api/battles [start]", catch_response=True,
                              json={"template_id": template["template_id"], "team_id": self.team_id}) as res:
            if res.status_code == 409:
                code = res.json().get("error")
                res.success()
                if code == "NO_ACTIVE_RUN":
                    self.team_id = None
                    self.start_run()
                    return
                if code != "BATTLE_IN_PROGRESS":
                    return
            elif res.status_code != 201:
                res.failure(f"start: {res.status_code} {res.text[:200]}")
                return
        for _ in range(MAX_ROUNDS):
            with self.client.post("/api/battles/current/actions", headers=self.auth, json={"type": "attack"},
                                  name="/api/battles/current/actions", catch_response=True) as res:
                if res.status_code != 200:
                    res.failure(f"action: {res.status_code} {res.text[:200]}")
                    return
                battle = res.json()
            if battle["status"] == "finished":
                self.encounter_ids.append(battle["encounter_id"])
                if battle["result"]["outcome"] == "defeat":
                    self.team_id = None  # the run was reset: new starters and team next time
                return

    @task(2)
    def history(self):
        page = self.get("/api/history")
        if page and page.get("items"):
            encounter_id = random.choice(page["items"])["encounter_id"]
            self.get(f"/api/history/{encounter_id}", name="/api/history/[encounter_id]")

    @task(1)
    def validate_team(self):
        if not self.owned:
            return
        with self.client.post("/api/teams/validate", headers=self.auth, json={"members": self.owned},
                              name="/api/teams/validate", catch_response=True) as res:
            if res.status_code != 200:
                res.failure(f"validate: {res.status_code}")
            elif not res.json().get("valid"):
                # A defeat may have archived the members since the last check
                res.success()
