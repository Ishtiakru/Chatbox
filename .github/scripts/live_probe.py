#!/usr/bin/env python3
"""Probe the Gemini Live API (BidiGenerateContent) with the key pool for Saira's voice mode.

Measures, per Live model: setup time, time from the end of speech to Saira's first audio,
what the model heard (input transcription), whether it calls the phone tool, and whether it
stays quiet for speech that is NOT addressed to Saira. Keys are never printed."""
import base64, json, re, sys, time, urllib.error, urllib.request

import numpy as np
from websockets.sync.client import connect

BASE = "https://generativelanguage.googleapis.com/v1beta"
WS = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.{ver}.GenerativeService.BidiGenerateContent?key={key}"

SYSTEM = (
    "You are Saira (সায়রা), a warm, quick female voice assistant on the user's phone. The microphone hears "
    "everyone around the phone. Respond ONLY when someone calls you by name (Saira / সায়রা) or is clearly "
    "continuing a conversation they started with you. For all other speech (people talking to each other, TV) "
    "stay completely silent — produce no audio at all. When addressed: if they want a phone action (call, "
    "message, open an app, screen on/off, answer/decline a call) call the phone_action tool and do not talk; "
    "otherwise answer in 1-2 short spoken sentences, Bengali if they spoke Bengali, English if English."
)
TOOLS = [{"functionDeclarations": [{
    "name": "phone_action",
    "description": "Do something on the user's phone.",
    "parameters": {"type": "OBJECT", "properties": {
        "action": {"type": "STRING", "enum": ["call", "video_call", "message", "answer", "decline", "open_app", "screen_on", "screen_off"]},
        "app": {"type": "STRING", "enum": ["phone", "sms", "whatsapp", "messenger", "telegram", "imo", "viber", "other"]},
        "target": {"type": "STRING", "description": "Contact name / number as said, or the app name"},
        "text": {"type": "STRING", "description": "Message text to send"},
    }, "required": ["action"]},
}]}]

CLIPS = {
    "ask": "সায়রা, এখন কয়টা বাজে?",
    "call": "সায়রা, আম্মুকে কল দাও।",
    "other": "আজকে বাজারে অনেক ভিড় ছিল, তাই আসতে দেরি হলো।",
    "soft_en": "Saira, what is the capital of Japan?",
}


def mask(s):
    return re.sub(r"(AIza|AQ\.)[\w\-\.]+", "<key>", str(s))


def http(method, url, body=None, timeout=60):
    req = urllib.request.Request(url, data=json.dumps(body).encode() if body else None, method=method)
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode(errors="replace")[:200]
    except Exception as e:  # noqa
        return 0, str(e)[:120]


def keys():
    raw = open("/tmp/pool.txt", encoding="utf-8").read()
    if not re.search(r"AQ\.|AIza", raw):
        raw = base64.b64decode(re.sub(r"\s+", "", raw)).decode()
    return [k.strip() for k in raw.splitlines() if k.strip() and not k.startswith("#")]


def list_models(ks):
    for k in ks[:6]:
        st, o = http("GET", f"{BASE}/models?pageSize=1000&key={k}")
        if st == 200:
            return o.get("models", [])
        print("list:", st, mask(o)[:100])
    return []


def tts(ks, tts_models, text):
    for m in tts_models:
        for k in ks[:8]:
            st, o = http("POST", f"{BASE}/models/{m}:generateContent?key={k}", {
                "contents": [{"parts": [{"text": text}]}],
                "generationConfig": {"responseModalities": ["AUDIO"], "speechConfig": {
                    "voiceConfig": {"prebuiltVoiceConfig": {"voiceName": "Puck"}}}},
            })
            if st == 200 and '"inlineData"' in json.dumps(o):
                part = next(p for p in o["candidates"][0]["content"]["parts"] if "inlineData" in p)
                pcm24 = np.frombuffer(base64.b64decode(part["inlineData"]["data"]), dtype="<i2").astype(np.float32)
                x = np.arange(0, len(pcm24), 1.5)
                pcm16 = np.interp(x, np.arange(len(pcm24)), pcm24).astype(np.int16)
                return pcm16, m
            if st not in (429, 503, 500):
                print(f"tts {m}: {st} {mask(o)[:90]}")
                break
    return None, None


