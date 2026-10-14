# players

Profilo di gioco e progressi del giocatore (US-03, US-04 per nome/avatar, US-06 scelta degli starter, US-07).

## Responsabilità

- Crea il profilo quando `auth` registra un account (`POST /internal/players`), estraendo il **pool di 10 starter common** a caso dal catalogo di `monsters`.
- Restituisce il profilo con i progressi aggregati (collezione da `monsters`, encounter da `history`).
- Permette di cambiare nome visualizzato e avatar.
- Gestisce la scelta (una sola volta) dei 3 starter e lo `stage` di progressione (1..10, 11 = gioco completato).

## Entità

`Player` (tabella `players`): `player_id` (UUID, = `sub` del JWT), `display_name`, `avatar_monster_id` (nullable), `stage`, `starter_pool` (lista ordinata di 10 `monster_id`, tabella `player_starter_pool`), `starters_chosen`, `created_at`.

## Endpoint

| Metodo | Path | Note |
|---|---|---|
| GET | `/players/me` | profilo + `progress`; 404 se il profilo non esiste. Se `monsters` o `history` non rispondono (timeout 2 s connessione / 3 s lettura) i loro campi in `progress` sono `null` e la risposta resta 200 |
| PATCH | `/players/me` | `{display_name?, avatar_monster_id?}`; 400 con `details`; 503 se `monsters` non risponde mentre si verifica l'avatar |
| POST | `/players/me/starters` | `{monster_ids:[a,b,c]}` → 201 `[OwnedMonster]` (risposta di `monsters`); 400 con `details`, 409 se già scelti, 503 se `monsters` fallisce (`starters_chosen` resta `false`) |
| POST | `/internal/players` | idempotente, 201 `PlayerProfile`; 503 se `monsters` non risponde |
| GET | `/internal/players/{player_id}` | `PlayerProfile` (senza `progress`), 404 |
| POST | `/internal/players/{player_id}/advance-stage` | `{cleared_stage}` → `PlayerProfile`, 404 |

## Regole

- `display_name`: 3–20 caratteri `[A-Za-z0-9_ ]`, salvato senza spazi ai bordi. `avatar_monster_id` deve essere una specie posseduta.
- Starter: esattamente 3 id distinti, tutti nel pool del giocatore. Il controllo "già scelti" ha la precedenza (409). La riga del giocatore è bloccata (`PESSIMISTIC_WRITE`) durante la chiamata a `monsters`, così un doppio invio non assegna gli starter due volte.
- `advance-stage`: `stage + 1` solo se `cleared_stage == stage` e `stage <= 10`; altrimenti invariato (idempotente).

## Classi pure testate (`src/test`)

- `StarterPolicy`: estrazione del pool (10 distinti, deterministica con un `RandomGenerator` fissato) e validazione della scelta.
- `StageRules`: avanzamento idempotente, `MAX_STAGE = 10`, 11 = completato.
- `ProfileRules`: validazione di nome e avatar.
- `PlayerDtosTest`: forma JSON del contratto (snake_case, campi `progress` a `null`).

33 unit test JUnit 5, nessun `@QuarkusTest`. Build: `mvn -B -q package` (vedi `docs/architettura/backend-guide.md`).

## Configurazione

`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_PUBLIC_KEY`, `INTERNAL_TOKEN`, `MONSTERS_URL`, `HISTORY_URL` (default di sviluppo in `application.properties`).
