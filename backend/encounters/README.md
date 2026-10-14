# encounters

Cuore della logica di gioco: bacheca degli encounter, simulazione delle battaglie e ricompense.

- **User stories:** US-17, US-18, US-19, US-20
- **Package:** `it.unipi.ase.tamers.encounters` (`engine` = logica pura senza CDI/DB, `client` = REST client verso gli altri servizi, `encounter` = entità, servizio e risorsa REST, `common` = errori)
- **Dipendenze a valle:** `monsters` (catalogo, collezione, XP, catture), `teams`, `players` (stage), `history` (storico)

## Regole di gioco

### Stage e bacheca
- Ogni giocatore ha uno `stage` (1..10; 11 = gioco completato). Per ogni stage la bacheca contiene **3 encounter `wild` aperti + 1 `guardian`**. Con `stage == 11` solo 3 wild di livello stage 10.
- La bacheca viene generata/completata a ogni `GET /encounters`. Le offerte rimaste aperte di uno stage passato vengono chiuse (`completed`).
- Wild vinto: l'offerta diventa `completed` e ne viene generata una nuova; wild perso: resta aperta.
- Livelli: wild `[3s-2, 3s]`, guardian `3s+2`. Difficoltà: wild `3s-2` easy, `3s-1` normal, `3s` hard; guardian `boss`.
- Specie wild: stage 1-3 solo common; 4-6 common 70% / rare 30%; 7-10 common 30% / rare 70%; mai legendary. Sulla stessa bacheca le specie sono distinte (e comunque diverse dal guardian); si evita di ripetere le ultime 6 specie uscite nello stesso stage.
- Guardian (uguale per tutti): tabella fissa, stage 1-5 rare, 6-10 legendary, elementi a rotazione `silence, echo, memory, time, chaos, gravity, dream, silence, echo, memory`. Tra le specie del catalogo con quella rarità/elemento si sceglie quella con **attacco base più basso** (a parità, `monster_id` minore).

