#!/usr/bin/env python3
import base64, json, sys, time, traceback, urllib.error, urllib.request
def note(m): print("::notice title=VOICE::" + str(m).replace("%", "%25").replace("\n", " ;; "))
def _hook(t, v, tb): note("CRASH " + "".join(traceback.format_exception(t, v, tb))[-500:])
sys.excepthook = _hook
BASE = "https://generativelanguage.googleapis.com/v1beta"
keys = [k.strip() for k in open("/tmp/pool.txt", encoding="utf-8").read().splitlines() if k.strip() and not k.startswith("#")]
with urllib.request.urlopen(f"{BASE}/models?pageSize=1000&key={keys[0]}", timeout=20) as r:
    models = json.loads(r.read())["models"]
names = [m["name"].split("/")[1] for m in models]
tts = [n for n in names if "tts" in n or "audio" in n or "live" in n or "native" in n]
note("TTS-ish models: " + ", ".join(tts))
note("flash-lite models: " + ", ".join(n for n in names if "lite" in n))
cands = [n for n in names if "tts" in n][:4]
ki = 1
for model in cands:
    res = []
    for voice in ["Kore", "Leda"]:
        for text in ["জি, বলুন।", "আজ শুক্রবার, বিকেলে বৃষ্টি হতে পারে।"]:
            body = {"contents": [{"parts": [{"text": text}]}],
                    "generationConfig": {"responseModalities": ["AUDIO"], "speechConfig": {"voiceConfig": {"prebuiltVoiceConfig": {"voiceName": voice}}}}}
            for a in range(4):
                key = keys[ki % len(keys)]; ki += 1
                t = time.time()
                req = urllib.request.Request(f"{BASE}/models/{model}:generateContent?key={key}", data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
                try:
                    with urllib.request.urlopen(req, timeout=30) as r: raw = r.read().decode(); code = 200
                except urllib.error.HTTPError as e: raw = e.read().decode(errors="replace"); code = e.code
                dt = time.time() - t
                if code == 200: break
            if code == 200:
                o = json.loads(raw); p = o["candidates"][0]["content"]["parts"][0].get("inlineData", {})
                res.append(f"{voice} {len(text)}ch {dt:.2f}s {p.get('mimeType')} {len(base64.b64decode(p.get('data','')))}B")
            else:
                res.append(f"{voice} {code} {dt:.2f}s {raw[:90]}")
    note(f"{model}: " + " || ".join(res))
