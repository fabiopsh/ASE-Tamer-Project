"""Build the OFL Amber pixel faces without changing the existing text advances.

Run from frontend/ with Python 3 and fonttools==4.68.0, brotli==1.2.1,
pillow==13.0.0 and freetype-py==2.5.1. Runtime uses only the generated WOFF2 files.
"""

from math import ceil
from pathlib import Path

from fontTools.pens.freetypePen import FreeTypePen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/assets/fonts"
WEIGHTS = (450, 550, 700, 800, 900)


def pixel_glyph(glyph, glyph_set, units, advance, resolution):
    """Rasterize outlines, then encode horizontal pixel runs as TrueType contours."""
    scale = resolution / units
    margin = 6
    baseline = resolution
    width = ceil(advance * scale) + margin * 2
    height = resolution * 3
    raster = FreeTypePen(glyph_set)
    glyph.draw(raster)
    pixels, size = raster.buffer(
        width=width, height=height,
        transform=(scale, 0, 0, scale, margin, baseline),
    )
    pen = TTGlyphPen(None)
    for row in range(size[1]):
        x = 0
        while x < size[0]:
            if pixels[row * size[0] + x] < 96:
                x += 1
                continue
            start = x
            while x < size[0] and pixels[row * size[0] + x] >= 96:
                x += 1
            left, right = round((start - margin) / scale), round((x - margin) / scale)
            top = round((size[1] - row - baseline) / scale)
            bottom = round((size[1] - row - 1 - baseline) / scale)
            pen.moveTo((left, bottom))
            pen.lineTo((left, top))
            pen.lineTo((right, top))
            pen.lineTo((right, bottom))
            pen.closePath()
    return pen.glyph()


def build(source, family, resolution):
    for weight in WEIGHTS:
        font = TTFont(source)
        font.recalcTimestamp = False
        # Retain HVAR, MVAR and GPOS variation data. Instancing rounds advances and
        # subtly changes line boxes; the original tables preserve browser geometry.
        glyph_set = font.getGlyphSet(location={"wght": weight})
        units = font["head"].unitsPerEm
        advances = {name: metric[0] for name, metric in font["hmtx"].metrics.items()}
        glyphs = {
            name: pixel_glyph(glyph_set[name], glyph_set, units, advances[name], resolution)
            for name in font.getGlyphOrder()
        }
        for name, glyph in glyphs.items():
            font["glyf"][name] = glyph
            glyph.recalcBounds(font["glyf"])
            font["hmtx"].metrics[name] = (advances[name], getattr(glyph, "xMin", 0))
        # New outlines have no source instructions. Preserve GPOS, cmap and all line metrics.
        for table in ("gvar", "fpgm", "prep", "cvt ", "LTSH", "VDMX", "hdmx", "DSIG"):
            if table in font:
                del font[table]
        style = {450: "Regular", 550: "Medium", 700: "Bold", 800: "Black", 900: "Heavy"}[weight]
        for name_id, value in {
            1: family, 2: style, 3: f"Amber-1.0-{family}-{weight}",
            4: f"{family} {style}", 6: f"{family.replace(' ', '')}-{style}",
            16: family, 17: style,
        }.items():
            for platform, encoding, language in ((3, 1, 0x409), (1, 0, 0)):
                font["name"].setName(value, name_id, platform, encoding, language)
        font["OS/2"].usWeightClass = weight
        font.flavor = "woff2"
        target = OUTPUT / f"{family.lower().replace(' ', '-')}-{weight}.woff2"
        font.save(target)
        assert advances == {n: m[0] for n, m in font["hmtx"].metrics.items()}
        assert all(ord(c) in font.getBestCmap() for c in "ÀÈÉÌÒÙàèéìòù")
        print(target.name, target.stat().st_size)


if __name__ == "__main__":
    OUTPUT.mkdir(parents=True, exist_ok=True)
    build(ROOT / "node_modules/@fontsource-variable/exo-2/files/exo-2-latin-wght-normal.woff2", "Amber UI", 22)
    build(ROOT / "node_modules/@fontsource-variable/cinzel/files/cinzel-latin-wght-normal.woff2", "Amber Display", 28)
