#!/usr/bin/env python3
"""Collect every fixed UI text (string literals) of the app into
app/src/main/assets/i18n/ui_strings.json.

The app translates ONLY these strings when the user picks a language, so chat
messages, names and other user content are never sent for translation.
Run before every release build (release-apk.yml does it), safe to run anytime.
"""
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "app/src/main/java/com/example")
OUT = os.path.join(ROOT, "app/src/main/assets/i18n/ui_strings.json")
SCAN_DIRS = ("ui", "agent", "call")

LITERAL = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
SKIP_LINE = re.compile(r'^\s*(import |package |@|//|\*|/\*)|testTag\(|Log\.[dewiv]\(|contentType|key = "|route|collection\(|document\(|getString\("|putString\(|Preferences|Regex\(')


def wanted(s: str) -> bool:
    if len(s) < 2 or len(s) > 300 or "$" in s or "\\u" in s:
        return False
    if not re.search(r"[A-Za-z\u0980-\u09FF]", s):
        return False
    if re.fullmatch(r"[a-z0-9_.:/\-#]+", s):          # identifiers, tags, keys, paths
        return False
    if re.match(r"^(https?://|content://|android\.|com\.|image/|video/|audio/|application/|text/)", s):
        return False
    if re.fullmatch(r"[A-Z0-9_]+", s) and len(s) > 3:   # CONSTANT_NAMES
        return False
    return True


def main() -> None:
    found = set()
    for sub in SCAN_DIRS:
        base = os.path.join(SRC, sub)
        for dp, _, files in os.walk(base):
            for f in files:
                if not f.endswith(".kt"):
                    continue
                text = open(os.path.join(dp, f), encoding="utf-8").read()
                text = re.sub(r'"""[\s\S]*?"""', '""', text)     # prompts / raw strings
                for line in text.splitlines():
                    if SKIP_LINE.search(line):
                        continue
                    for m in LITERAL.finditer(line):
                        raw = m.group(1)
                        try:
                            s = json.loads('"' + raw + '"')
                        except Exception:  # noqa: BLE001
                            continue
                        s = s.strip()
                        if wanted(s):
                            found.add(s)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump(sorted(found), fh, ensure_ascii=False, indent=0)
    print(f"ui_strings: {len(found)} texts → {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
