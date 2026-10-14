"""Validate abilities.json and monsters.json.

Checks: unique ids and names, stat ranges (1-100), rarity and element
distributions, valid ability references, enough commons for the starter pool.
Exits with status 1 if any error is found.
"""

import json
import sys
from collections import Counter

import config

STATS = ["hp", "attack", "defense", "speed"]

ABILITY_FIELDS = {
    "ability_id": int,
    "name": str,
    "scope": str,
    "element": (str, type(None)),
    "effect_type": str,
    "effect_value": int,
    "trigger": str,
    "description": str,
}

MONSTER_FIELDS = {
    "monster_id": int,
    "name": str,
    "element": str,
    "rarity": str,
    "archetype": str,
    **{f"base_{s}": int for s in STATS},
    **{f"growth_{s}": (int, float) for s in STATS},
    "ability_id": int,
    "description": str,
    "image_prompt": str,
    "image_url": str,
}


class Report:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []

    def error(self, msg: str) -> None:
        self.errors.append(msg)

    def warn(self, msg: str) -> None:
        self.warnings.append(msg)


def load(path, report: Report) -> list[dict]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        report.error(f"{path.name}: file not found")
        return []
    except json.JSONDecodeError as exc:
        report.error(f"{path.name}: invalid JSON ({exc})")
        return []
    if not isinstance(data, list):
        report.error(f"{path.name}: top level must be a list")
        return []
    return data


def check_fields(item: dict, fields: dict, label: str, report: Report) -> bool:
    ok = True
    for field, expected in fields.items():
        if field not in item:
            report.error(f"{label}: missing field '{field}'")
            ok = False
        elif isinstance(item[field], bool) and expected is not bool:
            report.error(f"{label}: field '{field}' must not be a boolean")
            ok = False
        elif not isinstance(item[field], expected):
            report.error(f"{label}: field '{field}' has wrong type {type(item[field]).__name__}")
            ok = False
    for field in item.keys() - fields.keys():
        report.warn(f"{label}: unexpected field '{field}'")
    return ok


def check_unique(items: list[dict], key: str, label: str, report: Report) -> None:
    values = [str(i.get(key)).casefold() for i in items]
    for value, count in Counter(values).items():
        if count > 1:
            report.error(f"{label}: duplicate {key} '{value}' ({count} times)")


def validate_abilities(abilities: list[dict], report: Report) -> dict[int, dict]:
    valid = [a for a in abilities if check_fields(a, ABILITY_FIELDS, f"ability {a.get('ability_id', '?')}", report)]
    check_unique(valid, "ability_id", "abilities", report)
    check_unique(valid, "name", "abilities", report)

    for a in valid:
        label = f"ability {a['ability_id']} ({a['name']})"
        if a["scope"] not in config.ABILITY_SCOPES:
            report.error(f"{label}: unknown scope '{a['scope']}'")
        if a["effect_type"] not in config.EFFECT_TYPES:
            report.error(f"{label}: unknown effect_type '{a['effect_type']}'")
        if a["trigger"] not in config.TRIGGERS:
            report.error(f"{label}: unknown trigger '{a['trigger']}'")
        if not 1 <= a["effect_value"] <= 100:
            report.error(f"{label}: effect_value {a['effect_value']} outside 1-100")
        if a["scope"] == "generic" and a["element"] is not None:
            report.error(f"{label}: generic ability must have element null")
        if a["scope"] in ("element", "legendary") and a["element"] not in config.ELEMENTS:
            report.error(f"{label}: {a['scope']} ability needs a valid element, got '{a['element']}'")
        if not a["description"].strip():
            report.error(f"{label}: empty description")

    return {a["ability_id"]: a for a in valid}


