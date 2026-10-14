# Monster Tamers — Architettura e contratto API

Documento di riferimento per l'implementazione. Ogni servizio deve rispettarlo alla lettera: endpoint, nomi dei campi, codici HTTP.

## 1. Panoramica

```
browser ──HTTPS──▶ gateway (nginx: TLS, frontend statico, reverse proxy /api/* e /images/*)
                     │
     ┌───────┬───────┼─────────┬──────────┬────────────┐
     ▼       ▼       ▼         ▼          ▼            ▼
   auth   players  monsters   teams   encounters    history      (Quarkus 3.27.6, Java 21)
     │       │       │         │          │            │
     └───────┴───────┴────┬────┴──────────┴────────────┘
                          ▼
              postgres (un database e un utente per servizio)
```

| Servizio | Porta interna | DB | Responsabilità | User stories |
|---|---|---|---|---|
| auth | 8080 | `auth` | account, password, JWT | US-01, 02, 05 |
| players | 8080 | `players` | profilo, stage, starter pool | US-03, 04, 06, 07 |
| monsters | 8080 | `monsters` | catalogo, abilità, immagini, collezione, XP | US-09, 10, 11, 12, 13 |
| teams | 8080 | `teams` | team e validazione | US-14, 15, 16 |
| encounters | 8080 | `encounters` | encounter disponibili, simulazione battaglia, ricompense | US-17, 18, 19, 20 |
| history | 8080 | `history` | storico encounter, catena di hash anti-manomissione | US-23, 24, 25 |

Gateway: `/api/<svc>/...` → `http://<svc>:8080/<svc>/...` (stesso prefisso), eccetto monsters che espone anche `/collection` (gateway `/api/collection/` → `monsters:8080/collection/`) e `/images/` (→ `monsters:8080/images/`). Il gateway **non** inoltra mai `/internal/*` né `/q/*`.

## 2. Convenzioni comuni

- **JSON in snake_case** ovunque: `quarkus.jackson.property-naming-strategy=SNAKE_CASE`. Date ISO-8601 UTC (`Instant`).
- **Errori**: corpo `{"error": "<codice>", "message": "<testo>", "details": [ ... ]}` (`details` opzionale, lista di stringhe). Codici: 400 `validation_error`, 401 `unauthorized`, 403 `forbidden`, 404 `not_found`, 409 `conflict`, 429 `too_many_requests`, 502/503 `upstream_error`. Ogni servizio ha `ExceptionMapper` per `WebApplicationException`, `ConstraintViolationException` e un proprio `ApiException(status, error, message, details)`.
- **Autenticazione**: `Authorization: Bearer <JWT>` RS256. Claims: `iss=monster-tamers`, `sub=<player_id UUID>`, `upn=<username>`, `groups=["player"]`, `exp` = 1 h. Endpoint pubblici annotati `@RolesAllowed("player")`; il `player_id` si prende **sempre** da `jwt.getSubject()`, mai da parametri del client.
- **Chiamate interne**: path `/internal/...`, annotati `@PermitAll`, protetti da un `ContainerRequestFilter` che per ogni path che inizia con `internal` richiede header `X-Internal-Token` uguale a config `internal.token` (altrimenti 401). Client REST: MicroProfile REST Client (`quarkus-rest-client-jackson`) con `@ClientHeaderParam(name="X-Internal-Token", value="${internal.token}")`. Errori di un servizio a valle → 503 `upstream_error`.
- **Ownership**: risorsa di un altro giocatore → **403**; id inesistente → **404**.
- **Validazione**: Bean Validation (`quarkus-hibernate-validator`) sui DTO in ingresso + controlli di dominio.
- **Persistenza**: Hibernate ORM Panache, `quarkus.hibernate-orm.schema-management.strategy=update`. Id UUID generati dal servizio.
- **OpenAPI**: `quarkus-smallrye-openapi`, `quarkus.smallrye-openapi.path=/<svc>/openapi`, swagger UI `quarkus.swagger-ui.always-include=true`, `quarkus.swagger-ui.path=/<svc>/swagger-ui`, `quarkus.swagger-ui.urls.default=/api/<svc>/openapi`.
- **Health**: `quarkus-smallrye-health`, usato dal healthcheck di compose su `/q/health/ready`.
- **Test**: unit test JUnit 5 **puri** (nessun `@QuarkusTest`, nessun DB) sulla logica di dominio; girano in `mvn package` dentro la build Docker.

