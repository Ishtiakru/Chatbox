#!/usr/bin/env python3
"""Announce a published release to every installed app.

Writes Firestore settings/sparkUpdate = {active, version, note, downloadUrl}.
Older app versions only light the green "App updates" button from this doc,
so every release must refresh it (newer versions also check GitHub directly).

Usage: announce_update.py <version|vTAG>     (FIREBASE_ADMIN_KEY in env)
Never fails the build: problems are reported as warnings.
"""
from __future__ import annotations

import datetime as dt
import json
import sys
import urllib.parse
import urllib.request

sys.path.insert(0, __file__.rsplit("/", 1)[0])
import firestore_keys  # noqa: E402  (shares the service-account auth)

NOTE = "নতুন ফিচার ও উন্নতি এসেছে। \"এখনই আপডেট\" চাপুন — অ্যাপের ভেতরেই বসে যাবে।"
URL = "https://github.com/Ishtiakru/Chatbox/releases/latest"


def main() -> int:
    if len(sys.argv) < 2 or not sys.argv[1].strip():
        print("::warning::announce_update: no version given")
        return 0
    version = sys.argv[1].strip().lstrip("v")
    token = firestore_keys._token(None)
    if not token:
        print("::warning::announce_update: FIREBASE_ADMIN_KEY unavailable — sparkUpdate not refreshed")
        return 0
    fields = {
        "active": {"booleanValue": True},
        "version": {"stringValue": version},
        "note": {"stringValue": NOTE},
        "downloadUrl": {"stringValue": URL},
        "updatedAt": {"timestampValue": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")},
        "updatedBy": {"stringValue": "release-apk"},
    }
    mask = "&".join("updateMask.fieldPaths=" + k for k in fields)
    url = (f"https://firestore.googleapis.com/v1/projects/{firestore_keys.PROJECT}/databases/"
           f"{urllib.parse.quote(firestore_keys.DB, safe='')}/documents/settings/sparkUpdate?{mask}")
    req = urllib.request.Request(
        url, method="PATCH", data=json.dumps({"fields": fields}).encode(),
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            r.read()
        print(f"::notice::sparkUpdate announced v{version} (all installed apps will show the green update button)")
    except Exception as exc:  # noqa: BLE001
        body = getattr(exc, "read", lambda: b"")()[:200]
        print(f"::warning::announce_update failed: {type(exc).__name__} {body!r}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
