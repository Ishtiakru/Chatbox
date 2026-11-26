#!/usr/bin/env python3
"""Audit / move / delete the OLD duplicate Firebase project (gen-lang-client-0532609883).

modes:
  audit   — what the old project still has (Firestore per database + root collection,
            Auth users, Storage objects) vs. the canonical project, and whether the
            old key may delete the project. Read-only.
  copy    — copy Firestore documents that are MISSING in the canonical database
            (never overwrites: create-only precondition) + Storage objects missing in
            the canonical bucket.
  delete  — shut down (delete) the old project. 30-day recovery window at Google.
Never prints secrets or document contents; only counts and short ids.
"""
import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

from google.auth.transport.requests import Request as AuthTransportRequest
from google.oauth2 import service_account

SCOPES = ["https://www.googleapis.com/auth/cloud-platform"]
DEST_DB = "ai-studio-android-chatbox-bc1e3568-f7e2-4c12-b2b9-9c365ad43fb9"
CAP = 20000


def notice(msg):
    clean = str(msg).replace("%", "%25").replace("\r", " ").replace("\n", " ")
    print(f"::notice::{clean[:3900]}")


class G:
    def __init__(self, path):
        self.info = json.load(open(path))
        self.project = self.info["project_id"]
        self.creds = service_account.Credentials.from_service_account_info(self.info, scopes=SCOPES)

    def call(self, method, url, body=None):
        if not self.creds.valid:
            self.creds.refresh(AuthTransportRequest())
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Authorization", f"Bearer {self.creds.token}")
        if data is not None:
            req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                raw = r.read()
                try:
                    return r.status, json.loads(raw.decode() or "{}")
                except Exception:
                    return r.status, raw
        except urllib.error.HTTPError as e:
            txt = e.read().decode(errors="replace")[:500]
            try:
                return e.code, json.loads(txt)
            except Exception:
                return e.code, txt
        except Exception as e:  # noqa: BLE001
            return 0, f"{type(e).__name__}: {e}"[:300]

    # ---- Firestore ----
    def fs(self, db):
        return f"https://firestore.googleapis.com/v1/projects/{self.project}/databases/{urllib.parse.quote(db, safe='')}/documents"

    def databases(self):
        st, b = self.call("GET", f"https://firestore.googleapis.com/v1/projects/{self.project}/databases")
        return [d["name"].split("/databases/")[-1] for d in b.get("databases", [])] if st == 200 else []

    def collection_ids(self, db, doc_path=""):
        url = f"{self.fs(db)}/{doc_path}:listCollectionIds" if doc_path else f"{self.fs(db)}:listCollectionIds"
        out, token = [], None
        while True:
            body = {"pageSize": 300}
            if token:
                body["pageToken"] = token
            st, b = self.call("POST", url, body)
            if st != 200:
                return out
            out += b.get("collectionIds", [])
            token = b.get("nextPageToken")
            if not token:
                return out

    def docs(self, db, col_path, mask_only=False):
        token = None
        n = 0
        while True:
            url = f"{self.fs(db)}/{urllib.parse.quote(col_path, safe='/')}?pageSize=300&showMissing=true"
            if mask_only:
                url += "&mask.fieldPaths=zzNoSuchField"
            if token:
                url += f"&pageToken={urllib.parse.quote(token, safe='')}"
            st, b = self.call("GET", url)
            if st != 200:
                raise RuntimeError(f"list {col_path}: HTTP {st} {str(b)[:200]}")
            for d in b.get("documents", []):
                n += 1
                if n > CAP:
                    return
                yield d
            token = b.get("nextPageToken")
            if not token:
                return

    def create_doc(self, db, rel, fields):
        url = f"{self.fs(db)}/{urllib.parse.quote(rel, safe='/')}?currentDocument.exists=false"
        return self.call("PATCH", url, {"fields": fields})

    # ---- Auth ----
    def auth_users(self):
        users, token = [], None
        while True:
            url = f"https://identitytoolkit.googleapis.com/v1/projects/{self.project}/accounts:batchGet?maxResults=1000"
            if token:
                url += f"&nextPageToken={urllib.parse.quote(token, safe='')}"
            st, b = self.call("GET", url)
            if st != 200:
                return None, f"HTTP {st} {str(b)[:200]}"
            users += b.get("users", [])
            token = b.get("nextPageToken")
            if not token or not b.get("users"):
                return users, None

    # ---- Storage ----
    def buckets(self):
        names = [f"{self.project}.firebasestorage.app", f"{self.project}.appspot.com"]
        return names

    def objects(self, bucket):
        out, token = [], None
        while True:
            url = f"https://storage.googleapis.com/storage/v1/b/{bucket}/o?maxResults=1000&fields=items(name,size),nextPageToken"
            if token:
                url += f"&pageToken={urllib.parse.quote(token, safe='')}"
            st, b = self.call("GET", url)
            if st != 200:
                return None, st
            out += b.get("items", [])
            token = b.get("nextPageToken")
            if not token:
                return out, 200