### Configurazione (`application.properties`, valori da env con default per sviluppo)

```properties
quarkus.http.port=8080
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=${DB_URL:jdbc:postgresql://localhost:5432/<svc>}
quarkus.datasource.username=${DB_USER:<svc>}
quarkus.datasource.password=${DB_PASSWORD:<svc>}
quarkus.hibernate-orm.schema-management.strategy=update
quarkus.jackson.property-naming-strategy=SNAKE_CASE
mp.jwt.verify.publickey.location=${JWT_PUBLIC_KEY:file:/secrets/jwt-public.pem}   # il prefisso file: è obbligatorio
mp.jwt.verify.issuer=monster-tamers
internal.token=${INTERNAL_TOKEN:dev-internal-token}
quarkus.rest-client.monsters.url=${MONSTERS_URL:http://monsters:8080}
quarkus.rest-client.players.url=${PLAYERS_URL:http://players:8080}
quarkus.rest-client.teams.url=${TEAMS_URL:http://teams:8080}
quarkus.rest-client.history.url=${HISTORY_URL:http://history:8080}
quarkus.log.level=INFO
```

(ogni servizio include solo i client che usa)

### Struttura di un servizio

```
backend/<svc>/
  pom.xml          groupId it.unipi.ase.tamers, artifactId <svc>, Quarkus BOM 3.27.6, Java 21
  Dockerfile       multi-stage: maven:3.9-eclipse-temurin-21 → eclipse-temurin:21-jre-alpine, utente non root
  README.md
  src/main/java/it/unipi/ase/tamers/<svc>/...
  src/main/resources/application.properties
  src/test/java/...
```

Compilazione e test senza Maven locale (dalla root del repo, Git Bash):

```bash
MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W)/backend/<svc>:/src" -v ase-m2:/root/.m2 -w /src maven:3.9-eclipse-temurin-21 mvn -B -q package
```

## 3. auth

Entità `Account`: `player_id` UUID PK, `username` unique (case-insensitive: salvato in minuscolo), `password_hash` (bcrypt, costo 12, libreria `org.mindrot:jbcrypt:0.4`), `created_at`, `last_login`, `failed_attempts`, `locked_until`.

| Metodo | Path | Auth | Corpo → Risposta |
|---|---|---|---|
| POST | `/auth/register` | no | `{username, password}` → **201** `{player_id, username}` |
| POST | `/auth/login` | no | `{username, password}` → **200** `{access_token, token_type:"Bearer", expires_in:3600, player_id, username}` |
| GET | `/auth/me` | JWT | → `{player_id, username, created_at, last_login}` |

- `username`: regex `^[A-Za-z0-9_]{3,20}$`; `password`: 8–72 caratteri. Errore → 400.
- Username esistente → 409 `conflict`.
- Registrazione: in transazione crea l'account, poi chiama `POST players:/internal/players {player_id, username}`; se fallisce → rollback e 503.
- Login errato (utente inesistente o password sbagliata) → 401 con messaggio generico `"invalid username or password"`. 5 tentativi falliti → account bloccato 5 minuti → 429.
- JWT firmato con `smallrye-jwt-build` (`smallrye.jwt.sign.key.location=${JWT_PRIVATE_KEY:/secrets/jwt-private.pem}`).

## 4. players

Entità `Player`: `player_id` UUID PK (= sub), `display_name`, `avatar_monster_id` (int, nullable), `stage` int (1..11; 11 = gioco completato), `starter_pool` (lista di 10 int, `@ElementCollection` o stringa CSV), `starters_chosen` bool, `created_at`.

Costante `MAX_STAGE = 10`.