def validate_monsters(monsters: list[dict], abilities: dict[int, dict], report: Report) -> None:
    valid = [m for m in monsters if check_fields(m, MONSTER_FIELDS, f"monster {m.get('monster_id', '?')}", report)]
    check_unique(valid, "monster_id", "monsters", report)
    check_unique(valid, "name", "monsters", report)
    check_unique(valid, "image_url", "monsters", report)

    ids = sorted(m["monster_id"] for m in valid)
    if ids != list(range(1, len(ids) + 1)):
        report.error("monsters: monster_id values must be 1..N without gaps")

    if len(valid) != config.TOTAL_MONSTERS:
        report.error(f"monsters: expected {config.TOTAL_MONSTERS}, found {len(valid)}")

    used_abilities = Counter()
    for m in valid:
        label = f"monster {m['monster_id']} ({m['name']})"
        if m["element"] not in config.ELEMENTS:
            report.error(f"{label}: unknown element '{m['element']}'")
        if m["rarity"] not in config.RARITIES:
            report.error(f"{label}: unknown rarity '{m['rarity']}'")
            continue

        for s in STATS:
            base = m[f"base_{s}"]
            growth = m[f"growth_{s}"]
            if not config.STAT_MIN <= base <= config.STAT_MAX:
                report.error(f"{label}: base_{s}={base} outside {config.STAT_MIN}-{config.STAT_MAX}")
            if not config.GROWTH_MIN <= growth <= config.GROWTH_MAX:
                report.error(f"{label}: growth_{s}={growth} outside {config.GROWTH_MIN}-{config.GROWTH_MAX}")

        total = sum(m[f"base_{s}"] for s in STATS)
        low, high = config.STAT_BUDGET[m["rarity"]]
        if not low <= total <= high:
            report.error(f"{label}: stat total {total} outside {low}-{high} for {m['rarity']}")

        ability = abilities.get(m["ability_id"])
        if ability is None:
            report.error(f"{label}: ability_id {m['ability_id']} does not exist")
        else:
            used_abilities[ability["ability_id"]] += 1
            scope = ability["scope"]
            if scope != "generic" and ability["element"] != m["element"]:
                report.error(f"{label}: uses {scope} ability '{ability['name']}' of element '{ability['element']}'")
            if m["rarity"] == "legendary" and scope != "legendary":
                report.error(f"{label}: legendary monsters must have a legendary ability")
            if m["rarity"] != "legendary" and scope == "legendary":
                report.error(f"{label}: only legendary monsters can have legendary abilities")
            if m["rarity"] == "rare" and scope == "generic":
                report.warn(f"{label}: rare monster with a generic ability")

        if len(m["description"].strip()) < 20:
            report.error(f"{label}: description too short")
        if len(m["image_prompt"].strip()) < 20:
            report.error(f"{label}: image_prompt too short")

    rarity_counts = Counter(m["rarity"] for m in valid)
    for rarity, expected in config.EXPECTED_RARITY_COUNTS.items():
        if rarity_counts[rarity] != expected:
            report.error(f"rarity '{rarity}': expected {expected}, found {rarity_counts[rarity]}")

    element_counts = Counter(m["element"] for m in valid)
    for element, expected in config.EXPECTED_ELEMENT_COUNTS.items():
        if element_counts[element] != expected:
            report.error(f"element '{element}': expected {expected}, found {element_counts[element]}")

    # The starter pool is drawn per account from the commons, so there must be enough of them.
    if rarity_counts["common"] < config.STARTER_POOL_SIZE:
        report.error(f"starter pool: needs at least {config.STARTER_POOL_SIZE} commons, found {rarity_counts['common']}")

    for ability_id, ability in abilities.items():
        if used_abilities[ability_id] == 0:
            report.warn(f"ability {ability_id} ({ability['name']}) is not used by any monster")

    return valid


def print_summary(monsters: list[dict], abilities: dict[int, dict]) -> None:
    print(f"\n{len(abilities)} abilities, {len(monsters)} monsters\n")
    header = f"{'element':<9}" + "".join(f"{r:>10}" for r in config.RARITIES) + f"{'total':>8}"
    print(header)
    print("-" * len(header))
    for element in config.ELEMENT_CYCLE:
        row = [m for m in monsters if m["element"] == element]
        counts = Counter(m["rarity"] for m in row)
        print(f"{element:<9}" + "".join(f"{counts[r]:>10}" for r in config.RARITIES) + f"{len(row):>8}")

    print("\naverage base stat total per rarity:")
    for rarity in config.RARITIES:
        totals = [sum(m[f"base_{s}"] for s in STATS) for m in monsters if m["rarity"] == rarity]
        if totals:
            print(f"  {rarity:<10} {sum(totals) / len(totals):6.1f}  (min {min(totals)}, max {max(totals)})")


def main() -> None:
    report = Report()
    abilities = validate_abilities(load(config.ABILITIES_FILE, report), report)
    monsters = validate_monsters(load(config.MONSTERS_FILE, report), abilities, report)

    for msg in report.warnings:
        print(f"WARNING  {msg}")
    for msg in report.errors:
        print(f"ERROR    {msg}")

    if monsters:
        print_summary(monsters, abilities)

    if report.errors:
        print(f"\nFAILED: {len(report.errors)} error(s), {len(report.warnings)} warning(s)")
        sys.exit(1)
    print(f"\nOK: 0 errors, {len(report.warnings)} warning(s)")


if __name__ == "__main__":
    main()
