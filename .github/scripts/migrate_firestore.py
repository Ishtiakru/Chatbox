#!/usr/bin/env python3
"""Migrate Firestore data between Firebase projects via REST (dry-run default).

Why REST: the firebase-admin Node SDK was killed mid-network-call on the
runner (runs 45547449479+); python + google-auth + urllib completed full
inspections reliably on the same runner.

Copies top-level collections (default: users) recursively — including
users/{uid}/sairaSessions/{sessionId}/exchanges — preserving document IDs
and TypedValue field JSON (identical on read and write).

Usage:
  python3 migrate_firestore.py --source-key source.json --dest-key dest.json \
      [--source-db ID] [--dest-db ID] [--collections users] [--execute]
"""
import argparse
import base64
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

from google.oauth2 import service_account

SCOPES = ["https://www.googleapis.com/auth/datastore"]
DEFAULT_SOURCE_DB = "ai-studio-android-chatbox-f5f16960-82e9-4756-8ee4-5c3fe55bdd70"


def emit(msg) -> None:
    clean = str(msg).replace("%", "%25").replace("\r", "%0D").replace("\n", " ")
    print(f"::error::{clean}")
    print(msg)


def load_key(path: str) -> dict:
    raw = open(path, encoding="utf-8").read().strip()
    if not raw.startswith("{"):
        raw = base64.b64decode(re.sub(r"\s+", "", raw)).decode()
    data = json.loads(raw)
    if data.get("type") != "service_account":
        raise ValueError(f"{path}: not a service-account key")
    return data


class Client:
    def __init__(self, key: dict, project: str, db: str):
        self.project = project
        self.db = db
        self.creds = service_account.Credentials.from_service_account_info(
            key, scopes=SCOPES
        )
        self.base = (
            f"https://firestore.googleapis.com/v1/projects/{project}"
            f"/databases/{urllib.parse.quote(db, safe='')}/documents"
        )
        self.db_root = f"https://firestore.googleapis.com/v1/projects/{project}/databases"

    def http(self, method: str, url: str, body: dict | None = None):
        data = json.dumps(body).encode() if body is not None else None
        try:
            if not self.creds.valid:
                self.creds.refresh(
                    urllib.request.Request("https://oauth2.googleapis.com/token")
                )
            req = urllib.request.Request(url, data=data, method=method)
            req.add_header("Authorization", f"Bearer {self.creds.token}")
            if data:
                req.add_header("Content-Type", "application/json")
            with urllib.request.urlopen(req, timeout=60) as resp:
                return resp.status, json.loads(resp.read().decode() or "{}")
        except urllib.error.HTTPError as exc:
            payload = exc.read().decode(errors="replace")[:600]
            try:
                return exc.code, json.loads(payload)
            except Exception:
                return exc.code, payload
        except Exception as exc:  # noqa: BLE001
            return 0, f"{type(exc).__name__}: {exc}"[:400]

    def list_databases(self):
        status, body = self.http("GET", self.db_root)
        if status != 200 or not isinstance(body, dict):
            return []
        return [d.get("name", "").split("/databases/")[-1] for d in body.get("databases", [])]

    def list_collection_ids(self):
        status, body = self.http(
            "POST", f"{self.base}:listCollectionIds", {"pageSize": 200}
        )
        if status != 200 or not isinstance(body, dict):
            return None, f"status={status} body={body}"
        return body.get("collectionIds", []), None

    def list_docs(self, col_path: str):
        """Yield Document dicts for a collection path like 'users'."""
        token = None
        while True:
            url = f"{self.base}/{urllib.parse.quote(col_path, safe='/')}?pageSize=300"
            if token:
                url += f"&pageToken={urllib.parse.quote(token, safe='')}"
            status, body = self.http("GET", url)
            if status != 200 or not isinstance(body, dict):
                raise RuntimeError(f"list {col_path}: status={status} body={body}")
            for doc in body.get("documents", []):
                yield doc
            token = body.get("nextPageToken")
            if not token:
                return

    def subcollection_ids(self, doc_path: str):
        status, body = self.http(
            "POST",
            f"{self.base}/{doc_path}:listCollectionIds",
            {"pageSize": 200},
        )
        if status != 200 or not isinstance(body, dict):
            return []
        return body.get("collectionIds", [])

    def write_doc(self, doc_path: str, fields: dict) -> None:
        status, body = self.http(
            "PATCH", f"{self.base}/{doc_path}", {"fields": fields}
        )
        if status != 200:
            raise RuntimeError(f"write {doc_path}: status={status} body={body}")