| Metodo | Path | Auth | Corpo → Risposta |
|---|---|---|---|
| GET | `/players/me` | JWT | → `PlayerProfile` |
| PATCH | `/players/me` | JWT | `{display_name?, avatar_monster_id?}` → `PlayerProfile` |
| POST | `/players/me/starters` | JWT | `{monster_ids:[a,b,c]}` → **201** `[OwnedMonster]` |
| POST | `/internal/players` | interno | `{player_id, username}` → **201** `PlayerProfile` (idempotente: se esiste restituisce quello) |
| GET | `/internal/players/{player_id}` | interno | → `PlayerProfile` |
| POST | `/internal/players/{player_id}/advance-stage` | interno | `{cleared_stage}` → `PlayerProfile` |

`PlayerProfile`:
```json
{"player_id":"…","display_name":"ash","avatar_monster_id":12,"stage":1,"max_stage":10,"game_completed":false,
 "starters_chosen":false,"starter_pool":[3,8,…],"created_at":"…",
 "progress":{"collection_size":3,"species_owned":3,"encounters_total":5,"encounters_won":4}}
```

- Creazione profilo: `display_name = username`, `stage = 1`, `starter_pool` = 10 `monster_id` **common** distinti estratti a caso dalla lista di `GET monsters:/internal/monsters` (filtrare `rarity == "common"`).
- `progress` solo in `GET /players/me`: aggregato da `GET monsters:/internal/collection/{player_id}/stats` e `GET history:/internal/history/{player_id}/stats`; se un servizio non risponde i suoi campi sono `null` (la risposta resta 200).
- PATCH: `display_name` 3–20 caratteri `^[A-Za-z0-9_ ]+$`; `avatar_monster_id` deve essere una specie posseduta (`GET monsters:/internal/collection/{player_id}` contiene quel `monster_id`), altrimenti 400.
- Starter: esattamente 3 id distinti, tutti nel pool, altrimenti 400; già scelti → 409. Chiama `POST monsters:/internal/collection/batch {player_id, monster_ids, source:"starter"}`, poi `starters_chosen = true`.
- `advance-stage`: se `cleared_stage == stage` e `stage <= MAX_STAGE` → `stage + 1`; altrimenti invariato (idempotente).

## 5. monsters

Seed caricato all'avvio se la tabella è vuota da `seed/monsters.json` e `seed/abilities.json` (classpath; il `pom.xml` include la cartella `seed/` come risorsa con `targetPath seed`, e `seed/images` con `targetPath META-INF/resources/images` così Quarkus serve `/images/monsters/NNN.png` come file statici pubblici). `seed/elements.json` → ciclo degli elementi.

Entità: `Monster` (campi del seed), `Ability` (campi del seed: `ability_id, name, scope, element, effect_type, effect_value, trigger, description`), `OwnedMonster` (`owned_id` UUID, `player_id`, `monster_id`, `level`, `xp`, `source` `starter|encounter`, `encounter_id` UUID nullable, `obtained_at`).

Regole di crescita:
- `stat_effettiva = round(base + growth × (level − 1))` per hp, attack, defense, speed.
- `xp_to_next(level) = 25 × level`; `MAX_LEVEL = 50`. Al raggiungimento della soglia: `xp -= soglia`, `level += 1` (ciclo, più livelli in una volta). A `MAX_LEVEL` l'xp resta 0.

| Metodo | Path | Auth | Risposta |
|---|---|---|---|
| GET | `/monsters?element=&rarity=&ids=1,2,3&q=&page=0&size=20` | JWT | `{items:[MonsterSummary], page, size, total, total_pages}` (size max 100, ordinati per `monster_id`; `q` = ricerca nel nome, case-insensitive) |
| GET | `/monsters/{monster_id}` | JWT | `MonsterDetail` o 404 |
| GET | `/monsters/abilities` | JWT | `[Ability]` |
| GET | `/monsters/elements` | JWT | contenuto di `elements.json` |
| GET | `/collection?element=&rarity=&sort=level` | JWT | `[OwnedMonster]` del giocatore (sort: `level` desc default, `recent`, `species`) |
| GET | `/collection/{owned_id}` | JWT | `OwnedMonsterDetail`; altrui → 403, inesistente → 404 |
| GET | `/images/monsters/NNN.png` | pubblico | sprite 64×64 |
| GET | `/internal/monsters` | interno | `[MonsterDetail]` tutti e 100 |
| GET | `/internal/collection/{player_id}` | interno | `[OwnedMonster]` |
| GET | `/internal/collection/{player_id}/owned?ids=<uuid>,<uuid>` | interno | `[OwnedMonster]` solo quelli del giocatore tra gli id dati, nello stesso ordine |
| GET | `/internal/collection/{player_id}/stats` | interno | `{collection_size, species_owned}` |
| POST | `/internal/collection` | interno | `{player_id, monster_id, source, encounter_id?}` → **201** `OwnedMonster` (level 1, xp 0) |
| POST | `/internal/collection/batch` | interno | `{player_id, monster_ids:[…], source}` → **201** `[OwnedMonster]` (transazione unica) |
| POST | `/internal/collection/xp` | interno | `{player_id, awards:[{owned_id, xp}]}` → `[{owned_id, old_level, new_level, xp, xp_to_next}]` (owned non del giocatore → 400) |

