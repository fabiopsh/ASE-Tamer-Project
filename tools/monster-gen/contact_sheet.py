"""Build contact sheets showing all sprite variants side by side.

Each row is a monster, each column one variant (0, 1, 2, ...) on a checkerboard,
so transparency is visible. The current choice from data/selections.json is
highlighted. To change a choice, edit selections.json: monster_id -> variant.
Missing entries default to variant 0 and are written on the first run.

Output: output/sheets/sheet_01.png, sheet_02.png, ...

Examples:
    python contact_sheet.py                   # all monsters, 10 per sheet
    python contact_sheet.py --ids 1-16 --per-sheet 16
"""

import argparse
import json
from collections import defaultdict

from PIL import Image, ImageDraw, ImageFont

import config
from generate import parse_ids, seed_for

CELL = 192           # pixels per variant cell (sprite upscaled with nearest neighbour)
LABEL_WIDTH = 260
GAP = 8
SELECTED = (230, 80, 60)
RARITY_COLORS = {"common": (110, 110, 110), "rare": (40, 110, 200), "legendary": (200, 140, 0)}


def checkerboard(size: int, square: int = 12) -> Image.Image:
    board = Image.new("RGB", (size, size), (236, 236, 236))
    draw = ImageDraw.Draw(board)
    for y in range(0, size, square):
        for x in range(0, size, square):
            if (x // square + y // square) % 2:
                draw.rectangle([x, y, x + square - 1, y + square - 1], fill=(206, 206, 206))
    return board


def available_variants() -> dict[int, int]:
    """Number of variant columns needed per monster (highest variant found + 1)."""
    counts: dict[int, int] = {}
    for path in config.SPRITES_DIR.glob("*.png"):
        monster_id, seed = (int(x) for x in path.stem.split("_"))
        counts[monster_id] = max(counts.get(monster_id, 0), seed - monster_id * 1000 + 1)
    return counts


def load_selections(monster_ids: list[int]) -> dict[int, int]:
    selections = {}
    if config.SELECTIONS_FILE.exists():
        selections = {int(k): v for k, v in json.loads(config.SELECTIONS_FILE.read_text(encoding="utf-8")).items()}
    changed = False
    for monster_id in monster_ids:
        if monster_id not in selections:
            selections[monster_id] = 0
            changed = True
    if changed:
        ordered = {str(k): selections[k] for k in sorted(selections)}
        config.SELECTIONS_FILE.write_text(json.dumps(ordered, indent=2) + "\n", encoding="utf-8")
    return selections


def draw_sheet(rows: list[dict], variants: int, selections: dict[int, int], fonts) -> Image.Image:
    title_font, text_font = fonts
    width = LABEL_WIDTH + variants * (CELL + GAP)
    height = 40 + len(rows) * (CELL + GAP)
    sheet = Image.new("RGB", (width, height), "white")
    draw = ImageDraw.Draw(sheet)

    for v in range(variants):
        draw.text((LABEL_WIDTH + v * (CELL + GAP) + CELL // 2 - 30, 10), f"variant {v}", fill="black", font=text_font)

    for r, monster in enumerate(rows):
        y = 40 + r * (CELL + GAP)
        monster_id = monster["monster_id"]
        draw.text((10, y + 10), f"{monster_id:03d} {monster['name']}", fill="black", font=title_font)
        draw.text((10, y + 44), monster["element"], fill="black", font=text_font)
        draw.text((10, y + 68), monster["rarity"], fill=RARITY_COLORS[monster["rarity"]], font=text_font)

        for v in range(variants):
            x = LABEL_WIDTH + v * (CELL + GAP)
            path = config.SPRITES_DIR / f"{monster_id:03d}_{seed_for(monster_id, v)}.png"
            cell = checkerboard(CELL)
            if path.exists():
                sprite = Image.open(path).convert("RGBA").resize((CELL, CELL), Image.Resampling.NEAREST)
                cell.paste(sprite, (0, 0), sprite)
            else:
                ImageDraw.Draw(cell).text((CELL // 2 - 30, CELL // 2 - 8), "missing", fill="red", font=text_font)
            sheet.paste(cell, (x, y))
            if selections.get(monster_id) == v:
                draw.rectangle([x - 3, y - 3, x + CELL + 2, y + CELL + 2], outline=SELECTED, width=4)
    return sheet


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ids", help="monster ids to include, e.g. '1-10,15' (default: all)")
    parser.add_argument("--per-sheet", type=int, default=10, help="monsters per sheet")
    parser.add_argument("--variants", type=int,
                        help="variant columns (default: as many as the sprites found for the sheet)")
    parser.add_argument("--clean", action="store_true", help="delete existing sheets before drawing")
    args = parser.parse_args()

    monsters = {m["monster_id"]: m for m in json.loads(config.MONSTERS_FILE.read_text(encoding="utf-8"))}
    ids = parse_ids(args.ids, sorted(monsters))
    selections = load_selections(sorted(monsters))
    fonts = (ImageFont.load_default(size=26), ImageFont.load_default(size=20))

    config.SHEETS_DIR.mkdir(parents=True, exist_ok=True)
    if args.clean:
        for old in config.SHEETS_DIR.glob("sheet_*.png"):
            old.unlink()
    pages = defaultdict(list)
    for index, monster_id in enumerate(ids):
        pages[index // args.per_sheet].append(monsters[monster_id])

    available = available_variants()
    for page, rows in pages.items():
        path = config.SHEETS_DIR / f"sheet_{page + 1:02d}.png"
        variants = args.variants or max(
            [available.get(m["monster_id"], 0) for m in rows] + [config.VARIANTS_PER_MONSTER])
        draw_sheet(rows, variants, selections, fonts).save(path)
        print(f"{path.name}: {rows[0]['monster_id']:03d}-{rows[-1]['monster_id']:03d}")
    print(f"{len(pages)} sheets -> {config.SHEETS_DIR}")


if __name__ == "__main__":
    main()
