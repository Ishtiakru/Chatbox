#!/usr/bin/env python3
"""Probe the Gemini key pool against the live REST API (read-only, CI runner).

For up to N keys from the AI_KEY_POOL secret: send one tiny generateContent
request and classify the HTTP answer. Output is public-safe: keys are masked
(first 8 + last 6) and only status/error-class is reported.

Classes:
  OK              200 + candidates        -> key works
  QUOTA           429 / RESOURCE_EXHAUSTED -> valid key, out of quota
  INVALID_KEY     API_KEY_INVALID / bad request naming the key
  MODEL           404 / NOT_FOUND (model)  -> key accepted, model name wrong
  HTTP_x          anything else (printed with short body snippet)
"""
import base64
import json
import re
import sys
import urllib.error
import urllib.request

MODELS = ["gemini-3.8-flash", "gemini-3.5-flash-lite"]
MAX_KEYS = 6


def notice(msg: str) -> None:
    clean = str(msg).replace("%", "%25").replace("\r", "%0D").replace("\n", " ")
    print(f"::notice title=KeyProbe::{clean}")
    print(msg)


def mask(key: str) -> str:
    return f"{key[:8]}…{key[-6:]}" if len(key) > 16 else "(short)"


def classify(status: int, body: str, model: str) -> tuple[str, str]:
    low = body.lower()
    if status == 200:
        if '"candidates"' in body:
            return "OK", ""
        return "EMPTY200", body[:160]
    if status == 429 or "resource_exhausted" in low or "resource exhausted" in low or "quota" in low:
        return "QUOTA", ""
    if "api_key_invalid" in low or "api key not valid" in low or "api_key_not_valid" in low:
        return "INVALID_KEY", ""
    if status == 404 or "not found" in low or "is not found" in low:
        return "MODEL", body[:160]
    if status == 400 and "key" in low:
        return "INVALID_KEY", body[:160]
    return f"HTTP_{status}", body[:160]


def probe(key: str, model: str) -> tuple[str, str]:
    url = (
        "https://generativelanguage.googleapis.com/v1beta/models/"
        f"{model}:generateContent?key={key}"
    )
    payload = json.dumps(
        {
            "contents": [
                {"role": "user", "parts": [{"text": "Reply with OK only."}]}
            ],
            "generationConfig": {"maxOutputTokens": 8},
        }
    ).encode()
    req = urllib.request.Request(url, data=payload, method="POST")
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=25) as resp:
            return classify(resp.status, resp.read().decode(errors="replace"), model)
    except urllib.error.HTTPError as exc:
        body = exc.read().decode(errors="replace")
        return classify(exc.code, body, model)
    except Exception as exc:  # noqa: BLE001
        return "NET", f"{type(exc).__name__}: {exc}"[:160]


def main() -> None:
    raw = ""
    for name in ("AI_KEY_POOL",):
        raw = __import__("os").environ.get(name) or ""
        if raw.strip():
            break
    if not raw.strip():
        notice("no AI_KEY_POOL secret available")
        sys.exit(1)
    if not raw.lstrip().startswith("AQ.") and not raw.lstrip().startswith("AIza"):
        try:
            raw = base64.b64decode(re.sub(r"\s+", "", raw)).decode()
        except Exception:
            pass
    keys: list[str] = []
    seen: set[str] = set()
    for line in raw.splitlines():
        k = line.strip()
        if not k or k.startswith("#"):
            continue
        for part in re.findall(r"AQ\.[A-Za-z0-9_\-]+|[A-Za-z0-9_\-]{35,}", k):
            if part not in seen:
                seen.add(part)
                keys.append(part)
    if not keys:
        notice("pool decoded but zero keys")
        sys.exit(1)

    results = []
    ok = quota = bad = other = 0
    for idx, key in enumerate(keys[:MAX_KEYS]):
        model = MODELS[0]
        cls, detail = probe(key, model)
        if cls == "MODEL":
            cls2, detail2 = probe(key, MODELS[1])
            if cls2 != "MODEL":
                cls, detail = cls2, detail2
            else:
                cls = f"MODEL({cls}|{cls2})"
        if cls == "OK":
            ok += 1
        elif cls == "QUOTA":
            quota += 1
        elif cls in ("INVALID_KEY",) or cls.startswith("INVALID"):
            bad += 1
        else:
            other += 1
        line = f"key{idx + 1} {mask(key)} -> {cls}"
        if detail:
            line += f" | {detail}"
        results.append(line)
        if cls == "OK":
            # a working key proves the pipeline; stop to keep the log short
            break

    summary = (
        f"probe n={min(len(keys), MAX_KEYS)}/{len(keys)} ok={ok} quota={quota} "
        f"invalid={bad} other={other} :: " + " ;; ".join(results)
    )
    notice(summary)


if __name__ == "__main__":
    main()
