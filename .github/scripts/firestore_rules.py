#!/usr/bin/env python3
"""Inspect / deploy Firestore security rules and read Saira diagnostics.

Modes:
  inspect  — fetch the ruleset currently released for the app's database and
             compare it to the repo's firestore.rules (sha, line diff, and
             whether settings/aiKeyPool is readable).
  deploy   — create a ruleset from firestore.rules and point the database's
             release at it.
  diag     — list aiDiagnostics/* (last Saira failure per device).

Auth: FIREBASE_ADMIN_KEY service account. Output is a single SUMMARY notice.
"""
import argparse
import difflib
import hashlib
import json
import sys
import urllib.error
import urllib.request

from google.auth.transport.requests import Request as AuthTransportRequest
from google.oauth2 import service_account

SCOPES = ["https://www.googleapis.com/auth/cloud-platform"]
RULES_API = "https://firebaserules.googleapis.com/v1"
FS_API = "https://firestore.googleapis.com/v1"


def notice(msg: str) -> None:
    clean = str(msg).replace("%", "%25").replace("\r", "%0D").replace("\n", " ;; ")
    print(f"::notice title=SUMMARY::{clean}")
    print(msg)


def fail(msg: str) -> None:
    clean = str(msg).replace("%", "%25").replace("\r", "%0D").replace("\n", " ;; ")
    print(f"::error title=SUMMARY::{clean}")
    sys.exit(1)


def call(token: str, method: str, url: str, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, json.loads(resp.read() or b"{}")
    except urllib.error.HTTPError as e:
        return e.code, {"raw": e.read().decode(errors="replace")[:500]}


def sha(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()[:12]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--key", required=True)
    ap.add_argument("--mode", choices=["inspect", "deploy", "diag"], required=True)
    ap.add_argument("--project", default="gen-lang-client-0251556445")
    ap.add_argument("--db", default="ai-studio-android-chatbox-bc1e3568-f7e2-4c12-b2b9-9c365ad43fb9")
    ap.add_argument("--rules", default="firestore.rules")
    args = ap.parse_args()

    creds = service_account.Credentials.from_service_account_file(args.key, scopes=SCOPES)
    creds.refresh(AuthTransportRequest())
    token = creds.token
    project = args.project
    release_name = f"projects/{project}/releases/cloud.firestore/{args.db}"

    if args.mode == "diag":
        url = f"{FS_API}/projects/{project}/databases/{args.db}/documents/aiDiagnostics?pageSize=50"
        st, body = call(token, "GET", url)
        if st != 200:
            fail(f"diag list HTTP {st}: {body}")
        docs = body.get("documents", [])
        lines = [f"diag docs={len(docs)}"]
        for d in sorted(docs, key=lambda x: x.get("updateTime", ""), reverse=True)[:8]:
            f = d.get("fields", {})
            g = lambda k: next(iter(f.get(k, {}).values()), "")
            uid = d["name"].rsplit("/", 1)[-1]
            lines.append(
                f"{uid[:6]}… v={g('appVersion')} stage={g('stage')} keys={g('keyCount')} "
                f"at={g('updatedAt')} err={g('errorClass')} msg={str(g('message'))[:260]}"
            )
        notice("\n".join(lines))
        return

    repo_src = open(args.rules, encoding="utf-8").read()

    st, rel = call(token, "GET", f"{RULES_API}/{release_name}")
    deployed_src = ""
    deployed_ruleset = ""
    if st == 200:
        deployed_ruleset = rel.get("rulesetName", "")
        st2, rs = call(token, "GET", f"{RULES_API}/{deployed_ruleset}")
        if st2 == 200:
            files = rs.get("source", {}).get("files", [])
            deployed_src = files[0].get("content", "") if files else ""
        else:
            fail(f"ruleset GET HTTP {st2}: {rs}")
    elif st != 404:
        fail(f"release GET HTTP {st}: {rel}")

    if args.mode == "inspect":
        diff = list(difflib.unified_diff(
            deployed_src.splitlines(), repo_src.splitlines(), lineterm="", n=0))
        changed = [l for l in diff if l[:1] in "+-" and not l.startswith(("+++", "---"))]
        notice(
            f"inspect release={'present' if st == 200 else 'MISSING'} ruleset={deployed_ruleset.rsplit('/', 1)[-1]} "
            f"deployed_sha={sha(deployed_src) if deployed_src else '-'} deployed_lines={len(deployed_src.splitlines())} "
            f"repo_sha={sha(repo_src)} repo_lines={len(repo_src.splitlines())} "
            f"deployed_has_aiKeyPool={'aiKeyPool' in deployed_src} changed_lines={len(changed)}\n"
            + "\n".join(changed[:40])
        )
        return

    # deploy
    st, rs = call(token, "POST", f"{RULES_API}/projects/{project}/rulesets", {
        "source": {"files": [{"name": "firestore.rules", "content": repo_src}]}
    })
    if st != 200:
        fail(f"ruleset create HTTP {st}: {rs}")
    new_ruleset = rs["name"]
    body = {"release": {"name": release_name, "rulesetName": new_ruleset}}
    if deployed_ruleset:
        st, out = call(token, "PATCH", f"{RULES_API}/{release_name}", body)
    else:
        st, out = call(token, "POST", f"{RULES_API}/projects/{project}/releases", body["release"])
    if st != 200:
        fail(f"release update HTTP {st}: {out}")
    notice(
        f"deploy OK release={release_name.rsplit('/', 1)[-1]} "
        f"old={deployed_ruleset.rsplit('/', 1)[-1] or '-'} new={new_ruleset.rsplit('/', 1)[-1]} "
        f"repo_sha={sha(repo_src)}"
    )


if __name__ == "__main__":
    main()
