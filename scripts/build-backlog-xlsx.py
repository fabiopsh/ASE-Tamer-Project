"""Builds docs/backlog/Monster_Tamers_Backlog_completed.xlsx (DOC-1) from the course backlog and
   docs/backlog/acceptance_criteria.md: adds the columns Acceptance criteria, Dependencies, Implemented.

   pip install openpyxl==3.1.5
   python3 scripts/build-backlog-xlsx.py docs/consegna/Monster_Tamers_Backlog.xlsx \
       docs/backlog/acceptance_criteria.md docs/backlog/Monster_Tamers_Backlog_completed.xlsx
"""
import re
import sys
from copy import copy

import openpyxl
from openpyxl.styles import Alignment

src, md_path, out = sys.argv[1:4]
md = open(md_path, encoding="utf-8").read()


def plain(text):
    text = re.sub(r"\*\*(.+?)\*\*", r"\1", text)
    text = re.sub(r"`([^`]+)`", r"\1", text)
    return re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text).strip()


stories = {}
for block in re.split(r"\n(?=## US-)", md):
    m = re.match(r"## (US-\d\d) — (.+)", block)
    if not m:
        continue
    sid = m.group(1)
    deps = re.search(r"\*\*Dependencies:\*\* ([^·\n]+)", block)
    phase = re.search(r"\*\*Phase:\*\* ([^\n]+)", block)
    rows = re.findall(r"^\| (\d+) \| (.+?) \| (.+?) \|$", block, re.M)
    criteria = [f"{n}. {plain(c)}" for n, c, _ in rows]
    stories[sid] = {
        "deps": plain(deps.group(1)) if deps else "none",
        "phase": plain(phase.group(1)) if phase else "",
        "ac": "\n".join(criteria),
    }

# Stories without an acceptance-criteria section
NOT_IN_SCOPE = {
    "US-04": ("US-01, US-02", "P2 (planned, optional green story)"),
    "US-08": ("US-01", "no"),
    "US-13": ("US-09, US-12", "Partly P1 (XP and levels from battles); customisation planned in P2"),
    "US-26": ("US-01, US-02", "no"),
    "US-27": ("US-26, US-18", "no"),
}

wb = openpyxl.load_workbook(src)
ws = wb["Project backlog"]
header = ws["I4"]
new_cols = {"J": ("Acceptance criteria (Given / When / Then)", 110),
            "K": ("Dependencies", 22), "L": ("Implemented (P1/P2/no)", 30)}
for col, (title, width) in new_cols.items():
    cell = ws[f"{col}4"]
    cell.value = title
    cell.font, cell.fill, cell.border = copy(header.font), copy(header.fill), copy(header.border)
    cell.alignment = Alignment(wrap_text=True, vertical="center", horizontal="center")
    ws.column_dimensions[col].width = width
ws.merge_cells("J2:L2")
ws["J2"] = "Completed by the group — details and tests: docs/backlog/acceptance_criteria.md"
ws["J2"].font = copy(ws["A2"].font)

for row in range(5, ws.max_row + 1):
    sid = ws[f"A{row}"].value
    if not sid:
        continue
    if sid in stories:
        st = stories[sid]
        ac, deps = st["ac"], st["deps"]
        implemented = "P1" if st["phase"].startswith("P1") else st["phase"]
        if "P2" in st["phase"]:
            implemented = "P1 (partial); completed in P2"
    else:
        deps, implemented = NOT_IN_SCOPE[sid]
        ac = "Not in scope of the delivered stories" if implemented == "no" else "To be written when implemented"
    for col, value in (("J", ac), ("K", deps), ("L", implemented)):
        cell = ws[f"{col}{row}"]
        cell.value = value
        cell.alignment = Alignment(wrap_text=True, vertical="top")
        cell.border = copy(ws[f"I{row}"].border)
    lines = ac.count("\n") + 1
    ws.row_dimensions[row].height = max(30, min(409, 15 * lines * 2))

wb.save(out)
print("stories with AC:", len(stories), sorted(stories))
