#!/usr/bin/env python3
"""Probe the Gemini key pool exactly like the app does (CI runner, read-only).

Replicates PooledGeminiProvider's request (system_instruction + contents +
generationConfig maxOutputTokens=1024) for several models, against
generateContent and streamGenerateContent?alt=sse. Reports per-model:
class counts, whether real text came back, finishReason, latency.
Keys are never printed (index only).
"""
import base64
import json
import re
import time
import urllib.error
import urllib.request

MODELS = [
    "gemini-3.8-flash",
    "gemini-3.5-flash-lite",
    "gemini-flash-latest",
    "gemini-flash-lite-latest",
]
BASE = "https://generativelanguage.googleapis.com/v1beta/models"
TRIES_PER_MODEL = 6
SYSTEM = "You are Saira, a friendly Bengali/English assistant inside the Chatbox app. Keep answers short."


def notice(msg: str) -> None:
    clean = str(msg).replace("%", "%25").replace("\r", "%0D").replace("\n", " ;; ")
    print(f"::notice title=SUMMARY::{clean}")
    print(msg)


def body() -> bytes:
    return json.dumps({
        "system_instruction": {"parts": [{"text": SYSTEM}]},
        "contents": [{"role": "user", "parts": [{"text": "hi"}]}],
        "generationConfig": {"maxOutputTokens": 1024, "temperature": 0.7},
    }).encode()


def text_of(obj: dict) -> tuple[str, str]:
    cands = obj.get("candidates") or []
    if not cands:
        return "", "NO_CANDIDATES"
    c = cands[0]
    parts = (c.get("content") or {}).get("parts") or []
    txt = "".join(p.get("text", "") for p in parts if not p.get("thought"))
    return txt.strip(), c.get("finishReason", "")


def classify(status: int, raw: str) -> str:
    low = raw.lower()
    if status == 429 or "resource_exhausted" in low or "quota" in low:
        return "QUOTA"
    if "api_key_invalid" in low or "api key not valid" in low:
        return "INVALID_KEY"
    if status == 404:
        return "MODEL404"
    return f"HTTP_{status}"


def call(url: str, timeout: int = 25):
    req = urllib.request.Request(url, data=body(), method="POST")
    req.add_header("Content-Type", "application/json")
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode(errors="replace"), time.time() - t0
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode(errors="replace"), time.time() - t0
    except Exception as e:  # noqa: BLE001
        return 0, f"{type(e).__name__}: {e}", time.time() - t0


def probe_generate(key: str, model: str) -> str:
    st, raw, dt = call(f"{BASE}/{model}:generateContent?key={key}")
    if st == 200:
        try:
            txt, fin = text_of(json.loads(raw))
        except Exception:  # noqa: BLE001
            return f"BADJSON {dt:.1f}s"
        return (f"OK len={len(txt)} fin={fin} {dt:.1f}s" if txt
                else f"NOTEXT fin={fin} {dt:.1f}s snip={raw[:140]}")
    if st == 0:
        return f"NET {dt:.1f}s {raw[:60]}"
    return f"{classify(st, raw)} {dt:.1f}s {raw[:100] if st not in (429,) else ''}"


def probe_sse(key: str, model: str) -> str:
    st, raw, dt = call(f"{BASE}/{model}:streamGenerateContent?alt=sse&key={key}", 30)
    if st != 200:
        return f"SSE_{classify(st, raw) if st else 'NET'} {dt:.1f}s"
    chunks, got = 0, 0
    for line in raw.splitlines():
        if not line.startswith("data:"):
            continue
        chunks += 1
        try:
            txt, _ = text_of(json.loads(line[5:].strip()))
            got += len(txt)
        except Exception:  # noqa: BLE001
            pass
    return f"SSE chunks={chunks} textlen={got} {dt:.1f}s"


def main() -> None:
    raw = open("/tmp/pool.txt", encoding="utf-8").read()
    if not re.search(r"AQ\.|AIza", raw):
        raw = base64.b64decode(re.sub(r"\s+", "", raw)).decode()
    keys = [k.strip() for k in raw.splitlines() if k.strip() and not k.startswith("#")]
    lines = [f"keys={len(keys)}"]
    offset = 0
    for model in MODELS:
        results = []
        sse = "-"
        for i in range(TRIES_PER_MODEL):
            idx = (offset + i) % len(keys)
            r = probe_generate(keys[idx], model)
            results.append(f"k{idx + 1}:{r}")
            if r.startswith("OK") and sse == "-":
                sse = probe_sse(keys[idx], model)
        offset += TRIES_PER_MODEL
        ok = sum(1 for r in results if ":OK" in r)
        lines.append(f"[{model}] ok={ok}/{len(results)} sse={sse} :: " + " | ".join(results))
    notice("\n".join(lines))


if __name__ == "__main__":
    main()
