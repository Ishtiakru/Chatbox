#!/usr/bin/env python3
"""Push the Gemini API key pool into Firestore settings/aiKeyPool.

Reads a newline-separated key list (one API key per line; '#' comments and
blank lines ignored) and PATCHes the app's Firestore document:

    settings/aiKeyPool = { keys: [..], count: N, updatedAt: <ts> }

The app (GeminiKeyPool.kt) reads this document at runtime and rotates through
every key — hot-swappable, no APK rebuild required.

Auth: FIREBASE_ADMIN_KEY service account (same key the migrate pipeline uses).

Usage:
  python3 sync_keypool.py --key dest.json --pool pool.txt \
      [--project gen-lang-client-0251556445] \
      [--db ai-studio-android-chatbox-bc1e3568-f7e2-4c12-b2b9-9c365ad43fb9]
"""
import argparse
import datetime
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

from google.auth.transport.requests import Request as AuthTransportRequest
from google.oauth2 import service_account

SCOPES = ["https://www.googleapis.com/auth/datastore"]
DEFAULT_PROJECT = "gen-lang-client-0251556445"
DEFAULT_DB = (
    "ai-studio-android-chatbox-bc1e3568-f7e2-4c12-b2b9-9c365ad43fb9"
)


def notice(msg: str) -> None:
    clean = str(msg).replace("%", "%25").replace("\r", "%0D").replace("\n", " ")
    print(f"::notice title=KeyPoolSync::{clean}")
    print(msg)


def fail(msg: str) -> None:
    clean = str(msg).replace("%", "%25").replace("\r", "%0D").replace("\n", " ")
    print(f"::error::{clean}")
    print(msg, file=sys.stderr)
    sys.exit(1)


def load_keys(path: str) -> list[str]:
    keys: list[str] = []
    seen: set[str] = set()
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            key = line.strip()
            if not key or key.startswith("#"):
                continue
            if key in seen:
                continue
            seen.add(key)
            keys.append(key)
    return keys


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--key", required=True, help="service-account JSON path")
    ap.add_argument("--pool", required=True, help="newline-separated key list")
    ap.add_argument("--project", default=DEFAULT_PROJECT)
    ap.add_argument("--db", default=DEFAULT_DB)
    args = ap.parse_args()

    key_info = json.load(open(args.key, encoding="utf-8"))
    if key_info.get("type") != "service_account":
        fail("admin key is not a service-account JSON")

    keys = load_keys(args.pool)
    if not keys:
        fail("pool file contains zero keys")
    if len(keys) > 5000:
        fail(f"pool too large: {len(keys)} keys (cap 5000)")

    creds = service_account.Credentials.from_service_account_info(
        key_info, scopes=SCOPES
    )
    if not creds.valid:
        creds.refresh(AuthTransportRequest())

    base = (
        f"https://firestore.googleapis.com/v1/projects/{args.project}"
        f"/databases/{urllib.parse.quote(args.db, safe='')}/documents"
    )
    doc_url = f"{base}/settings/aiKeyPool"

    body = {
        "fields": {
            "keys": {
                "arrayValue": {"values": [{"stringValue": k} for k in keys]}
            },
            "count": {"integerValue": str(len(keys))},
            "updatedAt": {
                "timestampValue": datetime.datetime.now(
                    datetime.timezone.utc
                ).strftime("%Y-%m-%dT%H:%M:%SZ")
            },
        }
    }

    req = urllib.request.Request(
        doc_url, data=json.dumps(body).encode(), method="PATCH"
    )
    req.add_header("Authorization", f"Bearer {creds.token}")
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            status = resp.status
    except urllib.error.HTTPError as exc:
        payload = exc.read().decode(errors="replace")[:500]
        fail(f"PATCH failed HTTP {exc.code}: {payload}")
    except Exception as exc:  # noqa: BLE001
        fail(f"PATCH failed: {type(exc).__name__}: {exc}")

    if status not in (200, 201):
        fail(f"PATCH unexpected status {status}")

    # Read-back verification (admin bypasses rules; proves the write landed).
    get_req = urllib.request.Request(doc_url)
    get_req.add_header("Authorization", f"Bearer {creds.token}")
    with urllib.request.urlopen(get_req, timeout=60) as resp:
        saved = json.loads(resp.read().decode())
    saved_count = len(
        (saved.get("fields", {}).get("keys", {}).get("arrayValue", {}).get("values")) or []
    )
    if saved_count != len(keys):
        fail(f"read-back mismatch: wrote {len(keys)}, found {saved_count}")

    notice(
        f"mode=WRITE db={args.db} keys_written={len(keys)} "
        f"readback={saved_count} doc=settings/aiKeyPool"
    )


if __name__ == "__main__":
    main()
