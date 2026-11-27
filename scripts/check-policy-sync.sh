#!/usr/bin/env bash
# Verifies INFRA-10 consistency:
# 1. backend/security/access-policy.json parses cleanly with no duplicate method+shape rules.
# 2. AccessPolicy.java, AccessPolicyFilter.java, RequestIdFilter.java (6 services) and
#    CurrentPlayer.java (5 services) are verbatim copies apart from their package declaration.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

python3 - <<'PY'
import json, pathlib, sys

policy_path = pathlib.Path("backend/security/access-policy.json")
data = json.loads(policy_path.read_text())
rules = data.get("rules", [])
assert len(rules) > 0, "access-policy.json has no rules"

seen = set()
for r in rules:
    for f in ("service", "method", "path", "access"):
        assert r.get(f), f"Missing {f} in rule {r}"
    segs = [("{*}" if s.startswith("{") and s.endswith("}") else s) for s in r["path"].strip("/").split("/") if s]
    key = f"{r['method'].upper()} /{'/'.join(segs)}"
    assert key not in seen, f"Duplicate/ambiguous policy rule: {key}"
    seen.add(key)
    if r["access"] == "roles":
        assert r.get("roles"), f"Missing roles in {key}"
print(f"OK access-policy.json: {len(rules)} unambiguous rules")

ref_svc = "players"
services = ["auth", "players", "monsters", "teams", "encounters", "history"]
common_files = ["AccessPolicy.java", "AccessPolicyFilter.java", "RequestIdFilter.java", "CurrentPlayer.java"]

def strip_pkg(text):
    lines = text.splitlines()
    return "\n".join(l for l in lines if not l.startswith("package "))

for fname in common_files:
    ref_path = pathlib.Path(f"backend/{ref_svc}/src/main/java/it/unipi/ase/tamer/{ref_svc}/security/{fname}")
    ref_body = strip_pkg(ref_path.read_text())
    for svc in services:
        if svc == "auth" and fname == "CurrentPlayer.java":
            continue
        p = pathlib.Path(f"backend/{svc}/src/main/java/it/unipi/ase/tamer/{svc}/security/{fname}")
        assert p.exists(), f"Missing {p}"
        if strip_pkg(p.read_text()) != ref_body:
            print(f"DRIFT detected in {p} vs {ref_path}", file=sys.stderr)
            sys.exit(1)
    print(f"OK verbatim sync: {fname}")
PY
