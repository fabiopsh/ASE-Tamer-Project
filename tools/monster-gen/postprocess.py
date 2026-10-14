"""Turn raw images into 64x64 pixel-art sprites with a transparent background.

Steps: background removal (rembg), hard alpha (no semi-transparent pixels),
filling of holes inside the monster, crop to the monster, downscale to SPRITE_SIZE, palette reduction, removal of
isolated stray pixels.

Input:  output/raw/<monster_id>_<seed>.png
Output: output/cutout/<monster_id>_<seed>.png   full size, transparent background
        output/sprites/<monster_id>_<seed>.png  SPRITE_SIZE x SPRITE_SIZE RGBA

Examples:
    python postprocess.py              # every raw image not processed yet
    python postprocess.py --ids 1-10   # only some monsters
    python postprocess.py --force      # redo everything
"""

import argparse
import os
import time

import numpy as np
from PIL import Image, ImageDraw

import config
from generate import parse_ids

os.environ.setdefault("HF_HUB_DISABLE_SYMLINKS_WARNING", "1")

KEEP_FRACTION = 0.10  # fragments smaller than 10% of the main body are removed
BACKGROUND_TOLERANCE = 60  # summed RGB distance under which a hole pixel counts as background
OPENING_MAX_ALPHA = 16     # rembg alpha under which a background-coloured hole is a real opening


def hard_alpha(alpha: Image.Image, threshold: int) -> Image.Image:
    return alpha.point(lambda a: 255 if a >= threshold else 0)


def background_color(raw: np.ndarray) -> np.ndarray:
    """Median colour of the image border, i.e. the generated background."""
    border = np.concatenate([raw[0], raw[-1], raw[:, 0], raw[:, -1]])
    return np.median(border, axis=0)


def fill_holes(alpha: Image.Image, soft_alpha: Image.Image, raw: Image.Image) -> Image.Image:
    """Make opaque the enclosed transparent pixels that are not background.

    rembg often gives light-coloured parts of the body a partial alpha, which
    the threshold turns into holes inside the monster. Real openings (rings,
    frames, loops) show the background colour and were fully transparent for
    rembg, so those stay transparent.
    """
    padded = Image.new("L", (alpha.width + 2, alpha.height + 2), 0)
    padded.paste(alpha, (1, 1))
    ImageDraw.floodfill(padded, (0, 0), 128)  # mark the outside background
    outside = np.asarray(padded)[1:-1, 1:-1] == 128

    opaque = np.asarray(alpha) > 0
    rgb = np.asarray(raw.convert("RGB")).astype(int)
    distance = np.abs(rgb - background_color(rgb)).sum(axis=2)
    holes = ~outside & ~opaque
    partly_kept = np.asarray(soft_alpha) > OPENING_MAX_ALPHA
    filled = opaque | (holes & (partly_kept | (distance > BACKGROUND_TOLERANCE)))
    return Image.fromarray(np.where(filled, 255, 0).astype(np.uint8))


def remove_stray_pixels(alpha: np.ndarray) -> np.ndarray:
    """Clear opaque pixels that have no opaque 4-neighbour."""
    opaque = alpha > 0
    padded = np.pad(opaque, 1)
    neighbours = padded[:-2, 1:-1].astype(int) + padded[2:, 1:-1] + padded[1:-1, :-2] + padded[1:-1, 2:]
    cleaned = alpha.copy()
    cleaned[opaque & (neighbours == 0)] = 0
    return cleaned


def reduce_palette(rgb: Image.Image, alpha: np.ndarray, colors: int) -> Image.Image:
    """Quantize using a palette built only from the opaque pixels."""
    pixels = np.asarray(rgb)[alpha > 0]
    if len(pixels) == 0:
        return rgb
    sample = Image.fromarray(pixels.reshape(1, -1, 3))
    palette = sample.quantize(colors=min(colors, len(pixels)), method=Image.Quantize.MEDIANCUT)
    return rgb.quantize(palette=palette, dither=Image.Dither.NONE).convert("RGB")