def walk(g, db, col, acc, depth=0):
    """Count docs per collection-group name; collect relative doc paths."""
    for d in g.docs(db, col, mask_only=True):
        rel = d["name"].split("/documents/", 1)[-1]
        group = "/".join(p for i, p in enumerate(rel.split("/")) if i % 2 == 0)
        acc.setdefault(group, set()).add(rel)
        if depth < 6:
            for sub in g.collection_ids(db, rel):
                walk(g, db, f"{rel}/{sub}", acc, depth + 1)
    # documents that exist only as parents of subcollections ("missing" docs)
    return acc


def audit_fs(old, new, db_old, db_new):
    cols_old = old.collection_ids(db_old)
    paths_old = {}
    for c in cols_old:
        walk(old, db_old, c, paths_old)
    cols_new = new.collection_ids(db_new)
    paths_new = {}
    for c in cols_old:
        if c in cols_new:
            walk(new, db_new, c, paths_new)
    summary = {}
    for group, s in sorted(paths_old.items()):
        missing = s - paths_new.get(group, set())
        summary[group] = f"{len(s)}/{len(missing)}"
    return cols_old, cols_new, summary, paths_old, paths_new


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--old-key", required=True)
    ap.add_argument("--new-key", required=True)
    ap.add_argument("--mode", choices=["audit", "copy", "delete", "keys"], required=True)
    a = ap.parse_args()
    old, new = G(a.old_key), G(a.new_key)
    if old.project == new.project:
        notice("ABORT old and new keys are the same project"); sys.exit(1)
    notice(f"old={old.project} new={new.project}")

    if a.mode == "keys":
        # Are any of Saira's pooled Gemini keys created inside the old project?
        st, b = new.call("GET", f"{new.fs(DEST_DB)}/settings/aiKeyPool")
        pool = set()
        if st == 200:
            for v in b.get("fields", {}).get("keys", {}).get("arrayValue", {}).get("values", []):
                pool.add(v.get("stringValue", ""))
        import re as _re
        by_proj = {}
        for key in pool:
            url = ("https://aiplatform.googleapis.com/v1/publishers/google/models/gemini-2.5-flash-lite:generateContent?key="
                   + urllib.parse.quote(key, safe=""))
            body = json.dumps({"contents": [{"role": "user", "parts": [{"text": "hi"}]}]}).encode()
            try:
                with urllib.request.urlopen(urllib.request.Request(url, data=body, headers={"Content-Type": "application/json"}), timeout=30) as r:
                    txt = r.read().decode(errors="replace")
            except urllib.error.HTTPError as e:
                txt = e.read().decode(errors="replace")
            except Exception as e:  # noqa: BLE001
                txt = str(e)
            m = _re.search(r"projects/(\d{6,})", txt) or _re.search(r"project (\d{6,})", txt)
            proj = m.group(1) if m else "unknown"
            if proj == "unknown" and not by_proj.get("_sampled"):
                by_proj["_sampled"] = 1
                notice("SAMPLE " + txt.replace(key, "<KEY>")[:600])
            by_proj[proj] = by_proj.get(proj, 0) + 1
        old_num = "838258163823"
        notice(f"KEYS pool={len(pool)} keys_in_OLD_project={by_proj.get(old_num, 0)} projects={len(by_proj)} unknown={by_proj.get('unknown', 0)}")
        # Second probe: Gemini API itself, an unsupported method reveals the consumer project.
        return
        keys = kb.get("keys", [])
        in_pool = unreadable = 0
        for k in keys:
            s2, ks = old.call("GET", f"https://apikeys.googleapis.com/v2/{k['name']}/keyString")
            if s2 != 200:
                unreadable += 1
            elif ks.get("keyString") in pool:
                in_pool += 1
        notice(f"KEYS pool={len(pool)} old_project_keys={len(keys)} of_which_in_saira_pool={in_pool} unreadable={unreadable}")
        return

    if a.mode == "delete":
        if old.project != "gen-lang-client-0532609883":
            notice(f"ABORT refusing to delete unexpected project {old.project}"); sys.exit(1)
        st, b = old.call("DELETE", f"https://cloudresourcemanager.googleapis.com/v1/projects/{old.project}")
        notice(f"delete project {old.project}: HTTP {st} {str(b)[:300]}")
        st, b = old.call("GET", f"https://cloudresourcemanager.googleapis.com/v1/projects/{old.project}")
        notice(f"project state after: HTTP {st} lifecycle={b.get('lifecycleState') if isinstance(b, dict) else b}")
        return

    # ---- Firestore ----
    dbs = old.databases()
    notice(f"old databases={[d[-12:] for d in dbs]} new databases={[d[-12:] for d in new.databases()]}")
    total_missing = {}
    for db in dbs:
        try:
            fs_one(a, old, new, db)
        except Exception as e:  # noqa: BLE001
            notice(f"FS {db[-12:]} ERROR {type(e).__name__}: {str(e)[:300]}")
    try:
        auth_storage(a, old, new)
    except Exception as e:  # noqa: BLE001
        notice(f"AUTH/STORAGE ERROR {type(e).__name__}: {str(e)[:300]}")