def doc_rel_path(doc: dict) -> str:
    """'projects/p/databases/d/documents/users/x' -> 'users/x'."""
    name = doc.get("name", "")
    return name.split("/documents/", 1)[-1]


def migrate_collection(
    src: Client, dst: Client, col_path: str, execute: bool, stats: dict
) -> None:
    for doc in src.list_docs(col_path):
        rel = doc_rel_path(doc)
        stats["docs"] += 1
        if execute:
            dst.write_doc(rel, doc.get("fields", {}))
            stats["writes"] += 1
        for sub in src.subcollection_ids(rel):
            migrate_collection(
                src, dst, f"{rel}/{sub}", execute, stats
            )


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--source-key", required=True)
    ap.add_argument("--dest-key", required=True)
    ap.add_argument("--source-db", default=DEFAULT_SOURCE_DB)
    ap.add_argument("--dest-db", required=True)
    ap.add_argument("--collections", default="users")
    ap.add_argument("--execute", action="store_true")
    args = ap.parse_args()

    execute = args.execute
    emit(f"MIGRATE mode={'EXECUTE' if execute else 'DRY RUN'}")
    emit(f"source-db={args.source_db} dest-db={args.dest_db}")

    try:
        skey = load_key(args.source_key)
        dkey = load_key(args.dest_key)
    except Exception as exc:  # noqa: BLE001
        emit(f"KEY_ERROR {exc}")
        return 1

    sp, dp = skey.get("project_id"), dkey.get("project_id")
    emit(f"source-project={sp} dest-project={dp}")
    src = Client(skey, sp, args.source_db)
    dst = Client(dkey, dp, args.dest_db)

    # Source sanity: list its collections (also proves connectivity).
    cols, err = src.list_collection_ids()
    if cols is None:
        emit(f"SOURCE_DB_UNAVAILABLE {args.source_db}: {err}")
        available = src.list_databases()
        emit(f"source project databases: {available or 'NONE-OR-NO-PERMISSION'}")
        return 1
    emit(f"source root collections: {cols or 'NONE (empty)'}")

    # Dest sanity.
    dcols, derr = dst.list_collection_ids()
    if dcols is None:
        emit(f"DEST_DB_UNAVAILABLE {args.dest_db}: {derr}")
        available = dst.list_databases()
        emit(f"dest project databases: {available or 'NONE-OR-NO-PERMISSION'}")
        return 1
    emit(f"dest root collections: {dcols or 'NONE (empty)'}")

    stats = {"docs": 0, "writes": 0}
    for col in [c.strip() for c in args.collections.split(",") if c.strip()]:
        emit(f"copying collection: {col}")
        try:
            migrate_collection(src, dst, col, execute, stats)
        except Exception as exc:  # noqa: BLE001
            emit(f"COPY_ERROR at {col}: {exc}")
            emit(f"TOTALS docs_scanned={stats['docs']} writes={stats['writes']}")
            return 1

    emit(f"TOTALS docs_scanned={stats['docs']} writes={stats['writes']}")
    emit("MIGRATION_DONE" if execute else "DRY_RUN_DONE")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except SystemExit:
        raise
    except BaseException as exc:  # noqa: BLE001 — surface every failure
        import traceback

        emit(f"CRASH: {type(exc).__name__}: {str(exc)[:400]}")
        tail = traceback.format_exc().strip().splitlines()
        emit("CRASH_TAIL: " + " >> ".join(tail[-5:])[:900])
        sys.exit(1)
