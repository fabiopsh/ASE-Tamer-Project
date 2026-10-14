# monster-gen

Pipeline per generare i 100 mostri del catalogo: dati, immagini pixel art 64×64, export verso `backend/monsters/seed/`.

## Setup

Ambiente Python 3.11 in `.venv/` (vedi comandi `uv` nella guida di setup). Modelli nella cache Hugging Face:
`stabilityai/stable-diffusion-xl-base-1.0`, `nerijs/pixel-art-xl`, `madebyollin/sdxl-vae-fp16-fix`, opzionale `latent-consistency/lcm-lora-sdxl`.

## Passi

| # | Script | Cosa fa |
|---|---|---|
| 1 | `validate.py` | Controlla `data/abilities.json` e `data/monsters.json` |
| 2 | `generate.py` | Genera 4 varianti 1024×1024 per mostro in `output/raw/` |
| 3 | `postprocess.py` | Sfondo trasparente, griglia 64×64, palette ridotta in `output/sprites/` |
| 4 | `contact_sheet.py` | Varianti affiancate per scegliere; scelte in `data/selections.json` |
| 5 | `export.py` | Sprite scelti + JSON finali in `backend/monsters/seed/` |

Parametri e percorsi condivisi in `config.py`, template dei prompt in `prompts/template.json`. La cartella `output/` non è versionata.

## Generazione immagini

```bash
.venv/Scripts/python generate.py --dry-run --ids 1-5   # mostra i prompt, senza GPU
.venv/Scripts/python generate.py --ids 1-5             # test su 5 mostri
.venv/Scripts/python generate.py                       # tutti: 400 immagini, ~85 min su RTX 4070 SUPER
.venv/Scripts/python generate.py --ids 29 --variants 8 # più varianti per un mostro difficile
```

- Modalità standard (30 passi, ~12 s/img) consigliata: segue meglio i prompt.
- `--lcm` (8 passi, ~8 s/img) è più veloce ma perde dettagli e sfondo bianco.
- Seed fisso = `monster_id * 1000 + variante`; le immagini già presenti vengono saltate (riprendere dopo un'interruzione = rilanciare). `--force` rigenera.
- Ogni immagine è registrata in `output/raw/manifest.jsonl` con prompt e parametri.

## Sprite e selezione

```bash
.venv/Scripts/python postprocess.py      # sfondo trasparente + sprite 64x64 in output/sprites/
.venv/Scripts/python contact_sheet.py    # fogli con tutte le varianti in output/sheets/
```

- `postprocess.py`: rembg rimuove lo sfondo, l'alpha diventa netto (pixel pieni o vuoti), si riempiono i buchi interni, si eliminano frammenti staccati, poi riduzione a 64×64 con palette di 24 colori. Il ritaglio a piena risoluzione resta in `output/cutout/`.
- `contact_sheet.py`: una riga per mostro, una colonna per variante (fino a 8 se ne sono state generate di più); la variante scelta è evidenziata. Per cambiarla si modifica `data/selections.json` (`monster_id` → numero di variante) e si rilancia lo script. `--clean` cancella i fogli precedenti.
- Mostri con varianti tutte deboli: si ritocca il loro `image_prompt` e si generano varianti extra, es. `generate.py --ids 12 --variants 8` (crea solo le varianti 4–7).
- I mostri dell'elemento Silence sono generati su sfondo nero (`prompts/template.json` → `background`), altrimenti rembg non li separa dal bianco.

## Export

```bash
python validate.py
.venv/Scripts/python export.py
```

Copia la variante scelta di ogni mostro in `backend/monsters/seed/images/monsters/NNN.png` e scrive in `backend/monsters/seed/` i JSON per il backend (`monsters.json` senza i campi della pipeline, `abilities.json`, `elements.json`) più `catalog_preview.png`. Quella cartella è versionata: è la fonte delle immagini per tutto il gruppo.