def fs_one(a, old, new, db):
    if True:
        cols_old, cols_new, summary, p_old, p_new = audit_fs(old, new, db, DEST_DB)
        notice(f"FS old_db={db[-12:]} root_cols={cols_old} group=total/missing_in_new: {' '.join(f'{k}={v}' for k, v in summary.items()) or 'EMPTY'}")
        if a.mode == "copy":
            done = fail = exists = 0
            errs = []
            for group, s in p_old.items():
                for rel in sorted(s - p_new.get(group, set())):
                    st, b = old.call("GET", f"{old.fs(db)}/{urllib.parse.quote(rel, safe='/')}")
                    if st == 404:
                        continue  # parent-only path, no document body
                    if st != 200:
                        fail += 1; errs.append(f"get {rel[:40]} {st}"); continue
                    st2, b2 = new.create_doc(DEST_DB, rel, b.get("fields", {}))
                    if st2 == 200:
                        done += 1
                    elif st2 in (400, 409) and "exist" in str(b2).lower():
                        exists += 1
                    else:
                        fail += 1; errs.append(f"put {rel[:40]} {st2} {str(b2)[:80]}")
            notice(f"COPY {db[-12:]} created={done} already={exists} failed={fail} errs={errs[:5]}")



def auth_storage(a, old, new):
    # ---- Auth ----
    ou, oerr = old.auth_users()
    nu, nerr = new.auth_users()
    if ou is None or nu is None:
        notice(f"AUTH old={oerr or len(ou)} new={nerr or len(nu)}")
    else:
        new_ids = {u["localId"] for u in nu}
        new_emails = {u.get("email", "").lower() for u in nu if u.get("email")}
        new_phones = {u.get("phoneNumber") for u in nu if u.get("phoneNumber")}
        prov = {}
        for u in ou:
            for p in u.get("providerUserInfo", []) or [{"providerId": "none"}]:
                prov[p["providerId"]] = prov.get(p["providerId"], 0) + 1
        same_uid = sum(1 for u in ou if u["localId"] in new_ids)
        same_ident = sum(1 for u in ou if (u.get("email", "").lower() in new_emails) or (u.get("phoneNumber") in new_phones))
        notice(f"AUTH old_users={len(ou)} providers={' '.join(f'{k}:{v}' for k, v in prov.items()) or 'none'} same_uid_in_new={same_uid} same_email_or_phone_in_new={same_ident} new_users={len(nu)}")

    # ---- Storage ----
    for ob in old.buckets():
        objs, st = old.objects(ob)
        if objs is None:
            notice(f"STORAGE old {ob}: HTTP {st}")
            continue
        nb = f"{new.project}.firebasestorage.app"
        nobjs, nst = new.objects(nb)
        have = {o["name"] for o in (nobjs or [])}
        missing = [o for o in objs if o["name"] not in have]
        size = sum(int(o.get("size", 0)) for o in missing)
        notice(f"STORAGE old {ob}: objects={len(objs)} missing_in_new={len(missing)} bytes={size} new_bucket_status={nst}")
        if a.mode == "copy" and missing and nobjs is not None:
            ok = bad = 0
            for o in missing:
                src = urllib.parse.quote(o["name"], safe="")
                st, b = old.call("GET", f"https://storage.googleapis.com/storage/v1/b/{ob}/o/{src}?alt=media")
                if st != 200 or not isinstance(b, (bytes, dict)):
                    bad += 1; continue
                data = b if isinstance(b, bytes) else json.dumps(b).encode()
                if not new.creds.valid:
                    new.creds.refresh(AuthTransportRequest())
                req = urllib.request.Request(
                    f"https://storage.googleapis.com/upload/storage/v1/b/{nb}/o?uploadType=media&name={src}&ifGenerationMatch=0",
                    data=data, method="POST")
                req.add_header("Authorization", f"Bearer {new.creds.token}")
                req.add_header("Content-Type", "application/octet-stream")
                try:
                    with urllib.request.urlopen(req, timeout=120):
                        ok += 1
                except Exception:  # noqa: BLE001
                    bad += 1
            notice(f"STORAGE COPY {ob}: copied={ok} failed={bad}")

    # ---- Can the old key delete the project? ----
    st, b = old.call("POST", f"https://cloudresourcemanager.googleapis.com/v1/projects/{old.project}:testIamPermissions",
                     {"permissions": ["resourcemanager.projects.delete", "resourcemanager.projects.get"]})
    notice(f"PERMS old key on {old.project}: HTTP {st} {b.get('permissions') if isinstance(b, dict) else b}")


if __name__ == "__main__":
    main()
