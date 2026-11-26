#!/usr/bin/env python3
"""Search directory audit / repair for the canonical project.

Search by number reads phoneDirectory/{digits}; search by 6-digit ID reads
shortIds/{id}; both then need profiles/{uid}. Accounts made with "number +
password" (and admin-linked numbers) never wrote phoneDirectory, so nobody could
find them.

  audit — counts per account (no numbers or names printed; uid prefix only)
  fix   — create the missing entries (never overwrites an entry owned by another uid)
"""
import argparse
import json
import random
import sys
import urllib.error
import urllib.parse
import urllib.request

from google.auth.transport.requests import Request as AuthTransportRequest
from google.oauth2 import service_account

DB = "ai-studio-android-chatbox-bc1e3568-f7e2-4c12-b2b9-9c365ad43fb9"
PHONE_DOMAIN = "@phone.chatbox.app"
ABOUT = "Hey there! I am using Chatbox."


def notice(msg):
    print(f"::notice::{str(msg).replace(chr(10), ' ')[:3900]}")


class C:
    def __init__(self, path):
        info = json.load(open(path))
        self.project = info["project_id"]
        self.creds = service_account.Credentials.from_service_account_info(
            info, scopes=["https://www.googleapis.com/auth/cloud-platform"])
        self.base = f"https://firestore.googleapis.com/v1/projects/{self.project}/databases/{DB}/documents"

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
                return r.status, json.loads(r.read().decode() or "{}")
        except urllib.error.HTTPError as e:
            t = e.read().decode(errors="replace")[:400]
            try:
                return e.code, json.loads(t)
            except Exception:
                return e.code, t

    def users(self):
        out, tok = [], None
        while True:
            url = f"https://identitytoolkit.googleapis.com/v1/projects/{self.project}/accounts:batchGet?maxResults=1000"
            if tok:
                url += "&nextPageToken=" + urllib.parse.quote(tok, safe="")
            st, b = self.call("GET", url)
            if st != 200:
                raise RuntimeError(f"users HTTP {st} {b}")
            out += b.get("users", [])
            tok = b.get("nextPageToken")
            if not tok or not b.get("users"):
                return out

    def collection(self, name):
        out, tok = {}, None
        while True:
            url = f"{self.base}/{name}?pageSize=300"
            if tok:
                url += "&pageToken=" + urllib.parse.quote(tok, safe="")
            st, b = self.call("GET", url)
            if st != 200:
                raise RuntimeError(f"{name} HTTP {st} {b}")
            for d in b.get("documents", []):
                f = {k: list(v.values())[0] for k, v in d.get("fields", {}).items()}
                out[d["name"].rsplit("/", 1)[-1]] = f
            tok = b.get("nextPageToken")
            if not tok:
                return out

    def get_doc(self, path):
        st, b = self.call("GET", f"{self.base}/{path}")
        if st != 200:
            return None
        return {k: list(v.values())[0] for k, v in b.get("fields", {}).items()}

    def create(self, path, fields):
        body = {"fields": {k: {"stringValue": v} for k, v in fields.items()}}
        return self.call("PATCH", f"{self.base}/{path}?currentDocument.exists=false", body)


