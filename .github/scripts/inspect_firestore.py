#!/usr/bin/env python3
"""Inspect Firestore databases in the target Firebase project.

Runs inside GitHub Actions (the agent sandbox cannot reach Google APIs).
Every finding is emitted as a workflow annotation so it can be read back
with `gh run view` even when raw log download is unavailable.
"""
import json
import os
import sys
import urllib.error
import urllib.request

KEY_FILE = "/tmp/firebase-key.json"
PROJECT = "gen-lang-client-0251556445"
BASE = f"https://firestore.googleapis.com/v1/projects/{PROJECT}"
SCOPES = ["https://www.googleapis.com/auth/datastore"]

TOKEN = ""


def emit(msg: str) -> None:
    """Print a line as a GitHub Actions error annotation (always visible)."""
    clean = str(msg).replace("%", "%25").replace("\r", "%0D").replace("\n", " ")
    print(f"::error::{clean}")
    print(msg)


def http(method: str, url: str, body: dict | None = None) -> tuple[int, dict | str]:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, json.loads(resp.read().decode() or "{}")
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode(errors="replace")[:300]
        return exc.code, detail
    except Exception as exc:  # noqa: BLE001 - report any network failure
        return 0, str(exc)[:300]


def main() -> int:
    global TOKEN
    if not os.path.exists(KEY_FILE):
        emit("KEY_MISSING: /tmp/firebase-key.json not written")
        return 1

    from google.auth.transport.requests import Request
    from google.oauth2 import service_account

    creds = service_account.Credentials.from_service_account_file(KEY_FILE, scopes=SCOPES)
    creds.refresh(Request())
    TOKEN = creds.token
    emit(f"Auth OK as {creds.service_account_email}")

    status, dbs = http("GET", f"{BASE}/databases")
    if status != 200 or not isinstance(dbs, dict):
        emit(f"LIST_DB_FAILED status={status} body={dbs}")
        return 1
    names = [d.get("name", "") for d in dbs.get("databases", [])]
    if not names:
        emit("NO_DATABASES_FOUND")
        return 0
    emit(f"Found {len(names)} database(s): " + ", ".join(n.split('/databases/')[-1] for n in names))

    for full in names:
        db_id = full.split("/databases/")[-1]
        db_base = f"{BASE}/databases/{db_id}"

        status, cols = http("POST", f"{db_base}/documents:listCollectionIds", {"pageSize": 200})
        if status != 200 or not isinstance(cols, dict):
            emit(f"DB {db_id}: collection list failed status={status} body={cols}")
            continue
        collection_ids = cols.get("collectionIds", [])
        emit(f"DB {db_id}: root collections = {collection_ids or 'NONE (empty)'}")

        if "users" not in collection_ids:
            continue

        status, users = http("GET", f"{db_base}/documents/users?pageSize=3")
        if status != 200 or not isinstance(users, dict):
            emit(f"DB {db_id}: users listing failed status={status} body={users}")
            continue
        docs = users.get("documents", [])
        if not docs:
            emit(f"DB {db_id}: users collection exists but has NO documents")
            continue
        emit(f"DB {db_id}: users has documents (sample: {[d.split('/')[-1] for d in docs]})")

        first_doc = docs[0].split("/documents/")[-1]
        status, subs = http(
            "POST",
            f"{db_base}/documents/{first_doc}:listCollectionIds",
            {"pageSize": 200},
        )
        if status == 200 and isinstance(subs, dict):
            sub_ids = subs.get("collectionIds", [])
            emit(f"DB {db_id}: subcollections under {first_doc} = {sub_ids or 'NONE'}")
            if "sairaSessions" in sub_ids:
                status, sess = http(
                    "GET",
                    f"{db_base}/documents/{first_doc}/sairaSessions?pageSize=3",
                )
                if status == 200 and isinstance(sess, dict):
                    sdocs = sess.get("documents", [])
                    emit(
                        f"DB {db_id}: sairaSessions EXISTS with "
                        f"{len(sdocs)}+ docs (sample: {[d.split('/')[-1] for d in sdocs]})"
                    )
                else:
                    emit(f"DB {db_id}: sairaSessions listing status={status} body={sess}")
        else:
            # Fallback probe: try fetching sairaSessions directly.
            status2, probe = http(
                "GET",
                f"{db_base}/documents/{first_doc}/sairaSessions?pageSize=2",
            )
            exists = status2 == 200
            emit(
                f"DB {db_id}: subcollection probe for sairaSessions -> "
                f"{'EXISTS' if exists else f'NOT_FOUND (status={status2})'}"
            )

    emit("INSPECTION_DONE")
    return 0


if __name__ == "__main__":
    sys.exit(main())
