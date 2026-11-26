#!/usr/bin/env python3
"""Saira admin agent: Firebase operations that need the admin service account.

Dispatched from the app (admin only, via the admin's own GitHub token).
Results are emitted as `::notice title=SAIRA::` annotations which the app reads.
Never prints secrets or personal data beyond aggregate counts.
"""
import base64
import json
import os
import re
import sys
import urllib.error
import urllib.request

PROJECT = "gen-lang-client-0251556445"
DB = "ai-studio-android-chatbox-bc1e3568-f7e2-4c12-b2b9-9c365ad43fb9"
KEY_FILE = "/tmp/firebase-key.json"
TOKEN = ""


def notice(msg: str) -> None:
    clean = str(msg).replace("%", "%25").replace("\r", "%0D").replace("\n", " ;; ")
    print(f"::notice title=SAIRA::{clean}")
    print(msg)


def http(method, url, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={
        "Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            return r.status, json.loads(r.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode(errors="replace")[:300]
    except Exception as e:  # noqa: BLE001
        return 0, str(e)[:300]


def auth() -> None:
    global TOKEN
    raw = os.environ.get("FIREBASE_ADMIN_KEY", "")
    if not raw.strip():
        notice("Firebase admin key সেট করা নেই (FIREBASE_ADMIN_KEY)।")
        sys.exit(1)
    if not raw.strip().startswith("{"):
        raw = base64.b64decode(re.sub(r"\s+", "", raw)).decode()
    open(KEY_FILE, "w").write(raw)
    from google.auth.transport.requests import Request
    from google.oauth2 import service_account
    creds = service_account.Credentials.from_service_account_file(
        KEY_FILE, scopes=["https://www.googleapis.com/auth/cloud-platform"])
    creds.refresh(Request())
    TOKEN = creds.token


def count(collection: str) -> str:
    url = f"https://firestore.googleapis.com/v1/projects/{PROJECT}/databases/{DB}/documents:runAggregationQuery"
    body = {"structuredAggregationQuery": {
        "structuredQuery": {"from": [{"collectionId": collection}]},
        "aggregations": [{"alias": "n", "count": {}}]}}
    st, res = http("POST", url, body)
    if st != 200 or not isinstance(res, list):
        return f"? ({st})"
    try:
        return res[0]["result"]["aggregateFields"]["n"]["integerValue"]
    except Exception:  # noqa: BLE001
        return "0"


def report() -> None:
    parts = []
    for c in ["profiles", "users", "conversations", "shortIds", "phoneDirectory"]:
        parts.append(f"{c}={count(c)}")
    st, res = http("POST", f"https://identitytoolkit.googleapis.com/v1/projects/{PROJECT}/accounts:query",
                   {"returnUserInfo": False})
    auth_users = res.get("recordsCount", "?") if isinstance(res, dict) else f"? ({st})"
    st2, rel = http("GET", f"https://firebaserules.googleapis.com/v1/projects/{PROJECT}/releases")
    rules = "?"
    if isinstance(rel, dict):
        names = [r.get("name", "").split("/")[-1] for r in rel.get("releases", [])]
        rules = ",".join(n for n in names if n)[:120] or "none"
    notice(f"Firebase রিপোর্ট: Auth ইউজার={auth_users}; " + "; ".join(parts) + f"; rules releases={rules}")


def main() -> int:
    op = (os.environ.get("OP") or "report").strip()
    auth()
    if op == "report":
        report()
    else:
        notice(f"অজানা অপারেশন: {op}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