def keep_main_parts(alpha: Image.Image) -> Image.Image:
    """Drop opaque fragments much smaller than the main body (ground lines, specks).

    Parts at least KEEP_FRACTION of the largest one (crowns, floating rings) are kept.
    """
    labels = alpha.copy()
    sizes: dict[int, int] = {}
    label = 1
    while label < 255:
        remaining = np.argwhere(np.asarray(labels) == 255)
        if len(remaining) == 0:
            break
        y, x = remaining[0]
        ImageDraw.floodfill(labels, (int(x), int(y)), label)
        sizes[label] = int((np.asarray(labels) == label).sum())
        label += 1
    if not sizes:
        return alpha
    largest = max(sizes.values())
    keep = [lab for lab, n in sizes.items() if n >= KEEP_FRACTION * largest]
    return Image.fromarray(np.where(np.isin(np.asarray(labels), keep), 255, 0).astype(np.uint8))


def to_sprite(raw: Image.Image, cutout: Image.Image) -> Image.Image:
    size, margin = config.SPRITE_SIZE, config.SPRITE_MARGIN
    soft_alpha = cutout.getchannel("A")
    alpha = hard_alpha(soft_alpha, config.ALPHA_THRESHOLD)
    alpha = fill_holes(keep_main_parts(alpha), soft_alpha, raw)
    bbox = alpha.getbbox()
    if bbox is None:
        raise ValueError("background removal left an empty image")

    # Colours come from the raw image: rembg blacks out the pixels it made transparent,
    # and fill_holes may turn some of them opaque again.
    subject = raw.convert("RGBA")
    subject.putalpha(alpha)
    subject = subject.crop(bbox)

    inner = size - 2 * margin
    scale = inner / max(subject.size)
    new_size = (max(1, round(subject.width * scale)), max(1, round(subject.height * scale)))
    # Pillow resizes RGBA with premultiplied alpha, so transparent pixels do not bleed into the edges.
    small = subject.resize(new_size, Image.Resampling.BOX)

    small_alpha = np.asarray(hard_alpha(small.getchannel("A"), config.ALPHA_THRESHOLD))
    small_alpha = remove_stray_pixels(small_alpha)
    small_rgb = reduce_palette(small.convert("RGB"), small_alpha, config.PALETTE_COLORS)

    sprite = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    layer = small_rgb.convert("RGBA")
    layer.putalpha(Image.fromarray(small_alpha))
    offset = ((size - new_size[0]) // 2, (size - new_size[1]) // 2)
    sprite.paste(layer, offset, layer)
    return sprite


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ids", help="monster ids to process, e.g. '1-10,15' (default: all)")
    parser.add_argument("--force", action="store_true", help="reprocess images that already have a sprite")
    args = parser.parse_args()

    raws = sorted(config.RAW_DIR.glob("*.png"))
    available = sorted({int(p.stem.split("_")[0]) for p in raws})
    if not available:
        raise SystemExit(f"no raw images in {config.RAW_DIR}, run generate.py first")
    ids = set(parse_ids(args.ids, available))

    jobs = [p for p in raws if int(p.stem.split("_")[0]) in ids
            and (args.force or not (config.SPRITES_DIR / p.name).exists())]
    print(f"{len(jobs)} images to process")
    if not jobs:
        return

    from rembg import new_session, remove

    session = new_session(config.REMBG_MODEL)
    config.CUTOUT_DIR.mkdir(parents=True, exist_ok=True)
    config.SPRITES_DIR.mkdir(parents=True, exist_ok=True)

    start = time.perf_counter()
    failed = []
    for done, raw in enumerate(jobs, start=1):
        raw_image = Image.open(raw).convert("RGB")
        cutout = remove(raw_image, session=session).convert("RGBA")
        cutout.save(config.CUTOUT_DIR / raw.name)
        try:
            to_sprite(raw_image, cutout).save(config.SPRITES_DIR / raw.name)
            status = "ok"
        except ValueError as exc:
            failed.append(raw.name)
            status = f"FAILED: {exc}"
        print(f"[{done}/{len(jobs)}] {raw.name} {status}", flush=True)

    print(f"done in {time.perf_counter() - start:.0f}s -> {config.SPRITES_DIR}")
    if failed:
        print(f"{len(failed)} failed: {', '.join(failed)}")


if __name__ == "__main__":
    main()