def run_clip(key, model, ver, pcm, quiet=False):
    """Stream the clip in real time (100 ms chunks) + 2 s of room noise; return measurements."""
    res = {"err": None, "setup": None, "first_audio": None, "heard": "", "said": "", "tool": None, "audio_ms": 0}
    try:
        t0 = time.time()
        with connect(WS.format(ver=ver, key=key), open_timeout=15, max_size=None) as ws:
            setup = {"model": f"models/{model}",
                     "generationConfig": {"responseModalities": ["AUDIO"], "speechConfig": {
                         "voiceConfig": {"prebuiltVoiceConfig": {"voiceName": "Kore"}}}},
                     "systemInstruction": {"parts": [{"text": SYSTEM}]},
                     "tools": TOOLS,
                     "inputAudioTranscription": {}, "outputAudioTranscription": {}}
            if ver == "v1alpha":
                setup["proactivity"] = {"proactiveAudio": True}
            ws.send(json.dumps({"setup": setup}))
            msg = json.loads(ws.recv(timeout=15))
            if "setupComplete" not in msg:
                res["err"] = "nosetup:" + mask(msg)[:80]
                return res
            res["setup"] = time.time() - t0
            if quiet:
                pcm = (pcm * 0.15).astype(np.int16)
            rng = np.random.default_rng(1)
            noise = (rng.normal(0, 60, 16000 * 2)).astype(np.int16)
            stream = np.concatenate([pcm, noise])
            speech_end = None
            chunk = 1600
            start = time.time()
            done = False
            i = 0
            deadline = None
            while not done:
                now = time.time()
                if i < len(stream) and now - start >= i / 16000:
                    b = stream[i:i + chunk].tobytes()
                    ws.send(json.dumps({"realtimeInput": {"audio": {"data": base64.b64encode(b).decode(), "mimeType": "audio/pcm;rate=16000"}}}))
                    i += chunk
                    if i >= len(pcm) and speech_end is None:
                        speech_end = start + len(pcm) / 16000
                    if i >= len(stream):
                        deadline = time.time() + 10
                    continue
                try:
                    raw = ws.recv(timeout=0.02)
                except TimeoutError:
                    if deadline and time.time() > deadline:
                        break
                    continue
                m = json.loads(raw)
                sc = m.get("serverContent") or {}
                if "inputTranscription" in sc:
                    res["heard"] += sc["inputTranscription"].get("text", "")
                if "outputTranscription" in sc:
                    res["said"] += sc["outputTranscription"].get("text", "")
                for p in (sc.get("modelTurn") or {}).get("parts", []):
                    if "inlineData" in p:
                        if res["first_audio"] is None:
                            res["first_audio"] = time.time() - (speech_end or time.time())
                        res["audio_ms"] += len(base64.b64decode(p["inlineData"]["data"])) // 48
                if "toolCall" in m:
                    fc = m["toolCall"].get("functionCalls", [{}])[0]
                    res["tool"] = (fc.get("name"), fc.get("args"), round(time.time() - (speech_end or time.time()), 2))
                    ws.send(json.dumps({"toolResponse": {"functionResponses": [{"id": fc.get("id"), "name": fc.get("name"), "response": {"result": "done"}}]}}))
                if sc.get("turnComplete") and (res["first_audio"] is not None or res["tool"]):
                    done = True
                if "goAway" in m or "error" in m:
                    res["err"] = mask(m)[:100]
                    done = True
    except Exception as e:  # noqa
        res["err"] = type(e).__name__ + ":" + mask(e)[:110]
    return res


def main():
    ks = keys()
    print(f"keys: {len(ks)}")
    models = list_models(ks)
    live = [m["name"].split("/")[1] for m in models if "bidiGenerateContent" in m.get("supportedGenerationMethods", [])]
    ttsm = [m["name"].split("/")[1] for m in models if "tts" in m["name"]]
    print("LIVE MODELS:", live)
    print("TTS MODELS:", ttsm)
    clips = {}
    for name, text in CLIPS.items():
        pcm, used = tts(ks, ttsm, text)
        print(f"clip {name}: {'ok %.1fs via %s' % (len(pcm) / 16000, used) if pcm is not None else 'FAILED'}")
        if pcm is not None:
            clips[name] = pcm
    if not clips:
        sys.exit("no clips")
    # Prefer native-audio / live models, newest first.
    order = sorted(live, key=lambda n: (("native-audio" not in n and "live" not in n), n), reverse=False)
    summary = []
    for model in order[:8]:
        for ver in ("v1beta", "v1alpha"):
            ok_key = None
            for k in ks[:10]:
                r = run_clip(k, model, ver, clips.get("ask", next(iter(clips.values()))))
                tag = f"{model} [{ver}]"
                if r["err"] is None and r["setup"] is not None:
                    ok_key = k
                    print(f"{tag} ask: setup={r['setup']:.2f}s first_audio={r['first_audio']} heard={r['heard'][:60]!r} said={r['said'][:80]!r} tool={r['tool']}")
                    break
                print(f"{tag}: {r['err']}")
                if r["err"] and ("not found" in r["err"].lower() or "not supported" in r["err"].lower() or "1008" in r["err"]):
                    break
            if not ok_key:
                continue
            lat = []
            for name in ("ask", "call", "other", "soft_en"):
                if name not in clips:
                    continue
                for attempt in range(2):
                    r = run_clip(ok_key, model, ver, clips[name], quiet=(name == "soft_en"))
                    print(f"  {name}{'#2' if attempt else ''}: err={r['err']} first_audio={None if r['first_audio'] is None else round(r['first_audio'], 2)} "
                          f"audio={r['audio_ms']}ms heard={r['heard'][:70]!r} said={r['said'][:90]!r} tool={r['tool']}")
                    if r["first_audio"] is not None and name != "other":
                        lat.append(r["first_audio"])
            summary.append(f"{model}[{ver}] median_first_audio={np.median(lat):.2f}s" if lat else f"{model}[{ver}] no-audio")
            break
    print("SUMMARY:", " | ".join(summary))


if __name__ == "__main__":
    main()
