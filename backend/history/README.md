# history

Storico persistente degli encounter e catena di hash anti-manomissione.

- **User stories:** US-23, US-24, US-25
- **Database:** `history`, tabella `history_records`

## Responsabilità

- Riceve da `encounters` ogni `EncounterResult` (`POST /internal/history`) e lo salva così com'è.
- Assegna a ogni giocatore una sequenza (1, 2, 3…) e una catena di hash HMAC-SHA256.
- Espone lo storico del giocatore con riepiloghi estratti dal payload, il dettaglio di un run e la verifica della catena.
- Non chiama altri servizi.

## Endpoint

| Metodo | Path | Auth | Note |
|---|---|---|---|
| GET | `/history?outcome=victory\|defeat&page=0&size=20` | JWT | Dal più recente; `size` max 100; `outcome` invalido → 400 |
| GET | `/history/verify` | JWT | `{valid, records, first_invalid_sequence}` sulla catena del giocatore |
| GET | `/history/{run_id}` | JWT | Riepilogo + `result` (EncounterResult), `hash`, `prev_hash`; altrui → 403, inesistente → 404 |
| POST | `/internal/history` | `X-Internal-Token` | EncounterResult + `player_id` → 201 `{record_id, sequence, hash}`; `run_id` già presente → 409 |
| GET | `/internal/history/{player_id}/stats` | `X-Internal-Token` | `{encounters_total, encounters_won, encounters_lost}` |

## Integrità (US-25)

- `hash = HMAC-SHA256(history.secret, prev_hash + "|" + sequence + "|" + payload)` in esadecimale minuscolo.
- `prev_hash` del primo record del giocatore = `"GENESIS"`, poi l'hash del record precedente.
- Il payload è salvato una sola volta (JSON serializzato alla ricezione) e l'hash è calcolato su quella stringa; la verifica usa il testo salvato.
- Le sequenze sono serializzate per giocatore con un lock transazionale (`pg_advisory_xact_lock`) e protette dal vincolo unico `(player_id, sequence)`.
- `history.secret` si imposta con la variabile `HISTORY_SECRET` (default di sviluppo `dev-history-secret`).

## Classi principali

- `record/HashChain`: calcolo HMAC e verifica della catena (pura).
- `record/EncounterPayload`: validazione dell'intestazione in ingresso, riepilogo e parsing del payload (puro).
- `record/HistoryService`: append con lock, letture, verifica, statistiche.
- `record/HistoryResource` (`/history`), `record/HistoryInternalResource` (`/internal/history`).

## Test

Unit test JUnit 5 puri, eseguiti da `mvn package` nella build Docker:

- `HashChainTest`: vettore noto HMAC, formato dell'hash, catena valida, payload/hash/link manomessi, record cancellato, secret errato, catena vuota.
- `EncounterPayloadTest`: validazione dei campi, riepilogo con e senza cattura, serializzazione stabile.
- `HistoryJsonShapeTest`: forma JSON in snake_case di dettaglio e pagina.
