"""Generate raw images with SDXL + Pixel Art XL LoRA.

Input:  data/monsters.json (image prompt per monster), prompts/template.json
Output: output/raw/<monster_id>_<seed>.png, VARIANTS_PER_MONSTER per monster,
        plus output/raw/manifest.jsonl with the parameters of every image.
Fixed seeds (monster_id * 1000 + variant) so every variant can be regenerated
identically. Existing images are skipped, so an interrupted run can be resumed.

Examples:
    python generate.py --dry-run --ids 1-5     # print prompts, no GPU
    python generate.py --lcm --ids 1-3         # fast test on 3 monsters
    python generate.py --lcm                   # all monsters
    python generate.py --ids 12 --variants 8 --force
"""

import argparse
import json
import os
import time
from datetime import datetime, timezone

import config

# Windows without developer mode cannot create symlinks in the HF cache; harmless, just noisy.
os.environ.setdefault("HF_HUB_DISABLE_SYMLINKS_WARNING", "1")

TEMPLATE_FILE = config.PROMPTS_DIR / "template.json"
MANIFEST_FILE = config.RAW_DIR / "manifest.jsonl"
PIXEL_LORA_FILE = "pixel-art-xl.safetensors"
MAX_PROMPT_TOKENS = 77  # CLIP limit: anything beyond is truncated by SDXL

# (steps, guidance, pixel LoRA weight)
DEFAULTS = {"lcm": (8, 1.5, 1.2), "standard": (30, 7.0, 1.0)}


def parse_ids(spec: str | None, available: list[int]) -> list[int]:
    """Parse '1-10,15,20-22' into a sorted list of ids; None means all."""
    if not spec:
        return available
    ids: set[int] = set()
    for part in spec.split(","):
        part = part.strip()
        if "-" in part:
            start, end = (int(x) for x in part.split("-", 1))
            ids.update(range(start, end + 1))
        elif part:
            ids.add(int(part))
    unknown = ids - set(available)
    if unknown:
        raise SystemExit(f"unknown monster ids: {sorted(unknown)}")
    return sorted(ids)


def build_prompt(monster: dict, template: dict) -> str:
    # Light monsters are generated on a dark background so rembg can separate them.
    backgrounds = template["background"]
    background = backgrounds.get(monster["element"], backgrounds["default"])
    return ", ".join([
        template["prefix"],
        monster["image_prompt"],
        template["rarity"][monster["rarity"]],
        template["element"][monster["element"]],
        template["suffix"].format(background=background),
    ])


def seed_for(monster_id: int, variant: int) -> int:
    return monster_id * 1000 + variant


def raw_path(monster_id: int, seed: int):
    return config.RAW_DIR / f"{monster_id:03d}_{seed}.png"


def load_tokenizer():
    from transformers import CLIPTokenizer

    return CLIPTokenizer.from_pretrained(config.BASE_MODEL, subfolder="tokenizer")


def load_pipeline(use_lcm: bool, lora_weight: float):
    import torch
    from diffusers import AutoencoderKL, LCMScheduler, StableDiffusionXLPipeline

    vae = AutoencoderKL.from_pretrained(config.VAE_MODEL, torch_dtype=torch.float16)
    pipe = StableDiffusionXLPipeline.from_pretrained(
        config.BASE_MODEL,
        vae=vae,
        torch_dtype=torch.float16,
        variant="fp16",
        use_safetensors=True,
    )
    pipe.load_lora_weights(config.PIXEL_LORA, weight_name=PIXEL_LORA_FILE, adapter_name="pixel")
    if use_lcm:
        pipe.load_lora_weights(config.LCM_LORA, adapter_name="lcm")
        pipe.set_adapters(["lcm", "pixel"], adapter_weights=[1.0, lora_weight])
        pipe.scheduler = LCMScheduler.from_config(pipe.scheduler.config)
    else:
        pipe.set_adapters(["pixel"], adapter_weights=[lora_weight])
    pipe.to("cuda")
    pipe.set_progress_bar_config(disable=True)
    return pipe


