# monsters

Catalogo dei mostri, abilità, elementi e collezione personale per run (livelli e XP).

- **Entità:** `Monsters`, `Abilities`, `Elements`, `OwnedMonsters`
- **User stories:** US-06, US-09, US-10, US-11, US-12, US-13

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
