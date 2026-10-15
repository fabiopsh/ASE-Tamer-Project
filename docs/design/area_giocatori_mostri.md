# Monster Tamers — Area Giocatori e Mostri

Proposta per la consegna opzionale dell'architettura (20/10).

> **Aggiornamento 15/10:** alcune decisioni sono cambiate con l'introduzione delle run roguelike (stage, starter per run, livello dei mostri ottenuti, `run_id` in `OwnedMonsters`). Vedi [architettura_backend.md, sezione 8](../architettura/architettura_backend.md#8-cosa-cambia-nelle-decisioni-precedenti).

Copre:

- dati del giocatore (account, credenziali, progressi);
- catalogo dei mostri e collezione personale;
- caratteristiche dei mostri e collezione iniziale;
- strutture dati di giocatori e mostri;
- acceptance criteria delle 9 user stories rosse/gialle dell'area.

User stories coperte: US-01, US-02, US-03, US-05 🟡, US-06, US-09, US-10, US-11, US-12.

---

## 1. Decisioni

| # | Tema | Decisione | Motivazione |
|---|---|---|---|
| D1 | Account e profilo | **Separati**: credenziali in `Accounts` (componente Auth), dati di gioco in `Players` | Taglio naturale tra servizi in un'architettura distribuita; isola i dati sensibili (US-05) |
| D2 | Credenziali | **Username + password** | È il minimo richiesto; nessuna US chiede l'email |
| D3 | Progressi | **Derivati** da collezione e storico degli encounter, più un campo `stage` di progressione | Evita dati duplicati da tenere allineati |
| D4 | Caratteristiche dei mostri | **Ricche**: elemento, rarità, 4 statistiche, abilità speciale, crescita di livello | Danno varietà agli encounter |
| D5 | Scala delle statistiche | **Interi 1–100** | Comodi per i calcoli; il frontend può mostrarli come stelle |
| D6 | Duplicati | **Ammessi**: ogni mostro posseduto è un'istanza con un proprio `owned_id` | Catturare due volte la stessa specie è naturale; livello e XP sono per istanza |
| D7 | Collezione iniziale | Il giocatore **sceglie 3 starter distinti** da un pool di **10 mostri common** estratto a caso per il suo account, una sola volta | Personalizza l'inizio del gioco (copre anche US-07, verde) |
| D8 | Immagini | **File statici** serviti dal backend; nel DB solo `image_url` | Evita BLOB nel database |
| D9 | Catalogo | **100 mostri**: dati e descrizioni generati; immagini pixel art da un modello locale tramite pipeline | Catalogo ampio senza lavoro manuale per ogni mostro |
| D10 | Formato AC | **Lista puntata**, criteri brevi e testabili | Entrano in 1–2 slide |
| D11 | Accesso al catalogo | **Solo utenti autenticati** | Regola uniforme: tutte le API di gioco richiedono il token |
| D12 | Abilità | **Catalogo condiviso** di ~20–30 abilità riusate da più mostri | Effetti da implementare e bilanciare restano pochi |
| D13 | Livelli | **XP + tasso di crescita per specie** | Mostri della stessa specie crescono in modo coerente; specie diverse crescono in modo diverso |

---

## 2. Entità

