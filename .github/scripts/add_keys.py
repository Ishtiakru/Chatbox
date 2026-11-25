#!/usr/bin/env python3
"""Test new Gemini keys and merge the usable ones with the current Firestore pool.
Writes /tmp/merged.txt and /tmp/admin.json. Never prints keys."""
import base64, json, os, re, sys, time, urllib.error, urllib.parse, urllib.request

from google.auth.transport.requests import Request
from google.oauth2 import service_account

PROJECT = "gen-lang-client-0251556445"
DB = "ai-studio-android-chatbox-bc1e3568-f7e2-4c12-b2b9-9c365ad43fb9"
MODEL = "gemini-3.5-flash-lite"
KEY_RE = re.compile(r"^(AQ\.[A-Za-z0-9_\-]{20,}|AIza[A-Za-z0-9_\-]{30,})$")


def notice(msg):
    print("::notice title=ADD_KEYS::" + msg.replace("%", "%25").replace("\n", " ;; "))
    print(msg)


def admin_creds():
    raw = os.environ.get("ADMIN_RAW") or ""
    if not raw.lstrip().startswith("{"):
        raw = base64.b64decode(re.sub(r"\s+", "", raw)).decode()
    open("/tmp/admin.json", "w").write(raw)
    c = service_account.Credentials.from_service_account_info(
        json.loads(raw), scopes=["https://www.googleapis.com/auth/datastore"])
    c.refresh(Request())
    return c


def current_pool(creds):
    url = (f"https://firestore.googleapis.com/v1/projects/{PROJECT}/databases/"
           f"{urllib.parse.quote(DB, safe='')}/documents/settings/aiKeyPool")
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {creds.token}"})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            doc = json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return []
        raise
    vals = doc.get("fields", {}).get("keys", {}).get("arrayValue", {}).get("values") or []
    return [v.get("stringValue", "").strip() for v in vals if v.get("stringValue")]


def probe(key):
    body = json.dumps({"contents": [{"role": "user", "parts": [{"text": "hi"}]}],
                       "generationConfig": {"maxOutputTokens": 8}}).encode()
    req = urllib.request.Request(
        f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent?key={key}",
        data=body, method="POST", headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=25) as r:
            return "OK" if r.status == 200 else f"H{r.status}"
    except urllib.error.HTTPError as e:
        low = e.read().decode(errors="replace").lower()
        if e.code == 429 or "resource_exhausted" in low or "quota" in low:
            return "QUOTA"  # valid key, just rate-limited right now
        if "api_key_invalid" in low or "api key not valid" in low or "expired" in low:
            return "INVALID"
        if e.code in (401, 403):
            return "DENIED"
        return f"H{e.code}"
    except Exception:  # noqa
        return "NET"


def main():
    raw = open("/tmp/new_keys.txt", encoding="utf-8").read()
    candidates, bad_format = [], 0
    for line in raw.splitlines():
        k = line.strip().replace("\\_", "_")
        if not k or k.startswith("#"):
            continue
        if not KEY_RE.match(k):
            bad_format += 1
            continue
        if k not in candidates:
            candidates.append(k)

    creds = admin_creds()
    existing = current_pool(creds)
    results = {}
    usable = []
    for i, k in enumerate(candidates):
        if k in existing:
            results[f"n{i+1}"] = "ALREADY"
            continue
        r = probe(k)
        if r == "NET":
            time.sleep(2); r = probe(k)
        results[f"n{i+1}"] = r
        if r in ("OK", "QUOTA", "NET") or r.startswith("H5"):
            usable.append(k)
        time.sleep(0.3)

    merged = list(existing)
    for k in usable:
        if k not in merged:
            merged.append(k)
    open("/tmp/merged.txt", "w", encoding="utf-8").write("\n".join(merged) + "\n")

    counts = {}
    for v in results.values():
        counts[v] = counts.get(v, 0) + 1
    notice(f"existing={len(existing)} new_unique={len(candidates)} bad_format={bad_format} "
           f"added={len(usable)} merged_total={len(merged)} results={counts} detail={results}")
    if not merged:
        sys.exit("empty pool")


if __name__ == "__main__":
    main()
