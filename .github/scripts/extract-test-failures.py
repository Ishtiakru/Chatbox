#!/usr/bin/env python3
"""Print failing test names + messages from JUnit XML reports (one per line).

Used by build-check.yml so failures are readable from `gh run view` annotations
even when raw Actions log download is unavailable.
"""
import glob
import xml.etree.ElementTree as ET

rows = []
for path in glob.glob("app/build/test-results/**/*.xml", recursive=True):
    try:
        root = ET.parse(path).getroot()
    except Exception:
        continue
    for case in root.iter("testcase"):
        for child in case:
            if child.tag in ("failure", "error"):
                msg = (child.get("message") or str(child.text or "")).strip().replace("\n", " ")
                rows.append(
                    f"{case.get('classname')} > {case.get('name')}: {msg[:400]}"
                )
print("\n".join(rows[:40]) if rows else "NO_FAILURE_ROWS_FOUND")