### Battaglia (`BattleEngine`, deterministico dato il `seed`)
- Il team combatte in ordine contro il nemico; quando un membro va KO entra il successivo (agisce dal round dopo). Vittoria se il nemico va KO, sconfitta se cade tutto il team o dopo **100 round**.
- HP di battaglia = `2 × hp + 10`, poi moltiplicato per `WILD_HP_MULTIPLIER` (1.5, wild) o `GUARDIAN_HP_MULTIPLIER` (1.4, guardian). Vedi "Scostamenti dal contratto".
- In ogni round agisce prima chi ha più `speed` (con `speed_boost`); a parità il giocatore.
- Danno = `max(1, round(atk × atk / (atk + def) × varianza[0.85, 1.0] × elemento × modificatori))`, con `atk` moltiplicato per l'eventuale `weaken`. Critico 6.25% (× 1.5).
- Elemento (`elements.json`, ciclo `silence → echo → memory → time → chaos → gravity → dream → silence`): forte 1.5, debole 0.67, altrimenti 1. La tabella è ricostruita da `strong_against`/`weak_against` del catalogo.
- Eventi del log: `enter` (anche per il nemico all'inizio), `attack`, `dodge` (actor = chi attacca, target = chi schiva), `ability` (reflect, copy, weaken), `stun`, `skip`, `heal`, `revive`, `faint`, `victory`/`defeat`. I campi nulli sono omessi.

### Abilità

| effect_type | semantica implementata |
|---|---|
| `damage_boost` | danno inflitto × (1 + v%) quando il trigger è attivo |
| `damage_reduction` | danno subito × (1 − v%) |
| `dodge` | v% di schivare l'attacco (evento `dodge`, nessun danno; `first_turn` = solo il primo attacco ricevuto) |
| `heal` | `hp_below_50`: una volta sola, appena gli HP scendono sotto il 50%, cura v% degli HP massimi; altri trigger (`always`): cura v% a fine di ogni propria azione |
| `speed_boost` | speed × (1 + v%) per l'ordine di azione (`first_turn` = solo nel primo round del portatore) |
| `reflect` | `on_hit`: rimanda v% del danno subito all'attaccante (solo se il portatore sopravvive) |
| `stun` | `on_attack`: v% di far saltare la prossima azione all'avversario (`skip`) |
| `weaken` | all'ingresso dei combattenti l'attacco avversario × (1 − v%); dura finché l'avversario resta in campo |
| `ignore_disadvantage` | quando il portatore attacca, il moltiplicatore 0.67 diventa 1 |
| `revive` | una volta per battaglia, al KO torna con v% degli HP massimi |
| `random_boost` | danno inflitto × casuale in `[1 − v%, 1 + 2v%]` |
| `invert_advantage` | forte/debole scambiati negli scontri del portatore (sia in attacco sia in difesa; se lo hanno entrambi si annulla) |
| `copy_ability` | all'ingresso copia l'abilità dell'avversario (se non è a sua volta `copy_ability`); le abilità copiate partono con i flag "una volta" azzerati |

Trigger: `always`; `first_turn` (prima azione del portatore; per `dodge` primo attacco subito; per `weaken`/`copy_ability` all'ingresso); `hp_above_50` / `hp_below_50`; `on_hit` (portatore colpito); `on_attack` (portatore attacca); `once`; `vs_faster` (avversario con speed effettiva maggiore).

### Ricompense
- Vittoria: XP a ogni membro del team = `10 × livello nemico` (guardian × 2). Sconfitta: 30% (arrotondato, min 1).
- Cattura solo in vittoria: common 60%, rare 35%, guardian garantita. Il mostro catturato entra a livello 1 con `source=encounter` e `encounter_id = run_id`.
- Vittoria sul guardian: `advance-stage` (`cleared_stage` = stage dell'offerta).
- Ordine in `start`: carica dati → simula → XP → cattura → advance-stage → salva run/offerta → history. Un errore di un servizio a valle restituisce 503 (nessuna saga: se la history fallisce, run e ricompense restano comunque salvati).

## Endpoint

| Metodo | Path | Note |
|---|---|---|
| GET | `/encounters` | `{stage, game_completed, encounters:[EncounterOffer]}`, genera le offerte mancanti |
| GET | `/encounters/{encounter_id}` | `EncounterOffer`; altrui 403, inesistente 404 |
| POST | `/encounters/{encounter_id}/start` | `{team_id}` → 201 `EncounterResult`; offerta non `open` o di uno stage passato 409; team inesistente/non tuo o membri mancanti 400; servizi a valle 503 |
| GET | `/encounters/runs/{run_id}` | `EncounterResult` salvato; altrui 403, inesistente 404 |

Entità: `EncounterOffer` (`encounter_offers`: id, player_id, kind, stage, status, monster_id, level, created_at) ed `EncounterRun` (`encounter_runs`: run_id, player_id, encounter_id, outcome, seed, result JSON, occurred_at). Il corpo inviato a `POST history:/internal/history` è lo stesso JSON dell'`EncounterResult` più `player_id`. Il catalogo dei 100 mostri è letto una volta da `monsters` e tenuto in memoria. Le richieste dello stesso giocatore sono serializzate (lock in memoria per giocatore) per evitare doppie ricompense e bacheche duplicate.

## Scostamenti dal contratto

Con la formula del danno del contratto (`atk² / (atk + def)`, superlineare) un guardian rare di livello 5 con HP × 2.5 è imbattibile per un team di tre common di livello 1 (< 1% di vittorie, anche scegliendo la rare più debole), mentre i wild common di livello 1-3 vengono battuti nel 98-100% dei casi. Per rispettare i target di bilanciamento sono stati cambiati due **moltiplicatori HP** (costanti in `BoardGenerator`) e la regola di scelta del guardian:

| Costante | Contratto | Implementato |
|---|---|---|
| HP wild | × 1 | × 1.5 (`WILD_HP_MULTIPLIER`) |
| HP guardian | × 2.5 | × 1.4 (`GUARDIAN_HP_MULTIPLIER`) |
| Specie guardian | "dal catalogo" | rarity/elemento fissi, attacco base più basso |

Le formule (HP `2×hp+10`, danno, varianza, critico, ordine) sono invariate. Se si preferisce tornare a × 2.5 bisogna rilassare il test di bilanciamento (o ridurre il livello del guardian).

## Test

```bash
# solo unit test (BalanceTest viene saltato: il seed non è visibile nel container)
cd backend/encounters && MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/src" -v ase-m2:/root/.m2 -w /src maven:3.9-eclipse-temurin-21 mvn -B -q package

# con il test di bilanciamento sui dati reali (monta tutta la cartella backend)
cd backend && MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/work" -v ase-m2:/root/.m2 -w /work/encounters maven:3.9-eclipse-temurin-21 mvn -B package
```

47 test JUnit puri (nessun `@QuarkusTest`):
- `BattleEngineTest` (33): determinismo, input non modificati, HP e moltiplicatore guardian, ordine/parità di speed, formula del danno e varianza, frequenza dei critici, moltiplicatori elementali, un test per ognuno dei 13 effetti, trigger `first_turn`, `hp_below_50`, `once`, `vs_faster`, dodge `first_turn`/`hp_above_50`, salto del turno per stun, revive, cambio di membro, sconfitta del team, pareggio dopo 100 round, forma JSON degli eventi.
- `BoardGeneratorTest` (8): livelli/difficoltà, bacheca stage 1 (solo common, specie distinte), stage 5 (mix 70/30), stage 10 e 11, esclusioni, tabella del guardian.
- `RewardsTest` (3): XP, probabilità di cattura.
- `BalanceTest` (3): simulazione di 500 battaglie con i mostri reali (`../monsters/seed`, oppure la cartella in `ENCOUNTERS_SEED_DIR`; saltato se non trovata).

### Numeri di bilanciamento (500 battaglie, team di 3 common di livello 1 casuali e distinti)

| Avversario | Vittorie | Intervallo richiesto |
|---|---|---|
| Wild stage 1 (common, livello 1-3) | 95.8% | 75% - 98% |
| Guardian stage 1 (Frostveil, rare, livello 5) | 37.0% | 25% - 80% |
