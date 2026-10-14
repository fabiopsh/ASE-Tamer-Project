# teams

Creazione, modifica e validazione dei team di mostri (US-14, US-15, US-16).

## Responsabilità

- Salva i team (`teams`, `team_members`) con l'ordine dei mostri = ordine di battaglia.
- Controlla le regole di composizione (`TeamRules`, logica pura).
- Recupera i dettagli dei mostri dal servizio `monsters` (`GET /internal/collection/{player_id}/owned?ids=`).
- Ricalcola `valid` ed `errors` ad ogni lettura: se un mostro non è più posseduto il team risulta non valido.

## Endpoint

| Metodo | Path | Note |
|---|---|---|
| GET | `/teams` | lista dei team del giocatore |
| POST | `/teams` | `{name, owned_ids}` → 201; non valido → 400 con `details` |
| GET | `/teams/{team_id}` | 403 se di un altro giocatore, 404 se inesistente |
| PUT | `/teams/{team_id}` | `{name, owned_ids}`, stesse regole della creazione |
| DELETE | `/teams/{team_id}` | 204 |
| POST | `/teams/validate` | `{name?, owned_ids}` → sempre 200 `{valid, errors}` |
| GET | `/internal/teams/{team_id}?player_id=` | interno; 404 se manca o è di un altro giocatore |

`TeamView`: `{team_id, name, owned_ids, members, valid, errors, created_at, updated_at}`.
Se `monsters` non risponde in lettura o scrittura → 503.

## Regole (`TeamRules`)

- Nome: 1–30 caratteri dopo `trim`, non vuoto.
- Da 1 a 3 mostri, id distinti.
- Tutti i mostri devono appartenere al giocatore.
- Massimo 5 team per giocatore (solo in creazione).

## Test

Test JUnit 5 puri su `TeamRules` (`TeamRulesTest`): team vuoto, troppi mostri, duplicati,
mostri non posseduti, nome non valido, team valido, limite di team.
Girano in `mvn package` dentro la build Docker.
