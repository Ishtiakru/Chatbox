#!/usr/bin/env python3
"""Inspect / configure Firebase Authentication providers (Identity Toolkit admin API).

Modes:
  inspect      — enabled sign-in providers, phone test numbers (masked), billing state
  enable-phone — enable Phone provider (+ optional --test-number/--test-code)

Auth: FIREBASE_ADMIN_KEY service account. Single SUMMARY notice.
"""
import argparse
import json
import sys
import urllib.error
import urllib.request

from google.auth.transport.requests import Request as AuthTransportRequest
from google.oauth2 import service_account

SCOPES = ["https://www.googleapis.com/auth/cloud-platform"]
IDT = "https://identitytoolkit.googleapis.com/admin/v2"


def notice(msg: str) -> None:
    clean = str(msg).replace("%", "%25").replace("\r", "%0D").replace("\n", " ;; ")
    print(f"::notice title=SUMMARY::{clean}")


def call(token, method, url, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        return e.code, {"raw": e.read().decode(errors="replace")[:400]}


def sms_desc(cfg: dict) -> str:
    sms = cfg.get("smsRegionConfig", {}) or {}
    if "allowlistOnly" in sms:
        regs = (sms["allowlistOnly"] or {}).get("allowedRegions", [])
        return "ALLOWLIST:" + ",".join(regs)
    if "allowByDefault" in sms:
        regs = (sms["allowByDefault"] or {}).get("disallowedRegions", [])
        return "DEFAULT_ALLOW_except:" + ",".join(regs)
    return "unset"


def summarize(cfg: dict) -> str:
    s = cfg.get("signIn", {})
    phone = s.get("phoneNumber", {})
    tests = phone.get("testPhoneNumbers", {}) or {}
    masked = [n[:6] + "…" + n[-2:] for n in tests]
    return (
        f"email={s.get('email', {}).get('enabled')} "
        f"phone={phone.get('enabled')} test_numbers={masked} "
        f"anonymous={s.get('anonymous', {}).get('enabled')} "
        f"authorizedDomains={len(cfg.get('authorizedDomains', []))} "
        f"smsRegion={sms_desc(cfg)}"
    )


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--key", required=True)
    ap.add_argument("--mode", choices=["inspect", "enable-phone", "sha", "enable-password", "admin-password"], required=True)
    ap.add_argument("--sha1", default="")
    ap.add_argument("--sha256", default="")
    ap.add_argument("--package", default="com.aistudio.chatbox.kxmpzq")
    ap.add_argument("--project", default="gen-lang-client-0251556445")
    ap.add_argument("--test-number", default="")
    ap.add_argument("--test-code", default="")
    ap.add_argument("--secret-file", default="/tmp/secret.txt")
    a = ap.parse_args()

    creds = service_account.Credentials.from_service_account_file(a.key, scopes=SCOPES)
    creds.refresh(AuthTransportRequest())
    tok = creds.token
    cfg_url = f"{IDT}/projects/{a.project}/config"

    if a.mode == "admin-password":
        # Attach "mobile number + password" login to the existing admin owner account
        # (uid from sparkAdmin/current) so the same account, data and admin panel open
        # with the number. Secret file: line 1 number, line 2 password (decrypted on
        # the runner only; never logged).
        lines = open(a.secret_file, encoding="utf-8").read().splitlines()
        number, password = lines[0].strip(), lines[1]
        digits = "".join(ch for ch in number if ch.isdigit())
        if digits.startswith("01") and len(digits) == 11:
            digits = "88" + digits
        if not (digits.startswith("880") and len(digits) == 13) or len(password) < 6:
            notice("admin-password: bad number or password too short"); sys.exit(1)
        email = f"{digits}@phone.chatbox.app"
        db = "ai-studio-android-chatbox-bc1e3568-f7e2-4c12-b2b9-9c365ad43fb9"
        st, adm = call(tok, "GET", f"https://firestore.googleapis.com/v1/projects/{a.project}/databases/{db}/documents/sparkAdmin/current")
        uid = adm.get("fields", {}).get("uid", {}).get("stringValue", "") if st == 200 else ""
        if not uid:
            notice(f"admin-password: sparkAdmin/current not readable HTTP {st}"); sys.exit(1)
        acc = f"https://identitytoolkit.googleapis.com/v1/projects/{a.project}/accounts"
        st, other = call(tok, "POST", acc + ":lookup", {"email": [email]})
        clash = [u for u in other.get("users", []) if u.get("localId") != uid] if st == 200 else []
        if clash:
            u = clash[0]
            notice(f"admin-password: {email} already used by uid={u.get('localId')} created={u.get('createdAt')} "
                   f"lastLogin={u.get('lastLoginAt')} providers={[p.get('providerId') for p in u.get('providerUserInfo', [])]}")
            sys.exit(1)
        st, out = call(tok, "POST", acc + ":update", {"localId": uid, "email": email, "password": password})
        if st != 200:
            notice(f"admin-password update HTTP {st}: {out}"); sys.exit(1)
        st, lu = call(tok, "POST", acc + ":lookup", {"localId": [uid]})
        u = (lu.get("users") or [{}])[0]
        provs = [(p.get("providerId"), p.get("email", "")) for p in u.get("providerUserInfo", [])]
        notice(f"admin-password OK uid={uid} email={u.get('email')} has_password={bool(u.get('passwordHash'))} providers={provs}")
        return

    if a.mode == "sha":
        fb = "https://firebase.googleapis.com/v1beta1"
        st, apps = call(tok, "GET", f"{fb}/projects/{a.project}/androidApps?pageSize=100")
        if st != 200:
            notice(f"androidApps HTTP {st}: {apps}")
            sys.exit(1)
        app = next((x for x in apps.get("apps", []) if x.get("packageName") == a.package), None)
        if not app:
            notice(f"no android app for {a.package}; apps={[x.get('packageName') for x in apps.get('apps', [])]}")
            sys.exit(1)
        name = app["name"]
        st, shas = call(tok, "GET", f"{fb}/{name}/sha")
        have = {c.get("shaHash", "").lower() for c in shas.get("certificates", [])}
        added = []
        for h, t in ((a.sha1, "SHA_1"), (a.sha256, "SHA_256")):
            h = h.replace(":", "").lower()
            if h and h not in have:
                st2, out = call(tok, "POST", f"{fb}/{name}/sha", {"shaHash": h, "certType": t})
                added.append(f"{t}:{'OK' if st2 == 200 else f'HTTP{st2} {out}'}")
        notice(f"sha app={app.get('appId')} registered={len(have)} added={added or 'none'} "
               f"release_sha1_present={a.sha1.replace(':','').lower() in have} "
               f"release_sha256_present={a.sha256.replace(':','').lower() in have}")
        return

    st, cfg = call(tok, "GET", cfg_url)
    if st != 200:
        notice(f"config GET HTTP {st}: {cfg}")
        sys.exit(1)

    st_b, bill = call(tok, "GET", f"https://cloudbilling.googleapis.com/v1/projects/{a.project}/billingInfo")
    billing = bill.get("billingEnabled") if st_b == 200 else f"unknown(HTTP {st_b})"

    if a.mode == "inspect":
        st_g, g = call(tok, "GET", f"{IDT}/projects/{a.project}/defaultSupportedIdpConfigs/google.com")
        gdesc = (f"google_enabled={g.get('enabled')} google_client={str(g.get('clientId',''))[:28]}"
                 if st_g == 200 else f"google=HTTP{st_g}")
        fb = "https://firebase.googleapis.com/v1beta1"
        st_a, apps = call(tok, "GET", f"{fb}/projects/{a.project}/androidApps?pageSize=100")
        shadesc = ""
        app = next((x for x in apps.get("apps", []) if x.get("packageName") == a.package), None) if st_a == 200 else None
        if app:
            _, shas = call(tok, "GET", f"{fb}/{app['name']}/sha")
            have = {c.get("shaHash", "").lower() for c in shas.get("certificates", [])}
            shadesc = (f" shas={len(have)} release_sha1_present={a.sha1.replace(':','').lower() in have}"
                       f" release_sha256_present={a.sha256.replace(':','').lower() in have}")
        notice(f"inspect {summarize(cfg)} billingEnabled={billing} {gdesc}{shadesc}")
        return

    if a.mode == "enable-password":
        # Mobile number + password login: passwords are verified by Firebase
        # (email/password provider with a per-number synthetic address).
        body = {"signIn": {"email": {"enabled": True, "passwordRequired": True}}}
        st, out = call(tok, "PATCH", cfg_url + "?updateMask=signIn.email.enabled,signIn.email.passwordRequired", body)
        if st != 200:
            notice(f"enable-password HTTP {st}: {out}")
            sys.exit(1)
        notice(f"enable-password OK {summarize(out)}")
        return

    phone = {"enabled": True}
    existing_tests = cfg.get("signIn", {}).get("phoneNumber", {}).get("testPhoneNumbers", {}) or {}
    if a.test_number and a.test_code:
        existing_tests = dict(existing_tests)
        existing_tests[a.test_number] = a.test_code
    if existing_tests:
        phone["testPhoneNumbers"] = existing_tests
    body = {"signIn": {"phoneNumber": phone}}
    mask = "signIn.phoneNumber"
    sms = cfg.get("smsRegionConfig", {}) or {}
    if "allowlistOnly" in sms:
        regs = list((sms["allowlistOnly"] or {}).get("allowedRegions", []))
        if "BD" not in regs:
            regs.append("BD")
            body["smsRegionConfig"] = {"allowlistOnly": {"allowedRegions": regs}}
            mask += ",smsRegionConfig"
    elif "allowByDefault" in sms:
        dis = list((sms["allowByDefault"] or {}).get("disallowedRegions", []))
        if "BD" in dis:
            dis.remove("BD")
            body["smsRegionConfig"] = {"allowByDefault": {"disallowedRegions": dis}}
            mask += ",smsRegionConfig"
    st, out = call(tok, "PATCH", cfg_url + "?updateMask=" + mask, body)
    if st != 200:
        notice(f"enable-phone HTTP {st}: {out}")
        sys.exit(1)
    notice(f"enable-phone OK {summarize(out)} billingEnabled={billing}")


if __name__ == "__main__":
    main()