Il client **non** ha endpoint per aggiungere mostri (US-12).

`MonsterSummary`: `{monster_id, name, element, rarity, image_url}`

`MonsterDetail`: tutti i campi del seed (`monster_id, name, element, rarity, description, image_url, base_hp, base_attack, base_defense, base_speed, growth_hp, growth_attack, growth_defense, growth_speed, ability_id`) + `ability: Ability` + `strong_against`, `weak_against` (elemento).

`OwnedMonster`:
```json
{"owned_id":"…","monster_id":12,"name":"…","element":"dream","rarity":"common","image_url":"/images/monsters/012.png",
 "level":3,"xp":10,"xp_to_next":75,"stats":{"hp":44,"attack":41,"defense":39,"speed":50},
 "ability":{Ability},"source":"starter","encounter_id":null,"obtained_at":"…"}
```
`OwnedMonsterDetail` = `OwnedMonster` + `description`, `base_stats{hp,attack,defense,speed}`, `growth{hp,attack,defense,speed}`, `strong_against`, `weak_against`.

## 6. teams

Entità `Team`: `team_id` UUID, `player_id`, `name`, `owned_ids` (lista ordinata di UUID, l'ordine è l'ordine di battaglia), `created_at`, `updated_at`.

Regole (`TeamRules`, logica pura testata):
- `name` 1–30 caratteri, non vuoto dopo trim.
- da `MIN_SIZE = 1` a `MAX_SIZE = 3` mostri, `owned_ids` distinti.
- tutti i mostri posseduti dal giocatore (`GET monsters:/internal/collection/{player_id}/owned?ids=…` deve restituirli tutti).
- massimo `MAX_TEAMS = 5` team per giocatore (solo in creazione).
- messaggi di errore leggibili, es. `"a team needs between 1 and 3 monsters"`, `"monster <id> is not in your collection"`, `"duplicate monster <id>"`.

| Metodo | Path | Auth | Corpo → Risposta |
|---|---|---|---|
| GET | `/teams` | JWT | `[TeamView]` |
| POST | `/teams` | JWT | `{name, owned_ids}` → **201** `TeamView`; non valido → 400 con `details` = lista errori |
| GET | `/teams/{team_id}` | JWT | `TeamView` (403/404) |
| PUT | `/teams/{team_id}` | JWT | `{name, owned_ids}` → `TeamView` |
| DELETE | `/teams/{team_id}` | JWT | **204** |
| POST | `/teams/validate` | JWT | `{name?, owned_ids}` → **200** `{valid, errors:[…]}` |
| GET | `/internal/teams/{team_id}?player_id=` | interno | `{team_id, player_id, name, owned_ids}`; di un altro giocatore o inesistente → 404 |

`TeamView`: `{team_id, name, owned_ids, members:[OwnedMonster], valid, errors:[…], created_at, updated_at}` (membri presi da monsters; `valid`/`errors` ricalcolati in lettura).

## 7. encounters

### Regole di gioco