def format_duration(seconds: float) -> str:
    minutes, seconds = divmod(int(seconds), 60)
    return f"{minutes}m{seconds:02d}s"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ids", help="monster ids to generate, e.g. '1-10,15' (default: all)")
    parser.add_argument("--variants", type=int, default=config.VARIANTS_PER_MONSTER, help="variants per monster")
    parser.add_argument("--lcm", action="store_true", help="use the LCM LoRA (8 steps, ~4x faster)")
    parser.add_argument("--steps", type=int, help="inference steps (default: 8 with --lcm, 30 otherwise)")
    parser.add_argument("--guidance", type=float, help="guidance scale (default: 1.5 with --lcm, 7.0 otherwise)")
    parser.add_argument("--lora-weight", type=float, help="Pixel Art XL weight (default: 1.2 with --lcm, 1.0 otherwise)")
    parser.add_argument("--size", type=int, default=1024, help="output width and height in pixels")
    parser.add_argument("--force", action="store_true", help="regenerate images that already exist")
    parser.add_argument("--dry-run", action="store_true", help="print prompts and planned files without generating")
    args = parser.parse_args()

    default_steps, default_guidance, default_weight = DEFAULTS["lcm" if args.lcm else "standard"]
    steps = args.steps or default_steps
    guidance = args.guidance if args.guidance is not None else default_guidance
    lora_weight = args.lora_weight if args.lora_weight is not None else default_weight

    template = json.loads(TEMPLATE_FILE.read_text(encoding="utf-8"))
    monsters = {m["monster_id"]: m for m in json.loads(config.MONSTERS_FILE.read_text(encoding="utf-8"))}
    ids = parse_ids(args.ids, sorted(monsters))
    negative = template["negative"]

    tokenizer = load_tokenizer()
    jobs = []
    for monster_id in ids:
        monster = monsters[monster_id]
        prompt = build_prompt(monster, template)
        tokens = len(tokenizer(prompt).input_ids)
        if tokens > MAX_PROMPT_TOKENS:
            print(f"WARNING  {monster_id:03d} {monster['name']}: prompt is {tokens} tokens, "
                  f"the part after {MAX_PROMPT_TOKENS} will be ignored")
        for variant in range(args.variants):
            seed = seed_for(monster_id, variant)
            path = raw_path(monster_id, seed)
            if path.exists() and not args.force:
                continue
            jobs.append((monster, variant, seed, prompt, path))

    mode = "LCM" if args.lcm else "standard"
    print(f"{len(ids)} monsters, {len(jobs)} images to generate "
          f"({mode}: {steps} steps, guidance {guidance}, LoRA weight {lora_weight}, {args.size}px)")

    if args.dry_run:
        for monster, variant, seed, prompt, path in jobs:
            if variant == 0:
                print(f"\n{monster['monster_id']:03d} {monster['name']} ({monster['element']}, {monster['rarity']})")
                print(f"  prompt:   {prompt}")
            print(f"  seed {seed} -> {path.relative_to(config.ROOT)}")
        print(f"\nnegative: {negative}")
        return
    if not jobs:
        print("nothing to do (use --force to regenerate existing images)")
        return

    import torch

    print("loading pipeline...")
    pipe = load_pipeline(args.lcm, lora_weight)
    config.RAW_DIR.mkdir(parents=True, exist_ok=True)

    start = time.perf_counter()
    with MANIFEST_FILE.open("a", encoding="utf-8") as manifest:
        for done, (monster, variant, seed, prompt, path) in enumerate(jobs, start=1):
            image = pipe(
                prompt=prompt,
                negative_prompt=negative,
                num_inference_steps=steps,
                guidance_scale=guidance,
                width=args.size,
                height=args.size,
                generator=torch.Generator("cuda").manual_seed(seed),
            ).images[0]
            image.save(path)

            manifest.write(json.dumps({
                "monster_id": monster["monster_id"],
                "name": monster["name"],
                "variant": variant,
                "seed": seed,
                "file": path.name,
                "prompt": prompt,
                "negative_prompt": negative,
                "lcm": args.lcm,
                "steps": steps,
                "guidance": guidance,
                "lora_weight": lora_weight,
                "size": args.size,
                "created_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            }) + "\n")
            manifest.flush()

            elapsed = time.perf_counter() - start
            remaining = elapsed / done * (len(jobs) - done)
            print(f"[{done}/{len(jobs)}] {monster['monster_id']:03d} {monster['name']} seed {seed} "
                  f"({elapsed / done:.1f}s/img, ETA {format_duration(remaining)})", flush=True)

    print(f"done in {format_duration(time.perf_counter() - start)} -> {config.RAW_DIR}")


if __name__ == "__main__":
    main()
