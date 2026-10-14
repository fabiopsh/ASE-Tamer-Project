# monsters

Catalogo dei mostri, abilità e collezione personale. Quarkus 3.27.6, Java 21, PostgreSQL (database `monsters`).

- **Entità:** `Monster`, `Ability`, `OwnedMonster`
- **User stories:** US-06, US-09, US-10, US-11, US-12, US-13

## Responsabilità

- Serve il catalogo (100 specie, 28 abilità, ciclo degli elementi) e gli sprite come file statici.
- Possiede la collezione dei giocatori: ogni mostro posseduto è un'istanza (`owned_id`) con livello e XP propri; i duplicati sono ammessi.
- Calcola le statistiche effettive e applica l'XP (livelli) per conto di `encounters`.
- All'avvio, se le tabelle sono vuote, carica `abilities.json` e `monsters.json` dal classpath (`SeedLoader`). `elements.json` è tenuto in memoria (`Elements`).
- Il catalogo è letto dal DB una sola volta (primo uso) e tenuto in memoria: non cambia a runtime.

## Endpoint

Pubblici (JWT, ruolo `player`; il `player_id` è sempre il `sub` del token):

| Metodo | Path | Note |
|---|---|---|
| GET | `/monsters?element=&rarity=&ids=1,2&q=&page=0&size=20` | catalogo paginato, ordinato per `monster_id` |
| GET | `/monsters/{monster_id}` | `MonsterDetail`; 404 se inesistente |
| GET | `/monsters/abilities` | tutte le abilità |
| GET | `/monsters/elements` | contenuto di `elements.json` |
| GET | `/collection?element=&rarity=&sort=level` | collezione del giocatore (`sort`: `level` default, `recent`, `species`) |
| GET | `/collection/{owned_id}` | `OwnedMonsterDetail`; di un altro giocatore 403, inesistente 404 |
| GET | `/images/monsters/NNN.png` | **pubblico, senza JWT** (file statici dal classpath `META-INF/resources`) |

Interni (header `X-Internal-Token`, mai esposti dal gateway):

| Metodo | Path | Note |
|---|---|---|
| GET | `/internal/monsters` | i 100 `MonsterDetail` |
| GET | `/internal/collection/{player_id}` | `[OwnedMonster]` |
| GET | `/internal/collection/{player_id}/owned?ids=<uuid>,<uuid>` | solo quelli del giocatore, nell'ordine richiesto |
| GET | `/internal/collection/{player_id}/stats` | `{collection_size, species_owned}` |
| POST | `/internal/collection` | `{player_id, monster_id, source, encounter_id?}` -> 201 |
| POST | `/internal/collection/batch` | `{player_id, monster_ids, source}` -> 201, transazione unica |
| POST | `/internal/collection/xp` | `{player_id, awards:[{owned_id, xp}]}` -> `[{owned_id, old_level, new_level, xp, xp_to_next}]` |

**Non esiste alcun endpoint pubblico che aggiunga mostri (US-12)**: l'unico punto di inserimento è `/internal/collection*`, chiamato da `players` (starter) ed `encounters` (cattura).

## Regole

- Statistica effettiva: `round(base + growth * (level - 1))` (arrotondamento half-up, calcolato in decimale).
- `xp_to_next(level) = 25 * level`; `MAX_LEVEL = 50`. Al raggiungimento della soglia `xp -= soglia`, `level += 1`, anche più livelli in una volta. A livello 50 l'XP resta 0 e `xp_to_next` è mostrato come 0.
- `/internal/collection/xp` è tutto-o-niente (una sola transazione, righe bloccate con `PESSIMISTIC_WRITE`): se un `owned_id` non esiste o non appartiene al giocatore -> 400 e nessuna modifica. Premi con lo stesso `owned_id` ripetuto sono applicati in sequenza.
- Filtri: `element` e `rarity` sono validati contro gli enum (valore sconosciuto -> 400, il confronto ignora maiuscole/minuscole); `ids` è una lista di interi, id non numerici -> 400 (max 200); `q` cerca nel nome, case-insensitive.
- Paginazione: `page >= 0`, `size >= 1`; `size > 100` viene ridotto a 100; valori non numerici -> 400. Una pagina oltre la fine restituisce `items: []` con totali corretti.
- Id malformati (UUID o intero) nei path -> 400 `validation_error`.
- `source` è `starter` o `encounter`; `monster_id` sconosciuto -> 400. Il batch valida tutti gli id prima di scrivere.
- Gli sprite sono pubblici: le richieste senza header `Authorization` non passano da alcuna policy. (Un header `Authorization` non valido su un'immagine produce comunque 401: il browser non lo invia per i tag `<img>`.)

## Struttura del codice

```
catalog/     Monster, Ability (entità), Catalog (cache), Elements, SeedData/SeedLoader,
             Growth, Paging, Filters, MonsterFilter (logica pura), MonstersResource
collection/  OwnedMonster (entità), CollectionService, CollectionSort/CollectionRules (logica pura),
             CollectionResource (pubblico), InternalResource (interno)
common/      ApiException, ErrorMappers, InternalTokenFilter (come in auth)
```

`pom.xml` copia `seed/*.json` in `seed/` e `seed/images/**` in `META-INF/resources/images` (esclude `catalog_preview.png`). Il `Dockerfile` fa `COPY seed seed` prima di `mvn package`.

## Test

Unit test JUnit 5 puri (nessun `@QuarkusTest`, nessun DB), eseguiti da `mvn package`:

- `GrowthTest`: statistiche effettive, soglie XP, level-up multipli, tetto a livello 50.
- `PagingTest`: parsing, clamp della dimensione, calcolo di pagine e slice.
- `FiltersTest`: validazione di `element`/`rarity`/`ids`/UUID, `MonsterFilter`.
- `SeedDataTest`: legge i veri `monsters.json`, `abilities.json`, `elements.json` dal classpath: 100 mostri, 28 abilità, ogni `ability_id` esiste, 60/30/10 rarità, ogni sprite presente, ciclo degli elementi.
- `CollectionRulesTest`: ordinamenti della collezione, `owned` nell'ordine richiesto, specie distinte.
- `ErrorMappersTest`: nomi dei campi nei dettagli di validazione.

```bash
cd backend/monsters && MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/src" -v ase-m2:/root/.m2 -w /src maven:3.9-eclipse-temurin-21 mvn -B -q package
```

## Dati di seed (`seed/`)

Generati da `tools/monster-gen/export.py`, da non modificare a mano (si rigenerano dalla pipeline).

| File | Contenuto |
|---|---|
| `monsters.json` | 100 mostri: elemento, rarità, statistiche base e crescita, `ability_id`, descrizione, `image_url` |
| `abilities.json` | 28 abilità: `scope`, `element`, `effect_type`, `effect_value`, `trigger` |
| `elements.json` | Ciclo degli elementi: `strong_against` / `weak_against` per elemento |
| `images/monsters/NNN.png` | Sprite 64×64 con sfondo trasparente; il percorso coincide con `image_url` (`/images/monsters/NNN.png`) |
| `catalog_preview.png` | Tutti i mostri in un'unica immagine, per consultazione |

Gli sprite vanno mostrati ingranditi senza sfocatura (CSS `image-rendering: pixelated`).
