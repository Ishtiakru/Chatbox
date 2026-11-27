#!/usr/bin/env python3
"""Read AI keys that the admin added from inside the app (Saira chat):

  settings/aiKeyPool        → {keys: [Gemini keys]}   (shared pool, all users)
  adminVault/sairaProKeys   → {keys: "<one pro key per line>"}  (owner-only)

Uses the FIREBASE_ADMIN_KEY service account. Never prints keys; every failure
just returns an empty result so builds never break because of it.
"""
from __future__ import annotations

import base64
import json
import os
import re
import urllib.parse
import urllib.request

PROJECT = "gen-lang-client-0251556445"
DB = "ai-studio-android-chatbox-bc1e3568-f7e2-4c12-b2b9-9c365ad43fb9"


def _token(admin_raw: str | None) -> str | None:
    raw = admin_raw if admin_raw is not None else os.environ.get("FIREBASE_ADMIN_KEY", "")
    if not raw or not raw.strip():
        return None
    try:
        from google.auth.transport.requests import Request
        from google.oauth2 import service_account
        if not raw.lstrip().startswith("{"):
            raw = base64.b64decode(re.sub(r"\s+", "", raw)).decode()
        c = service_account.Credentials.from_service_account_info(
            json.loads(raw), scopes=["https://www.googleapis.com/auth/datastore"])
        c.refresh(Request())
        return c.token
    except Exception as exc:  # noqa: BLE001
        print(f"[firestore_keys] no admin token: {type(exc).__name__}")
        return None


def _doc(token: str, path: str) -> dict:
    url = (f"https://firestore.googleapis.com/v1/projects/{PROJECT}/databases/"
           f"{urllib.parse.quote(DB, safe='')}/documents/{path}")
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read().decode()).get("fields", {})
    except Exception as exc:  # noqa: BLE001
        print(f"[firestore_keys] {path}: {type(exc).__name__}")
        return {}


def load(admin_raw: str | None = None) -> tuple[list[str], str]:
    """(gemini_pool_keys, pro_keys_text)."""
    token = _token(admin_raw)
    if not token:
        return [], ""
    pool = _doc(token, "settings/aiKeyPool")
    vals = pool.get("keys", {}).get("arrayValue", {}).get("values") or []
    gemini = [v.get("stringValue", "").strip() for v in vals if v.get("stringValue", "").strip()]
    vault = _doc(token, "adminVault/sairaProKeys")
    pro = vault.get("keys", {}).get("stringValue", "") or ""
    print(f"[firestore_keys] pool={len(gemini)} pro_lines={len([l for l in pro.splitlines() if l.strip()])}")
    return gemini, pro


if __name__ == "__main__":
    # Used by release-apk.yml: append the Firestore pool to a key file (one per line).
    import sys
    g, _ = load()
    if len(sys.argv) > 1 and g:
        with open(sys.argv[1], "a", encoding="utf-8") as fh:
            fh.write("\n" + "\n".join(g) + "\n")