- Il giocatore ha uno `stage` (players). Per ogni stage c'è una **bacheca** con 3 encounter `wild` e 1 `guardian` (boss dello stage). Al completamento del gioco (`stage == 11`) la bacheca ha solo wild di livello stage 10.
- Livelli: wild dello stage `s` hanno livello casuale in `[3s−2, 3s]`; il guardian ha livello `3s + 2`.
- Specie wild: stage 1–3 solo common; 4–6 common 70% / rare 30%; 7–10 common 30% / rare 70%; mai legendary.
- Guardian deterministico per stage (uguale per tutti): stage 1–5 rare, stage 6–10 legendary, elementi diversi a rotazione; tra le specie candidate si sceglie quella con attacco base più basso (tabella in `BoardGenerator`).
- HP dei nemici: wild × 1.5, guardian × 1.4 (valori tarati con `BalanceTest`: con × 2.5 il guardian dello stage 1 era imbattibile).
- Ricompense alla vittoria: XP a ogni membro del team `= 10 × livello_nemico` (guardian × 2); wild → cattura con probabilità common 60% / rare 35%; guardian → cattura garantita e `advance-stage`. Sconfitta: XP al 30%, nessuna cattura. Il mostro catturato entra a livello 1 con `source=encounter` e `encounter_id = run_id`.
- Wild vinto → l'offerta passa a `completed` e ne viene generata una nuova; wild perso → resta aperta (si può ritentare).

### Battaglia (motore puro e deterministico, `BattleEngine`, seed salvato)

- Il team combatte in ordine: il primo membro vivo affronta il nemico; quando va KO entra il successivo. Vittoria se il nemico va KO, sconfitta se cade tutto il team o dopo 100 round.
- HP di battaglia = `2 × hp + 10` (nemici poi × 1.5 wild / × 1.4 guardian, arrotondato).
- In ogni round agisce prima chi ha più `speed` (parità: il giocatore).
- Danno = `max(1, round(atk × atk / (atk + def) × varianza[0.85,1.0] × elemento × modificatori))`; critico 6.25% × 1.5.
- Elemento (`elements.json`): forte 1.5, debole 0.67, altrimenti 1.
- Abilità: `effect_type` × `trigger` dal seed. Semantica:
  - trigger: `always`; `first_turn` (prima azione del portatore); `hp_above_50` / `hp_below_50` (HP del portatore); `on_hit` (quando il portatore viene colpito); `on_attack` (quando attacca); `once` (una volta per battaglia); `vs_faster` (avversario più veloce).
  - effetti (`effect_value` in %): `damage_boost` +v% danno inflitto; `damage_reduction` −v% danno subito; `dodge` v% di schivare; `heal` cura v% degli HP massimi (con `hp_below_50` una sola volta, con `always` a fine di ogni propria azione); `speed_boost` +v% speed; `reflect` rimanda v% del danno subito; `stun` v% di far saltare il turno all'avversario; `weaken` −v% attacco avversario (applicato una volta all'ingresso); `ignore_disadvantage` il moltiplicatore 0.67 diventa 1; `revive` una volta, al KO torna con v% HP; `random_boost` danno × casuale in `[1−v%, 1+2v%]`; `invert_advantage` scambia forte/debole negli scontri del portatore; `copy_ability` all'ingresso copia l'abilità dell'avversario.
- Log degli eventi per l'animazione nel frontend; sempre presenti `round`, `type`, `actor`, `text`, gli altri campi solo se pertinenti:
```json
{"round":1,"type":"enter|attack|dodge|ability|stun|skip|heal|revive|faint|victory|defeat",
 "actor":"team:0|wild","target":"team:0|wild","damage":12,"critical":false,"effectiveness":"strong|weak|neutral",
 "ability":"Lullaby","actor_hp":80,"target_hp":40,"text":"Snoozle hits Gearbit for 12!"}
```

### API

| Metodo | Path | Auth | Corpo → Risposta |
|---|---|---|---|
| GET | `/encounters` | JWT | `{stage, game_completed, encounters:[EncounterOffer]}` (genera la bacheca se mancano offerte) |
| GET | `/encounters/{encounter_id}` | JWT | `EncounterOffer` (403/404) |
| POST | `/encounters/{encounter_id}/start` | JWT | `{team_id}` → **201** `EncounterResult` |
| GET | `/encounters/runs/{run_id}` | JWT | `EncounterResult` (403/404) |

