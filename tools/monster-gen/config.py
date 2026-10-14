"""Shared paths and parameters for the monster generation pipeline."""

from pathlib import Path

ROOT = Path(__file__).resolve().parent

DATA_DIR = ROOT / "data"
ABILITIES_FILE = DATA_DIR / "abilities.json"
MONSTERS_FILE = DATA_DIR / "monsters.json"
SELECTIONS_FILE = DATA_DIR / "selections.json"

PROMPTS_DIR = ROOT / "prompts"

OUTPUT_DIR = ROOT / "output"
RAW_DIR = OUTPUT_DIR / "raw"          # 1024x1024 variants straight from SDXL
CUTOUT_DIR = OUTPUT_DIR / "cutout"    # full-size variants with transparent background
SPRITES_DIR = OUTPUT_DIR / "sprites"  # 64x64 post-processed variants
SHEETS_DIR = OUTPUT_DIR / "sheets"    # contact sheets for manual selection

EXPORT_DIR = ROOT.parent.parent / "backend" / "monsters" / "seed"

# Image generation
BASE_MODEL = "stabilityai/stable-diffusion-xl-base-1.0"
VAE_MODEL = "madebyollin/sdxl-vae-fp16-fix"
PIXEL_LORA = "nerijs/pixel-art-xl"
LCM_LORA = "latent-consistency/lcm-lora-sdxl"  # optional, faster sampling

VARIANTS_PER_MONSTER = 4

# Post-processing
REMBG_MODEL = "isnet-general-use"  # background removal model, downloaded by rembg on first use
SPRITE_SIZE = 64
SPRITE_MARGIN = 2        # empty pixels kept around the monster
ALPHA_THRESHOLD = 128    # alpha >= threshold becomes opaque, the rest transparent
PALETTE_COLORS = 24      # max colors per sprite

# Game data rules (see docs/design/area_giocatori_mostri.md, section 5)

# Each element is strong against the next one in the list (the last wraps to the first).
ELEMENT_CYCLE = ["silence", "echo", "memory", "time", "chaos", "gravity", "dream"]
ELEMENTS = set(ELEMENT_CYCLE)

RARITIES = ["common", "rare", "legendary"]

TOTAL_MONSTERS = 100
EXPECTED_RARITY_COUNTS = {"common": 60, "rare": 30, "legendary": 10}
EXPECTED_ELEMENT_COUNTS = {
    "dream": 16,
    "memory": 16,
    "time": 15,
    "echo": 15,
    "gravity": 15,
    "silence": 15,
    "chaos": 8,
}

STAT_MIN, STAT_MAX = 1, 100
# Allowed range for the sum of the four base stats, per rarity.
STAT_BUDGET = {"common": (140, 180), "rare": (200, 240), "legendary": (260, 310)}
GROWTH_MIN, GROWTH_MAX = 0.1, 5.0

# Commons drawn at random for each new account; the player picks 3 of them.
STARTER_POOL_SIZE = 10

ABILITY_SCOPES = ["generic", "element", "legendary"]
EFFECT_TYPES = [
    "damage_boost",         # +value% damage dealt
    "damage_reduction",     # -value% damage taken
    "dodge",                # value% chance to avoid an attack
    "heal",                 # restore value% of max HP
    "speed_boost",          # +value% speed
    "reflect",              # return value% of damage taken to the attacker
    "stun",                 # value% chance the opponent loses its next action
    "weaken",               # -value% opponent attack
    "ignore_disadvantage",  # element disadvantage has no effect
    "revive",               # once per encounter, come back with value% HP
    "random_boost",         # +value% to a random stat
    "invert_advantage",     # element advantages are reversed in this encounter
    "copy_ability",         # copy the opponent's ability
]
TRIGGERS = [
    "always",
    "first_turn",
    "hp_above_50",
    "hp_below_50",
    "on_hit",       # when this monster is hit
    "on_attack",    # when this monster attacks
    "once",         # first time the condition applies in an encounter
    "vs_faster",    # against a faster opponent
]
