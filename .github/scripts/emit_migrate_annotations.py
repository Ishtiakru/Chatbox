#!/usr/bin/env python3
"""Dump every non-empty line of the migration log as a GitHub annotation.

Pattern-based picking kept hiding the real failure (lines that matched no
regex vanished). Logs are small (~20-60 lines), so dump them all — capped
for safety — and prefix each line with its number for cross-referencing.
"""
import sys


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else "/tmp/migrate.log"
    try:
        with open(path, encoding="utf-8", errors="replace") as fh:
            lines = fh.read().splitlines()
    except FileNotFoundError:
        print("::error::MIGRATE log missing — migration step crashed before logging")
        return 0

    emitted = 0
    for i, raw in enumerate(lines, 1):
        line = raw.rstrip()
        if not line:
            continue
        if emitted >= 55:
            print(f"::error::... log truncated at line {i} (total {len(lines)})")
            break
        clean = line.replace("%", "%25").replace("\r", "%0D").replace("\n", " ").strip()
        print(f"::error::L{i}: {clean}")
        emitted += 1
    if emitted == 0:
        print("::error::(log file was empty)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