`start`: legge il team (`GET teams:/internal/teams/{team_id}?player_id=`, 404 → 400 `"team not found"`), i membri con statistiche (`GET monsters:/internal/collection/{player_id}/owned?ids=`; se ne mancano → 400), la specie nemica da `GET monsters:/internal/monsters` (cache in memoria). Offerta non `open` → 409. Simula, assegna XP (`POST monsters:/internal/collection/xp`), eventuale cattura (`POST monsters:/internal/collection`), eventuale `advance-stage`, poi registra in history (`POST history:/internal/history`). Salva il run.

`EncounterOffer`:
```json
{"encounter_id":"…","kind":"wild|guardian","stage":1,"status":"open|completed","difficulty":"easy|normal|hard|boss",
 "enemy":{"monster_id":5,"name":"…","element":"time","rarity":"common","image_url":"…","level":2,
          "stats":{"hp":40,"attack":38,"defense":30,"speed":44},"ability":{Ability}},
 "rewards":{"xp":20,"capture_chance":0.6,"advances_stage":false},"created_at":"…"}
```
`difficulty`: guardian → `boss`; wild livello `3s−2` → `easy`, `3s−1` → `normal`, `3s` → `hard`.

`EncounterResult`:
```json
{"run_id":"…","encounter_id":"…","kind":"wild","stage":1,"outcome":"victory|defeat","rounds":7,"seed":123,
 "team":[{"owned_id":"…","monster_id":1,"name":"…","element":"dream","image_url":"…","level":1,"max_hp":86,"stats":{…}}],
 "enemy":{"monster_id":5,"name":"…","element":"time","image_url":"…","level":2,"max_hp":90,"stats":{…}},
 "log":[Event],
 "rewards":{"xp":[{"owned_id":"…","xp":20,"old_level":1,"new_level":2}],"captured":OwnedMonster|null,
            "stage_advanced":false,"new_stage":1},
 "occurred_at":"…"}
```

## 8. history

Entità `HistoryRecord`: `record_id` UUID, `player_id`, `run_id` unique, `sequence` (per giocatore, da 1), `occurred_at`, `outcome`, `kind`, `stage`, `payload` (testo JSON = l'intero `EncounterResult`), `prev_hash`, `hash`.

Integrità (US-25): `hash = HMAC-SHA256(history.secret, prev_hash + "|" + sequence + "|" + payload)` in esadecimale; `prev_hash` del primo record = `"GENESIS"`. Config `history.secret=${HISTORY_SECRET:dev-history-secret}`.

| Metodo | Path | Auth | Risposta |
|---|---|---|---|
| GET | `/history?outcome=&page=0&size=20` | JWT | `{items:[HistorySummary], page, size, total, total_pages}` dal più recente |
| GET | `/history/{run_id}` | JWT | `HistoryDetail` = `HistorySummary` + `result` (l'`EncounterResult` completo) + `hash`, `prev_hash` (403/404) |
| GET | `/history/verify` | JWT | `{valid, records, first_invalid_sequence}` (ricalcola la catena del giocatore) |
| POST | `/internal/history` | interno | `EncounterResult` + `player_id` → **201** `{record_id, sequence, hash}` (stesso `run_id` → 409) |
| GET | `/internal/history/{player_id}/stats` | interno | `{encounters_total, encounters_won, encounters_lost}` |

`HistorySummary`: `{run_id, encounter_id, sequence, kind, stage, outcome, rounds, enemy:{monster_id,name,element,image_url,level}, team:[{monster_id,name,image_url,level}], captured:{monster_id,name,image_url}|null, occurred_at}`.

## 9. Infrastruttura

- `docker-compose.yml` alla radice: `init-secrets` (genera una volta chiavi JWT RSA 2048 e certificato TLS self-signed in volumi), `postgres` (16-alpine, `infra/postgres/init.sh` crea un DB e un utente per servizio), i 6 servizi, `gateway`.
- Volumi: `pgdata`, `secrets-jwt-private` (solo auth), `secrets-jwt-public` (tutti), `secrets-tls` (solo gateway).
- Gateway nginx: 80 → redirect 443; 443 TLS; header di sicurezza; rate limit su `/api/auth/`; porte host `8080` e `8443`.
- Segreti in `.env` (da `.env.example`): `POSTGRES_PASSWORD`, `<SVC>_DB_PASSWORD`, `INTERNAL_TOKEN`, `HISTORY_SECRET`.
