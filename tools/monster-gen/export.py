"""Export the final catalog for the monsters service.

For every monster, copies the sprite variant chosen in data/selections.json to
the path given by its image_url, then writes the seed data used by the backend.
Pipeline-only fields (archetype, image_prompt) are dropped.

Output: backend/monsters/seed/
    monsters.json              catalog (100 monsters)
    abilities.json             ability catalog
    elements.json              element cycle (who is strong against whom)
    images/monsters/NNN.png    64x64 transparent sprites
    catalog_preview.png        all sprites on one page, for a quick look

Run validate.py first; this script only checks that every chosen sprite exists.
"""

import json
import shutil

from PIL import Image

import config
from generate import seed_for

PIPELINE_ONLY_FIELDS = {"archetype", "image_prompt"}
PREVIEW_COLUMNS = 10
PREVIEW_SCALE = 2


def write_json(path, data) -> None:
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def build_preview(sprites: list[Image.Image]) -> Image.Image:
    cell = config.SPRITE_SIZE * PREVIEW_SCALE
    rows = (len(sprites) + PREVIEW_COLUMNS - 1) // PREVIEW_COLUMNS
    preview = Image.new("RGBA", (PREVIEW_COLUMNS * cell, rows * cell), (0, 0, 0, 0))
    for index, sprite in enumerate(sprites):
        row, col = divmod(index, PREVIEW_COLUMNS)
        big = sprite.resize((cell, cell), Image.Resampling.NEAREST)
        preview.paste(big, (col * cell, row * cell), big)
    return preview


def main() -> None:
    monsters = json.loads(config.MONSTERS_FILE.read_text(encoding="utf-8"))
    abilities = json.loads(config.ABILITIES_FILE.read_text(encoding="utf-8"))
    selections = {int(k): v for k, v in json.loads(config.SELECTIONS_FILE.read_text(encoding="utf-8")).items()}

    sources = {}
    missing = []
    for monster in monsters:
        monster_id = monster["monster_id"]
        variant = selections.get(monster_id)
        source = config.SPRITES_DIR / f"{monster_id:03d}_{seed_for(monster_id, variant or 0)}.png"
        if variant is None or not source.exists():
            missing.append(f"{monster_id:03d} (variant {variant})")
        sources[monster_id] = source
    if missing:
        raise SystemExit(f"missing selection or sprite for: {', '.join(missing)}")

    images_root = config.EXPORT_DIR / "images"
    if images_root.exists():
        shutil.rmtree(images_root)  # drop sprites of a previous export

    exported, sprites = [], []
    for monster in monsters:
        destination = config.EXPORT_DIR / monster["image_url"].lstrip("/")
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(sources[monster["monster_id"]], destination)
        sprites.append(Image.open(destination).convert("RGBA"))
        exported.append({k: v for k, v in monster.items() if k not in PIPELINE_ONLY_FIELDS})

    cycle = config.ELEMENT_CYCLE
    elements = {
        "cycle": cycle,
        "strong_against": {e: cycle[(i + 1) % len(cycle)] for i, e in enumerate(cycle)},
        "weak_against": {e: cycle[i - 1] for i, e in enumerate(cycle)},
    }

    write_json(config.EXPORT_DIR / "monsters.json", exported)
    write_json(config.EXPORT_DIR / "abilities.json", abilities)
    write_json(config.EXPORT_DIR / "elements.json", elements)
    build_preview(sprites).save(config.EXPORT_DIR / "catalog_preview.png")

    print(f"exported {len(exported)} monsters, {len(abilities)} abilities -> {config.EXPORT_DIR}")


if __name__ == "__main__":
    main()