> Se `Accounts`, `Players`, `Monsters` e `OwnedMonsters` finiscono in servizi diversi, i riferimenti tra loro sono **logici** (si salva l'id), non chiavi esterne vere tra database.

### `Accounts` — componente Auth

| Campo | Tipo | Note |
|---|---|---|
| `player_id` | UUID, PK | Stesso id usato in `Players` |
| `username` | string, unique | 3–20 caratteri |
| `password_hash` | string | Hash con salt (bcrypt/argon2), mai la password in chiaro |
| `created_at` | timestamp | |
| `last_login` | timestamp | |

### `Players` — profilo di gioco

| Campo | Tipo | Note |
|---|---|---|
| `player_id` | UUID, PK | |
| `display_name` | string | Nome mostrato in gioco |
| `stage` | int | Progressione nel gioco; significato da concordare con chi definisce gli encounter |
| `starter_pool` | lista di `monster_id` | 10 common estratti a caso alla creazione dell'account; fissati, così ricaricare non cambia l'offerta |
| `starters_chosen` | bool | `true` dopo la scelta degli starter, che non si può ripetere |
| `created_at` | timestamp | |

### `Monsters` — catalogo (100 righe di seed)

| Campo | Tipo | Note |
|---|---|---|
| `monster_id` | int, PK | |
| `name` | string, unique | |
| `description` | string | |
| `element` | enum | `dream` / `echo` / `time` / `gravity` / `silence` / `memory` / `chaos` (vedi sezione 5) |
| `rarity` | enum | `common` / `rare` / `legendary` |
| `base_hp`, `base_attack`, `base_defense`, `base_speed` | int 1–100 | Statistiche al livello 1 |
| `growth_hp`, `growth_attack`, `growth_defense`, `growth_speed` | number | Aumento della statistica per ogni livello |
| `ability_id` | ref → `Abilities` | |
| `image_url` | string | Percorso del file pixel art |

### `Abilities` — catalogo abilità (~20–30 righe)

| Campo | Tipo | Note |
|---|---|---|
| `ability_id` | int, PK | |
| `name` | string | |
| `description` | string | Testo leggibile per il giocatore |
| `effect_type` | enum | Es. `damage_boost`, `dodge`, `damage_reduction`, `heal` |
| `effect_value` | number | Intensità dell'effetto (es. 25 = 25%) |
| `trigger` | enum | Quando si attiva, es. `always`, `first_turn`, `hp_above_50` |

L'effetto è descritto da tipo + valore + condizione: la logica che lo applica sta **solo nel backend**.

### `OwnedMonsters` — collezione personale (istanze)

| Campo | Tipo | Note |
|---|---|---|
| `owned_id` | UUID, PK | Identifica la singola istanza; i team referenziano questo id |
| `player_id` | ref → `Players` | |
| `monster_id` | ref → `Monsters` | |
| `level` | int | Parte da 1 |
| `xp` | int | |
| `source` | enum | `starter` / `encounter` |
| `encounter_id` | ref, nullable | Encounter da cui proviene il mostro (entità dell'area encounter) |
| `obtained_at` | timestamp | |

### Statistiche effettive

Calcolate dal backend, mai dal frontend:

```
statistica_effettiva = base + growth × (level − 1)
```

---

## 3. Acceptance criteria

### Account e progressi

**US-01 — Creare un account** 🔴
*Dipendenze: nessuna*
- La registrazione con username e password crea l'account e restituisce il `player_id`.
- Se lo username esiste già, viene restituito un errore e nessun account viene creato.
- Una password con meno di 8 caratteri viene rifiutata con un errore di validazione.
- Dopo la registrazione il giocatore deve scegliere gli starter (→ US-06).

**US-02 — Login** 🔴
*Dipendenze: US-01*
- Con credenziali corrette viene restituito un token di sessione.
- Con credenziali errate viene restituito un errore generico, senza indicare se è sbagliato lo username o la password.
- Le API protette chiamate senza un token valido vengono rifiutate.

**US-03 — Ritrovare i progressi salvati** 🔴
*Dipendenze: US-01, US-02*
- Dopo logout e nuovo login, collezione, livelli, storico e `stage` sono invariati.
- I dati sopravvivono al riavvio del servizio (persistenza su database).
- L'endpoint del profilo restituisce lo stato corrente del giocatore.

**US-05 — Proteggere i dati dell'account** 🟡
*Dipendenze: US-01, US-02*
- Le password sono salvate solo come hash con salt, mai in chiaro.
- Un giocatore non può leggere né modificare i dati di un altro giocatore (risposta 403).
- Il token scade dopo un tempo prefissato; un token scaduto viene rifiutato.
- Gli input sono validati lato server (es. contro injection).
- La comunicazione avviene su HTTPS.

### Collezione di mostri

**US-06 — Ricevere una collezione iniziale** 🔴
*Dipendenze: US-01, US-10*
- Alla creazione dell'account viene estratto a caso un pool di 10 mostri common, diverso per ogni giocatore e salvato nel suo profilo.
- Il giocatore vede il proprio pool; ricaricare la pagina o rifare il login non lo cambia.
- Ne sceglie esattamente 3, distinti tra loro.
- I 3 mostri vengono aggiunti alla collezione a livello 1, con `source = starter`.
- La scelta si può fare una sola volta (`starters_chosen`).
- Una selezione non valida (mostro fuori dal pool, numero diverso da 3, duplicati) viene rifiutata con un errore.
- Prima di aver scelto gli starter il giocatore non può creare team.

**US-09 — Vedere la collezione personale** 🔴
*Dipendenze: US-02, US-06*
- Il giocatore autenticato vede solo i propri mostri.
- Per ogni mostro vede immagine, livello, XP, statistiche effettive e abilità.
- Più copie della stessa specie compaiono come istanze separate.

**US-10 — Sfogliare il catalogo** 🔴
*Dipendenze: nessuna*
- Il catalogo elenca tutti i 100 mostri con nome, immagine, elemento e rarità.
- La lista è paginata.
- La lista si può filtrare per elemento e per rarità.
- Il catalogo è accessibile solo a utenti autenticati.

**US-11 — Vedere i dettagli di un mostro** 🔴
*Dipendenze: US-10*
- Il dettaglio di una specie mostra statistiche base, crescita e abilità con la sua descrizione.
- Il dettaglio di un'istanza posseduta mostra in più livello, statistiche effettive, provenienza e data di ottenimento.
- Un id inesistente restituisce 404.

**US-12 — Ottenere un mostro guadagnato** 🔴
*Dipendenze: US-19 (risoluzione dell'encounter), US-09*
- Il mostro viene aggiunto in automatico **solo dal backend**, dopo un encounter il cui esito prevede una ricompensa.
- Il client non ha un endpoint per aggiungere mostri alla collezione.
- Il nuovo mostro è un'istanza a livello 1, collegata all'encounter tramite `encounter_id`.
- Il mostro è subito visibile nella collezione.

---

## 4. Da concordare con il gruppo

| Con chi | Punto |
|---|---|
| Area team | I team referenziano `owned_id` (istanze, D6). Dimensione minima del team ≤ 3, perché il giocatore parte con 3 starter. |
| Area encounter | Significato di `stage`; XP data da un encounter, livello massimo e curva di crescita; regole di ricompensa (quale mostro, con quale probabilità, a quale livello). |
| Area encounter | Come gli encounter usano `element`, statistiche e abilità (ciclo di debolezze tra elementi, effetti delle abilità). |
| Architettura | Auth come componente separato con il proprio database (`Accounts`). |
| Tutti | D7 copre anche **US-07** (verde) e D13 tocca **US-13** (verde): entrano già nel nostro scope. |

## 5. Catalogo dei mostri

### Decisioni sui dati

| # | Tema | Decisione |
|---|---|---|
| G1 | Elementi | 7 elementi **concettuali**: Dream, Echo, Time, Gravity, Silence, Memory, Chaos |
| G2 | Interazioni | **Ciclo semplice**: ogni elemento è forte contro il successivo, debole contro il precedente, neutro con gli altri 4 |
| G3 | Rarità | **60 common / 30 rare / 10 legendary** |
| G4 | Distribuzione | **Chaos speciale**: 8 mostri quasi tutti rare/legendary; gli altri 6 elementi si dividono i restanti 92 (15–16 ciascuno) |
| G5 | Abilità | ~25: ~10 **generiche** + 2–3 **per elemento**; i legendary hanno abilità **esclusive** più forti |
| G6 | Starter | Pool di **10 common estratti a caso per ogni nuovo account** (salvato in `Players.starter_pool`); il giocatore ne sceglie 3 distinti. Nessun mostro è marcato come starter nel catalogo |
| G7 | Lingua | Nomi e descrizioni in **inglese** |
| G8 | Stile visivo | **Stile Pokémon, approccio ibrido**: circa metà dei mostri sono oggetti legati all'elemento con tratti da mostro (occhi, bocca, arti, espressione), l'altra metà creature fuse con oggetti dell'elemento. Common carini, rare "cool", legendary maestosi; niente horror né grottesco |
| G9 | Immagini | SDXL + LoRA Pixel Art XL in locale, sprite **64×64** trasparenti (pipeline in `tools/monster-gen/`) |

### Ciclo degli elementi

```
Silence → Echo → Memory → Time → Chaos → Gravity → Dream → Silence
```

| Forte | contro | Perché |
|---|---|---|
| Silence | Echo | Il silenzio spegne l'eco |
| Echo | Memory | L'eco distorce i ricordi |
| Memory | Time | La memoria sopravvive al tempo |
| Time | Chaos | Il tempo ricompone il caos |
| Chaos | Gravity | Il caos rompe le leggi fisiche |
| Gravity | Dream | La gravità riporta a terra chi sogna |
| Dream | Silence | Il sogno riempie il silenzio |

### Distribuzione proposta

| Elemento | Common | Rare | Legendary | Totale |
|---|---|---|---|---|
| Chaos | 1 | 4 | 3 | 8 |
| Altri 6 (ciascuno) | 9–10 | 4–5 | 1–2 | 15–16 |
| **Totale** | **60** | **30** | **10** | **100** |

### Motivi visivi per elemento (proposta per i prompt)

Gli elementi concettuali vanno resi con oggetti concreti, altrimenti a 64×64 non si riconoscono:

| Elemento | Motivi | Palette |
|---|---|---|
| Dream | nuvole, stelle, lune, occhi socchiusi, cuscini | lilla, rosa pastello |
| Echo | onde sonore, conchiglie, campane, bocche aperte, cristalli che vibrano | turchese, argento |
| Time | clessidre, ingranaggi, quadranti, sabbia | ottone, ocra |
| Gravity | pianeti in orbita, rocce sospese, anelli, buchi neri | blu notte, viola |
| Silence | maschere, neve, piume, bende sulla bocca | bianco, grigio freddo |
| Memory | fotografie, libri, nastri, fili annodati, vetro incrinato | seppia, ambra |
| Chaos | frammenti, glitch, colori incoerenti, forme impossibili | colori saturi in contrasto |

## 6. Prossimo passo

Generare `abilities.json` e `monsters.json` in `tools/monster-gen/data/`, validarli, poi avviare la pipeline delle immagini.
