#!/usr/bin/env python3
"""Measure Gemini latency for Saira auto-reply style requests under different
generationConfig variants (thinking off/minimal, smaller output). Keys never printed."""
import base64, json, re, statistics, time, urllib.error, urllib.request

BASE = "https://generativelanguage.googleapis.com/v1beta/models"
MODELS = ["gemini-3.5-flash-lite", "gemini-flash-lite-latest"]
SYSTEM = ("You are Saira, replying on WhatsApp on behalf of the phone owner who is busy. "
          "Reply in the sender's language, 1-2 short sentences, friendly.")
PROMPT = "Sender: Rahim\nMessage: bhai tumi kothay? kokhon free hoba? ekta jaruri kotha chilo"
VARIANTS = {
    "base": {"maxOutputTokens": 1024, "temperature": 0.7},
    "budget0": {"maxOutputTokens": 1024, "temperature": 0.7, "thinkingConfig": {"thinkingBudget": 0}},
    "minimal": {"maxOutputTokens": 1024, "temperature": 0.7, "thinkingConfig": {"thinkingLevel": "minimal"}},
    "low": {"maxOutputTokens": 1024, "temperature": 0.7, "thinkingConfig": {"thinkingLevel": "low"}},
    "budget0_256": {"maxOutputTokens": 256, "temperature": 0.7, "thinkingConfig": {"thinkingBudget": 0}},
}
TRIES = 4


def call(key, model, cfg):
    body = json.dumps({
        "system_instruction": {"parts": [{"text": SYSTEM}]},
        "contents": [{"role": "user", "parts": [{"text": PROMPT}]}],
        "generationConfig": cfg,
    }).encode()
    req = urllib.request.Request(f"{BASE}/{model}:generateContent?key={key}", data=body, method="POST")
    req.add_header("Content-Type", "application/json")
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read().decode(errors="replace"); st = r.status
    except urllib.error.HTTPError as e:
        raw = e.read().decode(errors="replace"); st = e.code
    except Exception as e:  # noqa
        return "NET", time.time() - t0, str(e)[:60]
    dt = time.time() - t0
    if st != 200:
        return f"H{st}", dt, raw[:120].replace("\n", " ")
    o = json.loads(raw)
    c = (o.get("candidates") or [{}])[0]
    txt = "".join(p.get("text", "") for p in (c.get("content") or {}).get("parts", []) if not p.get("thought"))
    th = (o.get("usageMetadata") or {}).get("thoughtsTokenCount", 0)
    return ("OK" if txt.strip() else "EMPTY"), dt, f"th={th} len={len(txt)}"


def main():
    raw = open("/tmp/pool.txt", encoding="utf-8").read()
    if not re.search(r"AQ\.|AIza", raw):
        raw = base64.b64decode(re.sub(r"\s+", "", raw)).decode()
    keys = [k.strip() for k in raw.splitlines() if k.strip() and not k.startswith("#")]
    out, n = [], 0
    for model in MODELS:
        for name, cfg in VARIANTS.items():
            res = []
            for _ in range(TRIES):
                res.append(call(keys[n % len(keys)], model, cfg)); n += 1
            oks = [d for s, d, _ in res if s == "OK"]
            med = f"{statistics.median(oks):.2f}s" if oks else "-"
            info = ";".join(f"{s}:{d:.1f}s:{x}" for s, d, x in res)
            out.append(f"[{model} {name}] ok={len(oks)}/{TRIES} median={med} :: {info}")
    msg = "\n".join(out)
    print(msg)
    print("::notice title=SUMMARY::" + msg.replace("%", "%25").replace("\n", " ;; "))


if __name__ == "__main__":
    main()
