# 12 — Documentation and delivery

## Required content of `docs/` (requirements §3)

| Item | Location | Task | Phase |
|---|---|---|---|
| Final report (PDF, course template) | `docs/report/` | DOC-2 | P2 |
| OpenAPI of the gateway API and of every internal service | `docs/openapi/gateway.yaml`, `docs/openapi/<svc>.yaml` | DOC-1 | P1 (updated P2) |
| Bruno/Postman JSON collections (≥ 3 internal services; we do 6) | `docs/tests/json/` (source in `docs/tests/bruno/`) | TEST-1 | P1 |
| Locust file(s) | `docs/locust/` | TEST-3 | P2 |
| Copy of the GitHub Actions workflow(s) | `docs/ci/ci.yml` | CI-1 | P1 |
| Completed backlog with acceptance criteria and dependencies | `docs/backlog/` | DOC-1 | P1 (completed P2) |

Plus the root `README.md` with **Get Started** (install, build, run backend, run main tests, run frontend) — INFRA-8.

---

## DOC-1 — Backlog, OpenAPI, architecture update, prototype delivery · P1

**Depends on:** all P1 tasks (run continuously, finalise on 09–10/11)

**Backlog**

- [x] `docs/backlog/acceptance_criteria.md`: one section per user story in scope — the 18 red, US-05 and US-25 (yellow), and the implemented green ones (US-07, US-21, US-22; later US-04, US-13). For each: dependencies by ID (or "none"), AC in **Given / When / Then**, and the tests that verify each AC. Drafts are in the area files of this plan.
- [x] Update the old AC that this plan changed: US-12 (wild level instead of level 1), US-05 (404 instead of 403), US-03 (progress kept until the run is lost; past runs visible).
- [x] `docs/backlog/Monster_Tamers_Backlog_completed.xlsx`: copy of the course file with the columns *Acceptance criteria*, *Dependencies*, *Implemented (P1/P2/no)* filled from the markdown file. Regenerate with `scripts/build-backlog-xlsx.py` after changing the markdown.

**OpenAPI**

- [x] Annotate resources (`@Operation`, `@APIResponse` for every status, `@Tag(name = "<service>")` or `"<service>-internal"`), and security schemes. Bearer JWT via `quarkus.smallrye-openapi.security-scheme=jwt` (401/403 added automatically to `@RolesAllowed` operations); the OAuth2 grants are described in the auth spec description (Quarkus only generates the implicit flow); `operationId` = `Class_method`.
- [x] `scripts/export-openapi.sh`: `mvn -f backend/pom.xml package -DskipTests`, then copy each `backend/<svc>/target/openapi/openapi.yaml` to `docs/openapi/<svc>.yaml` (generated at build time by `quarkus.smallrye-openapi.store-schema-directory`).
- [x] `scripts/openapi/build-gateway-openapi.mjs` (Node, `yaml` 2.9.1 pinned in `scripts/openapi/package.json`): drop `/internal/*` paths, merge the six specs (prefix schema names with the service to avoid collisions), set `servers: [https://localhost:8443]` → `docs/openapi/gateway.yaml`.

**Architecture update**

- [x] Update `docs/architettura/architettura_backend.md` with the "Changes to the architecture" table of the [plan README](README.md#changes-to-the-architecture) and the decision log P-01…P-21 (keep the document in Italian or translate, as the group prefers).
- [x] Mark superseded parts of `docs/design/area_giocatori_mostri.md`.

**README**

- [x] General project description (what the game is, core loop, roguelike runs, architecture picture, links to `docs/`), as the prototype delivery asks.
- [ ] Get Started verified on a fresh clone by someone who did not write it.

**Prototype delivery (10/11)**

- [ ] All P1 "must" tasks merged; CI green.
- [ ] Fresh clone → `docker compose up --build` → frontend → full core loop.
- [ ] JSON collections re-exported (`cd docs/tests/bruno && npm run export`); OpenAPI regenerated (`scripts/export-openapi.sh`).
- [ ] Tag `v0.1-prototype` and send the repository link through the channel indicated by the teacher (check Moodle / email).
- [ ] Collect the teacher's feedback into new issues.

---

## DOC-2 — Final report and final delivery · P2

**Depends on:** all P2 tasks

**Report** (course template, PDF in `docs/report/`). Suggested content, keep it concise:

- [ ] Project overview, game rules (core loop, runs, stages, battle, rewards), and the choices with their rationale (decision tables A, R, P).
- [ ] Architecture: components diagram, per-service responsibilities and data, main sequence diagrams (registration, starters, battle, end of battle, reset), resilience (timeouts, retries, circuit breakers, outbox, idempotency).
- [ ] **Architectural smells analysis** (from the course lesson). Expected verdicts, to verify against the slides:

  | Smell | Status |
  |---|---|
  | Endpoint-based service interactions | Present, allowed by the requirements (services addressed by DNS name) |
  | Wobbly service interactions | Mitigated: timeouts, retries on idempotent calls, circuit breakers, 503 on failure |
  | No API gateway | Absent: Nginx gateway |
  | Shared persistence | Absent: one database per service |
  | Multiple services in one container | Absent |
  | Single-layer teams | Absent: work split by service (vertical) |
  | Others in the course list | Check each one; note the players ↔ encounters call cycle and how it is handled (EN-7) |

- [ ] Client-side assumptions (list in [09-frontend.md](09-frontend.md#client-side-assumptions-copy-into-the-final-report)).
- [ ] Security: §6 compliance table and self-attack results (SEC-1), Semgrep/Trivy outcome, accepted exceptions.
- [ ] Testing: strategy, Bruno coverage matrix, e2e, Locust results and analysis, AC traceability.
- [ ] Known limitations (in-memory battles and single `encounters` instance, R19; others found during development).
- [ ] Optional features implemented (green stories) and how they are documented.

**Final delivery (15/12)**

- [ ] Everything in the §3 table present and up to date.
- [ ] CI fully green including security jobs.
- [ ] Fresh-clone test of README Get Started on a different machine (macOS and Linux if possible).
- [ ] Tag `v1.0-final`; submit as instructed.
- [ ] Each member reviews the whole report and can explain design, deployment, tests and rationale (individual oral evaluation).

---

## Implementation notes (09/11/2026)

| Task | Note |
|---|---|
| DOC-1 AC | Added US-05, US-25 (P1 part, P2 rows marked "not implemented yet") and US-28; US-03 gained the "run lost or abandoned" criterion. US-12 and the 404 rule were already updated. |
| DOC-1 OpenAPI | 7 specs in `docs/openapi/` (6 services + gateway: 31 public operations, no `/internal/*`). Valid with `@redocly/cli lint --extends=minimal`; remaining warnings are missing tag descriptions. `scripts/export-openapi.sh` runs Maven from `backend/` (not `-f <path>`): the Quarkus plugin rejects an absolute path with an apostrophe. |
| DOC-1 architecture | Section 10 of `architettura_backend.md` (Italian): changes table, decision log P-01…P-21, other implementation choices. The `.tex`/`.pdf` are still the 15/10 version. |
| Still to do (people) | Get Started on a fresh clone by someone who did not write it; CI green; tag `v0.1-prototype`; send the link; collect feedback. |

