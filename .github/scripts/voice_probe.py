#!/usr/bin/env python3
import base64, json, subprocess, sys, time, traceback, urllib.error, urllib.request, datetime
def _hook(t, v, tb):
    print("::notice title=VOICE::CRASH " + "".join(traceback.format_exception(t, v, tb))[-600:].replace("\n", " ;; "))
sys.excepthook = _hook
BASE = "https://generativelanguage.googleapis.com/v1beta/models"
SYSTEM = "You are Saira (Bengali: সায়রা), a warm, quick female voice assistant living on the owner's phone. You get a short audio clip of the owner speaking. Reply in EXACTLY this format, two lines:\nT: <exact transcript — Bengali speech in Bengali script, English words in English, Banglish as spoken; always write the name as Saira; nothing else; empty if there is no clear speech>\nR: <your spoken reply, or ->\nRules for R: write - (a single dash) when the speech is a phone action (call, video call, message/SMS, answer or decline/cut a call, open an app, screen on/off, yes/no), when it is not addressed to Saira (the owner must start with Saira unless it is a follow-up turn), or when asked to transcribe only. Otherwise answer like a caring friend in the owner's language (Bengali, Banglish or English): 1–2 short spoken sentences, no markdown, emojis, lists or links. Use the given time for time/date questions."
PHRASES = [("bn", "সায়রা, আম্মুকে কল দাও"), ("bn", "সায়রা, আজকে কি বার?"), ("bn", "সায়রা, তুমি কেমন আছো?"),
           ("en", "Saira, what is the capital of Japan?"), ("bn", "আজকে বিকেলে বাজারে যাবো"), ("bn", "হ্যাঁ")]
def note(m): print("::notice title=VOICE::" + str(m).replace("%", "%25").replace("\n", " ;; "))
keys = [k.strip() for k in open("/tmp/pool.txt", encoding="utf-8").read().splitlines() if k.strip() and not k.startswith("#")]
from gtts import gTTS
import imageio_ffmpeg
FF = imageio_ffmpeg.get_ffmpeg_exe()
wavs = []
for i, (lang, text) in enumerate(PHRASES):
    gTTS(text, lang=lang).save(f"/tmp/p{i}.mp3")
    subprocess.run([FF, "-loglevel", "error", "-y", "-i", f"/tmp/p{i}.mp3", "-ar", "16000", "-ac", "1", "-sample_fmt", "s16", f"/tmp/p{i}.wav"], check=True)
    wavs.append(base64.b64encode(open(f"/tmp/p{i}.wav", "rb").read()).decode())
def call(model, key, b64, ctx):
    body = {"systemInstruction": {"parts": [{"text": SYSTEM}]},
            "contents": [{"role": "user", "parts": [{"inline_data": {"mime_type": "audio/wav", "data": b64}}, {"text": ctx}]}],
            "generationConfig": {"maxOutputTokens": 320, "temperature": 0.3}}
    req = urllib.request.Request(f"{BASE}/{model}:generateContent?key={key}", data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
    t = time.time()
    try:
        with urllib.request.urlopen(req, timeout=30) as r: raw = r.read().decode(); code = r.status
    except urllib.error.HTTPError as e: raw = e.read().decode(errors="replace"); code = e.code
    dt = time.time() - t
    if code != 200: return code, raw[:100], dt
    o = json.loads(raw); c = (o.get("candidates") or [{}])[0]
    return code, "".join(p.get("text", "") for p in (c.get("content") or {}).get("parts", []) if not p.get("thought")), dt
now = datetime.datetime.now().strftime("%A %d %B %Y, %I:%M %p")
ki = 0
for model in ["gemini-3.5-flash-lite", "gemini-flash-lite-latest"]:
    for rnd in range(2):
        for pi, b64 in enumerate(wavs):
            ctx = f"Now: {now}\nFollow-up turn (owner may speak without saying Saira): no\n" + ("Transcribe only (R: -)." if pi == 5 else "Transcribe and reply.")
            for a in range(4):
                key = keys[ki % len(keys)]; ki += 1
                code, msg, dt = call(model, key, b64, ctx)
                if code == 200: break
            note(f"{model} r{rnd} p{pi} {code} {dt:.2f}s | {msg}")
