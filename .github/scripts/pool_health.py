#!/usr/bin/env python3
"""Every key × candidate text models: OK / QUOTA (which quota) / other. Keys never printed."""
import base64, collections, concurrent.futures as cf, json, re, time, urllib.error, urllib.request

BASE = "https://generativelanguage.googleapis.com/v1beta/models"
MODELS = ["gemini-3.5-flash-lite", "gemini-flash-lite-latest", "gemini-3.1-flash-lite", "gemini-3.1-flash-lite-preview",
          "gemini-2.5-flash-lite", "gemini-2.5-flash", "gemini-3.5-flash", "gemini-flash-latest", "gemini-3-flash-preview",
          "gemini-3.8-flash", "gemini-omni-1.2-flash", "gemini-omni-flash-preview", "gemini-2.5-pro", "gemini-3.1-pro-preview"]
BODY = json.dumps({"contents": [{"role": "user", "parts": [{"text": "hi"}]}],
                   "generationConfig": {"maxOutputTokens": 64}}).encode()


def keys():
    raw = open("/tmp/pool.txt", encoding="utf-8").read()
    if not re.search(r"AQ\.|AIza", raw):
        raw = base64.b64decode(re.sub(r"\s+", "", raw)).decode()
    return [k.strip() for k in raw.splitlines() if k.strip() and not k.startswith("#")]


def call(model, key):
    req = urllib.request.Request(f"{BASE}/{model}:generateContent?key={key}", data=BODY, method="POST")
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=25) as r:
            o = json.loads(r.read().decode())
            t = "".join(p.get("text", "") for c in o.get("candidates", [])[:1] for p in (c.get("content") or {}).get("parts", []))
            return "OK" if t.strip() else "EMPTY"
    except urllib.error.HTTPError as e:
        raw = e.read().decode(errors="replace")
        if e.code == 429 or "RESOURCE_EXHAUSTED" in raw:
            q = re.findall(r'"quotaId":\s*"([^"]+)"', raw)
            return "Q:" + (",".join(sorted(set(x.replace("GenerateRequests", "Req").replace("GenerateContent", "Gen") for x in q))) or "?")
        return f"H{e.code}:" + re.sub(r"(AIza|AQ\.)[\w\-\.]+", "<k>", raw)[:60].replace("\n", " ")
    except Exception as e:  # noqa
        return "NET:" + type(e).__name__


def main():
    ks = keys()
    lines = [f"keys={len(ks)} at {time.strftime('%H:%M:%SZ', time.gmtime())}"]
    for m in MODELS:
        with cf.ThreadPoolExecutor(12) as ex:
            res = list(ex.map(lambda k: call(m, k), ks))
        c = collections.Counter(res)
        lines.append(f"{m}: OK={c.get('OK', 0)}/{len(ks)} :: " + "; ".join(f"{k}×{v}" for k, v in c.most_common(4) if k != "OK"))
    msg = "\n".join(lines)
    print(msg)
    for i in range(0, len(msg), 3000):
        print("::notice title=HEALTH %d::" % (i // 3000) + msg[i:i + 3000].replace("%", "%25").replace("\n", "%0A"))


if __name__ == "__main__":
    main()
