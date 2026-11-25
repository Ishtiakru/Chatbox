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
        f"smsRegion={json.dumps(cfg.get('smsRegionConfig', {}))[:120]}"
    )


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--key", required=True)
    ap.add_argument("--mode", choices=["inspect", "enable-phone"], required=True)
    ap.add_argument("--project", default="gen-lang-client-0251556445")
    ap.add_argument("--test-number", default="")
    ap.add_argument("--test-code", default="")
    a = ap.parse_args()

    creds = service_account.Credentials.from_service_account_file(a.key, scopes=SCOPES)
    creds.refresh(AuthTransportRequest())
    tok = creds.token
    cfg_url = f"{IDT}/projects/{a.project}/config"

    st, cfg = call(tok, "GET", cfg_url)
    if st != 200:
        notice(f"config GET HTTP {st}: {cfg}")
        sys.exit(1)

    st_b, bill = call(tok, "GET", f"https://cloudbilling.googleapis.com/v1/projects/{a.project}/billingInfo")
    billing = bill.get("billingEnabled") if st_b == 200 else f"unknown(HTTP {st_b})"

    if a.mode == "inspect":
        notice(f"inspect {summarize(cfg)} billingEnabled={billing}")
        return

    phone = {"enabled": True}
    existing_tests = cfg.get("signIn", {}).get("phoneNumber", {}).get("testPhoneNumbers", {}) or {}
    if a.test_number and a.test_code:
        existing_tests = dict(existing_tests)
        existing_tests[a.test_number] = a.test_code
    if existing_tests:
        phone["testPhoneNumbers"] = existing_tests
    body = {"signIn": {"phoneNumber": phone}}
    st, out = call(tok, "PATCH", cfg_url + "?updateMask=signIn.phoneNumber", body)
    if st != 200:
        notice(f"enable-phone HTTP {st}: {out}")
        sys.exit(1)
    notice(f"enable-phone OK {summarize(out)} billingEnabled={billing}")


if __name__ == "__main__":
    main()
