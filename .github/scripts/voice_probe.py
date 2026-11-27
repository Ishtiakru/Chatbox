#!/usr/bin/env python3
"""Temporary: checks Gemini speech-to-text the way SairaVoiceBrain does. Keys never printed."""
import base64, json, subprocess, time, urllib.error, urllib.request

BASE = "https://generativelanguage.googleapis.com/v1beta/models"
MODELS = ["gemini-3.5-flash-lite", "gemini-flash-lite-latest", "gemini-3.6-flash", "gemini-4-hydrogen", "gemini-3.5-flash", "gemini-flash-latest"]
PHRASES = [("bn", "সায়রা, আম্মুকে কল দাও"), ("en", "Saira, call my mom on WhatsApp"), ("bn", "সায়রা, স্ক্রিন বন্ধ করো")]
SYSTEM = ("You are a speech-to-text engine for a phone assistant named Saira (Bengali: সায়রা). "
          "Write exactly what the speaker said, in the language spoken: Bengali speech in Bengali script, "
          "English words in English. Always write the assistant's name as \"Saira\". "
          "Output only the transcript — no quotes, labels or translation. If there is no clear speech, output nothing.")

def note(m):
    print("::notice title=VOICE::" + str(m).replace("%", "%25").replace("\n", " ;; "))

keys = [k.strip() for k in open("/tmp/pool.txt", encoding="utf-8").read().splitlines() if k.strip() and not k.startswith("#")]
note(f"keys={len(keys)}")
from gtts import gTTS
wavs = []
for i, (lang, text) in enumerate(PHRASES):
    gTTS(text, lang=lang).save(f"/tmp/p{i}.mp3")
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", f"/tmp/p{i}.mp3", "-ar", "16000", "-ac", "1", "-sample_fmt", "s16", f"/tmp/p{i}.wav"], check=True)
    wavs.append(base64.b64encode(open(f"/tmp/p{i}.wav", "rb").read()).decode())

def call(model, key, b64, cfg):
    body = {"systemInstruction": {"parts": [{"text": SYSTEM}]},
            "contents": [{"role": "user", "parts": [{"inline_data": {"mime_type": "audio/wav", "data": b64}}, {"text": "Transcribe this."}]}],
            "generationConfig": cfg}
    req = urllib.request.Request(f"{BASE}/{model}:generateContent?key={key}", data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
    t = time.time()
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read().decode(); code = r.status
    except urllib.error.HTTPError as e:
        raw = e.read().decode(errors="replace"); code = e.code
    except Exception as e:
        return 0, str(e)[:80], time.time() - t
    dt = time.time() - t
    if code != 200:
        msg = raw
        try: msg = json.loads(raw)["error"]["message"]
        except Exception: pass
        return code, msg[:140].replace(keys[0], "K"), dt
    o = json.loads(raw)
    c = (o.get("candidates") or [{}])[0]
    txt = "".join(p.get("text", "") for p in (c.get("content") or {}).get("parts", []) if not p.get("thought"))
    u = o.get("usageMetadata", {})
    return code, f"{c.get('finishReason')} thoughts={u.get('thoughtsTokenCount')} text='{txt.strip()}'", dt

ki = 0
for cfg_name, cfg in [("plain300", {"maxOutputTokens": 300}), ("t0_1024", {"maxOutputTokens": 1024, "temperature": 0})]:
    for model in MODELS:
        results = []
        for pi, b64 in enumerate(wavs):
            for attempt in range(4):
                key = keys[ki % len(keys)]; ki += 1
                code, msg, dt = call(model, key, b64, cfg)
                if code == 200 or code == 404:
                    break
            results.append(f"p{pi}:{code} {dt:.1f}s {msg}")
            if code == 404:
                break
        note(f"{cfg_name} {model} | " + " || ".join(results))