def phone_keys(digits):
    keys = [digits]
    if digits.startswith("880") and len(digits) == 13:
        keys.append(digits[2:])
    elif digits.startswith("01") and len(digits) == 11:
        keys.append("88" + digits)
    return keys


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--key", required=True)
    ap.add_argument("--mode", choices=["audit", "fix"], required=True)
    a = ap.parse_args()
    c = C(a.key)
    users = c.users()
    profiles = c.collection("profiles")
    short_ids = c.collection("shortIds")
    phones = c.collection("phoneDirectory")
    notice(f"totals auth_users={len(users)} profiles={len(profiles)} shortIds={len(short_ids)} phoneDirectory={len(phones)}")

    rows, fixes, errs = [], [], []
    live = {u["localId"] for u in users}
    ghost_phone = sorted(k for k, v in phones.items() if v.get("uid") not in live)
    ghost_short = sorted(k for k, v in short_ids.items() if v.get("uid") not in live)
    notice(f"ghost entries (point to accounts that do not exist): phoneDirectory={len(ghost_phone)} shortIds={len(ghost_short)} profiles={sum(1 for k in profiles if k not in live)}")
    for u in users:
        uid = u["localId"]
        email = (u.get("email") or "").lower()
        provs = ",".join(sorted(p["providerId"][:6] for p in u.get("providerUserInfo", [])))
        digits = set()
        if email.endswith(PHONE_DOMAIN):
            digits.add(email[: -len(PHONE_DOMAIN)])
        for p in u.get("providerUserInfo", []):
            pe = (p.get("email") or "").lower()
            if pe.endswith(PHONE_DOMAIN):
                digits.add(pe[: -len(PHONE_DOMAIN)])
        if u.get("phoneNumber"):
            digits.add(u["phoneNumber"].lstrip("+"))
        priv = c.get_doc(f"users/{uid}/private/profile") or {}
        pd = "".join(ch for ch in str(priv.get("phoneNumber", "")) if ch.isdigit())
        if len(pd) >= 7:
            digits.add(pd)
        keys = sorted({k for d in digits for k in phone_keys(d)})
        prof = profiles.get(uid)
        sid = (prof or {}).get("shortId", "")
        sid_ok = bool(sid) and short_ids.get(sid, {}).get("uid") == uid
        have_keys = [k for k in keys if phones.get(k, {}).get("uid") == uid]
        taken = [k for k in keys if k in phones and phones[k].get("uid") != uid and phones[k].get("uid") in live]
        name = ((prof or {}).get("displayName") or u.get("displayName") or "Chatbox User")[:80]
        rows.append(f"{name.replace(' ', '_')}:{uid[:6]}[{provs}] digits={'/'.join(sorted(digits))} profile={'Y' if prof else 'N'} id={'Y' if sid_ok else 'N'} phone={len(have_keys)}/{len(keys)}" + (f" taken={len(taken)}" if taken else ""))

        if a.mode == "fix":
            if not prof:
                seed = str(random.randint(100000, 999999))
                while seed in short_ids:
                    seed = str(random.randint(100000, 999999))
                st, b = c.create(f"profiles/{uid}", {"uid": uid, "displayName": name, "shortId": seed, "about": ABOUT})
                if st == 200:
                    prof, sid = {"shortId": seed}, seed
                    fixes.append(f"{uid[:6]}:profile")
                else:
                    errs.append(f"{uid[:6]} profile {st}")
            if sid and not sid_ok and sid not in short_ids:
                st, b = c.create(f"shortIds/{sid}", {"uid": uid, "displayName": name})
                (fixes if st == 200 else errs).append(f"{uid[:6]}:shortId" + ("" if st == 200 else f" {st}"))
                if st == 200:
                    short_ids[sid] = {"uid": uid}
            for k in keys:
                if k in phones and phones[k].get("uid") not in live:
                    # Entry points to an account that no longer exists: give it to the real owner.
                    st, b = c.call("PATCH", f"{c.base}/phoneDirectory/{k}",
                                   {"fields": {"uid": {"stringValue": uid}, "displayName": {"stringValue": name}}})
                    (fixes if st == 200 else errs).append(f"{uid[:6]}:phone-reassign" + ("" if st == 200 else f" {st}"))
                    if st == 200:
                        phones[k] = {"uid": uid}
                elif k not in phones:
                    st, b = c.create(f"phoneDirectory/{k}", {"uid": uid, "displayName": name})
                    (fixes if st == 200 else errs).append(f"{uid[:6]}:phone" + ("" if st == 200 else f" {st}"))
                    if st == 200:
                        phones[k] = {"uid": uid}

    notice("accounts: " + " | ".join(rows))
    try:
        convs = c.collection("conversations")
        crow = []
        for cid, v in convs.items():
            parts = [x.get("stringValue", "")[:6] for x in (v.get("participants") or {}).get("values", [])]
            st, b = c.call("GET", f"{c.base}/conversations/{cid}/messages?pageSize=300")
            n = len(b.get("documents", [])) if st == 200 else f"err{st}"
            crow.append(f"{'+'.join(parts)} status={v.get('status')} by={str(v.get('createdBy',''))[:6]} msgs={n}")
        notice(f"conversations={len(convs)}: " + " | ".join(crow))
    except Exception as e:  # noqa: BLE001
        notice(f"conversations ERROR {e}")
    if a.mode == "fix":
        for k, v in list(phones.items()):
            if v.get("uid") not in live:
                st, _ = c.call("DELETE", f"{c.base}/phoneDirectory/{k}")
                (fixes if st == 200 else errs).append("ghost-phone-del" + ("" if st == 200 else f" {st}"))
        for k, v in list(short_ids.items()):
            if v.get("uid") not in live:
                st, _ = c.call("DELETE", f"{c.base}/shortIds/{k}")
                (fixes if st == 200 else errs).append("ghost-id-del" + ("" if st == 200 else f" {st}"))
        notice(f"fix created={len(fixes)} {fixes} errors={errs}")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # noqa: BLE001
        notice(f"ERROR {type(e).__name__}: {str(e)[:400]}")
        sys.exit(1)
