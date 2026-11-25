#!/usr/bin/env python3
"""Emit key lines of the Saira migration log as GitHub Actions annotations.

Raw CI logs cannot be downloaded in the agent sandbox (blob storage is
blocked), so every important line must surface as an annotation, readable
via `gh api .../check-runs/<id>/annotations`.
"""
import re
import sys

FORCED = re.compile(
    r"Docs scanned:|Docs written:|ERROR|Migration failed|Mode:|Source project:|"
    r"Dest project:|Auth users in|Could not list Auth"
)
INTERESTING = re.compile(
    r"^Chatbox Saira|^=|Collections:|Source DB:|Dest DB:|Copying top-level|"
    r"— \d+ doc\(s\)|\(empty\)|WARNING|orphaned|Done\.|Re-run with"
)
UID = re.compile(r"^\s*- uid=")

MAX_LINES = 45
MAX_UIDS = 25


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else "/tmp/migrate.log"
    try:
        with open(path, encoding="utf-8", errors="replace") as fh:
            lines = fh.read().splitlines()
    except FileNotFoundError:
        print("::error::MIGRATE log missing — migration step crashed before logging")
        return 0

    picked: list[str] = []
    uids = 0
    for raw in lines:
        line = raw.rstrip()
        if not line:
            continue
        if FORCED.search(line) or (UID.match(line) and uids < MAX_UIDS):
            if UID.match(line):
                uids += 1
            picked.append(line)
        elif INTERESTING.search(line) and len(picked) < MAX_LINES:
            picked.append(line)

    if not picked:
        picked = lines[-15:] or ["(log was empty)"]

    for line in picked[:50]:
        clean = line.replace("%", "%25").replace("\r", "%0D").replace("\n", " ").strip()
        print(f"::error::{clean}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
