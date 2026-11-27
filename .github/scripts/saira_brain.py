#!/usr/bin/env python3
"""Saira's "pro brain": the strongest AI models the admin has API keys for.

Keys come from the SAIRA_PRO_KEYS secret (one per line, set from the app:
Connected → AI Brain). Each line is a key, optionally followed by a model name:

    sk-ant-...                      → Anthropic Claude
    sk-proj-... / sk-...            → OpenAI
    AIza... / AQ....                → Google Gemini (paid tier)
    sk-or-...                       → OpenRouter
    xai-...                         → xAI Grok
    gsk_...                         → Groq
    deepseek:sk-...                 → DeepSeek (explicit prefix; plain 32-hex sk- keys are detected too)
    sk-ant-... claude-fable-5-2     → force a model

Keys are never printed. Nothing here is bundled into the APK.
"""
from __future__ import annotations

import json
import os
import re
import time
import urllib.error
import urllib.request

# Strongest first. The first entry that answers wins; on "model not found" the next one is tried,
# then the provider's model list is searched for the newest matching family.
PROVIDERS = {
    "anthropic": {"models": ["claude-opus-5-5", "claude-fable-5-2", "claude-sonnet-5-5"],
                  "fast": ["claude-haiku-5-5", "claude-sonnet-5-5"],
                  "discover": [r"claude-fable", r"claude-opus", r"claude-sonnet"]},
    "openai": {"url": "https://api.openai.com/v1", "models": ["gpt-6.2-astra", "gpt-6.1-sol", "gpt-5.5"],
               "fast": ["gpt-6.2-sol", "gpt-6.1-terra"],
               "discover": [r"^gpt-6\.\d+-astra$", r"^gpt-6", r"^gpt-5\.\d+$"]},
    "gemini": {"models": ["gemini-pro-latest", "gemini-3.1-pro-preview", "gemini-3.8-flash"],
               "fast": ["gemini-3.8-flash", "gemini-flash-latest"],
               "discover": [r"gemini-\d.*pro", r"gemini-\d.*flash$"]},
    "openrouter": {"url": "https://openrouter.ai/api/v1",
                   "models": ["anthropic/claude-opus-5-5", "openai/gpt-6.2-astra", "google/gemini-pro-latest"],
                   "fast": ["anthropic/claude-sonnet-5-5", "google/gemini-flash-latest"],
                   "discover": [r"^anthropic/claude-(opus|fable)", r"^openai/gpt-6", r"^google/gemini.*pro"]},
    "xai": {"url": "https://api.x.ai/v1", "models": ["grok-5", "grok-4"], "fast": ["grok-4-fast"],
            "discover": [r"^grok-\d+(\.\d+)?$", r"^grok"]},
    "deepseek": {"url": "https://api.deepseek.com", "models": ["deepseek-reasoner", "deepseek-chat"],
                 "fast": ["deepseek-flash", "deepseek-chat"], "discover": [r"deepseek"]},
    "groq": {"url": "https://api.groq.com/openai/v1", "models": ["moonshotai/kimi-k2-instruct", "openai/gpt-oss-120b"],
             "fast": ["openai/gpt-oss-120b"], "discover": [r"kimi", r"gpt-oss-120b", r"llama"]},
}
ORDER = ["anthropic", "openai", "gemini", "openrouter", "xai", "deepseek", "groq"]
VISION = {"anthropic", "openai", "gemini", "openrouter", "xai"}


def detect(key: str) -> str | None:
    if ":" in key.split()[0][:12]:
        p = key.split(":", 1)[0].lower()
        return p if p in PROVIDERS else None
    k = key.strip()
    if k.startswith("sk-ant-"):
        return "anthropic"
    if k.startswith("sk-or-"):
        return "openrouter"
    if k.startswith(("AIza", "AQ.")):
        return "gemini"
    if k.startswith("xai-"):
        return "xai"
    if k.startswith("gsk_"):
        return "groq"
    if re.fullmatch(r"sk-[0-9a-f]{32}", k):
        return "deepseek"
    if k.startswith("sk-"):
        return "openai"
    return None


def load(raw: str | None = None) -> list[dict]:
    raw = os.environ.get("SAIRA_PRO_KEYS", "") if raw is None else raw
    out: list[dict] = []
    for line in raw.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        key, model = parts[0], (parts[1] if len(parts) > 1 else None)
        prov = detect(key)
        if prov is None:
            continue
        if key.lower().startswith(prov + ":"):
            key = key.split(":", 1)[1]
        if not any(e["key"] == key for e in out):
            out.append({"provider": prov, "key": key, "model": model})
    out.sort(key=lambda e: ORDER.index(e["provider"]))
    return out


PRO = load()
_DEAD: set[tuple[str, str]] = set()          # (key, model) pairs that 404'd
_GOOD: dict[str, str] = {}                   # key → model that worked
_DISCOVERED: dict[str, list[str]] = {}


def available() -> bool:
    return bool(PRO)


def describe() -> str:
    return ", ".join(f"{e['provider']}" + (f"({e['model']})" if e["model"] else "") for e in PRO) or "none"


class ProError(RuntimeError):
    pass


def _http(url: str, body: dict | None, headers: dict, timeout: int) -> dict:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method="POST" if data else "GET",
                                 headers={"Content-Type": "application/json", **headers})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode())


def _anthropic(e, model, system, messages, max_tokens, timeout):
    msgs = []
    for i, m in enumerate(messages):
        content = []
        for mime, b64 in m.get("images") or []:
            content.append({"type": "image", "source": {"type": "base64", "media_type": mime, "data": b64}})
        content.append({"type": "text", "text": m["text"]})
        if i == len(messages) - 1:
            content[-1]["cache_control"] = {"type": "ephemeral"}   # cache the whole growing prefix
        msgs.append({"role": "assistant" if m["role"] == "assistant" else "user", "content": content})
    body = {"model": model, "max_tokens": max_tokens, "messages": msgs,
            "system": [{"type": "text", "text": system, "cache_control": {"type": "ephemeral"}}]}
    obj = _http("https://api.anthropic.com/v1/messages", body,
                {"x-api-key": e["key"], "anthropic-version": "2023-06-01"}, timeout)
    return "".join(b.get("text", "") for b in obj.get("content") or [] if b.get("type") == "text")


def _openai_like(e, model, system, messages, max_tokens, timeout):
    base = PROVIDERS[e["provider"]]["url"]
    msgs = [{"role": "system", "content": system}]
    for m in messages:
        if m.get("images") and e["provider"] in VISION:
            content = [{"type": "image_url", "image_url": {"url": f"data:{mime};base64,{b64}"}}
                       for mime, b64 in m["images"]]
            content.append({"type": "text", "text": m["text"]})
        else:
            content = m["text"]
        msgs.append({"role": "assistant" if m["role"] == "assistant" else "user", "content": content})
    body = {"model": model, "messages": msgs}
    body["max_completion_tokens" if e["provider"] == "openai" else "max_tokens"] = max_tokens
    headers = {"Authorization": f"Bearer {e['key']}"}
    if e["provider"] == "openrouter":
        headers.update({"HTTP-Referer": "https://github.com/Ishtiakru/Chatbox", "X-Title": "Chatbox Saira"})
    obj = _http(f"{base}/chat/completions", body, headers, timeout)
    ch = (obj.get("choices") or [{}])[0].get("message") or {}
    c = ch.get("content")
    if isinstance(c, list):
        c = "".join(p.get("text", "") for p in c if isinstance(p, dict))
    return c or ""


def _gemini(e, model, system, messages, max_tokens, timeout):
    contents = []
    for m in messages:
        parts = [{"inline_data": {"mime_type": mime, "data": b64}} for mime, b64 in m.get("images") or []]
        parts.append({"text": m["text"]})
        contents.append({"role": "model" if m["role"] == "assistant" else "user", "parts": parts})
    body = {"system_instruction": {"parts": [{"text": system}]}, "contents": contents,
            "generationConfig": {"maxOutputTokens": max_tokens}}
    obj = _http(f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={e['key']}",
                body, {}, timeout)
    cands = obj.get("candidates") or []
    parts = ((cands[0] if cands else {}).get("content") or {}).get("parts") or []
    return "".join(p.get("text", "") for p in parts if not p.get("thought"))


def _discover(e) -> list[str]:
    k = e["key"]
    if k in _DISCOVERED:
        return _DISCOVERED[k]
    names: list[str] = []
    try:
        p = e["provider"]
        if p == "anthropic":
            obj = _http("https://api.anthropic.com/v1/models?limit=100", None,
                        {"x-api-key": k, "anthropic-version": "2023-06-01"}, 30)
            names = [m["id"] for m in obj.get("data") or []]
        elif p == "gemini":
            obj = _http(f"https://generativelanguage.googleapis.com/v1beta/models?pageSize=200&key={k}", None, {}, 30)
            names = [m["name"].split("/", 1)[-1] for m in obj.get("models") or []
                     if "generateContent" in (m.get("supportedGenerationMethods") or [])]
        else:
            obj = _http(f"{PROVIDERS[p]['url']}/models", None, {"Authorization": f"Bearer {k}"}, 30)
            names = [m["id"] for m in obj.get("data") or []]
    except Exception as exc:  # noqa: BLE001
        print(f"[brain] model discovery failed for {e['provider']}: {type(exc).__name__}")
    picked: list[str] = []
    for pat in PROVIDERS[e["provider"]]["discover"]:
        hits = sorted((n for n in names if re.search(pat, n) and not re.search(r"embed|image|audio|tts|vision-preview", n)),
                      reverse=True)
        picked += [h for h in hits if h not in picked][:2]
    _DISCOVERED[k] = picked[:4]
    return _DISCOVERED[k]


def _models_for(e, fast: bool) -> list[str]:
    cfg = PROVIDERS[e["provider"]]
    first = [e["model"]] if e["model"] else []
    if e["key"] in _GOOD and not fast:
        first.insert(0, _GOOD[e["key"]])
    base = cfg["fast"] if fast else cfg["models"]
    seen, out = set(), []
    for m in first + base:
        if m and m not in seen and (e["key"], m) not in _DEAD:
            seen.add(m)
            out.append(m)
    return out


def chat(system: str, messages: list[dict], max_tokens: int = 16000, fast: bool = False,
         timeout: int = 600, need_vision: bool = False) -> tuple[str, str]:
    """messages: [{"role": "user"|"assistant", "text": str, "images": [(mime, b64)]?}].
    Returns (text, "provider/model"). Raises ProError when no pro key answers."""
    if not PRO:
        raise ProError("no pro keys")
    last = ""
    for e in PRO:
        if need_vision and e["provider"] not in VISION:
            continue
        fn = _anthropic if e["provider"] == "anthropic" else _gemini if e["provider"] == "gemini" else _openai_like
        tried_discovery = False
        models = _models_for(e, fast)
        i = 0
        while i < len(models):
            model = models[i]
            i += 1
            for attempt in range(3):
                try:
                    t0 = time.time()
                    text = fn(e, model, system, messages, max_tokens, timeout)
                    if not text.strip():
                        raise ProError("empty answer")
                    _GOOD.setdefault(e["key"], model)
                    print(f"[brain] {e['provider']}/{model} answered in {time.time() - t0:.0f}s")
                    return text, f"{e['provider']}/{model}"
                except urllib.error.HTTPError as h:
                    detail = ""
                    try:
                        detail = h.read().decode()[:300]
                    except Exception:  # noqa: BLE001
                        pass
                    last = f"{e['provider']}/{model} HTTP {h.code} {detail[:160]}"
                    print(f"[brain] {last}")
                    low = detail.lower()
                    if h.code in (404,) or (h.code == 400 and "model" in low and
                                            any(w in low for w in ("not found", "does not exist", "invalid model",
                                                                   "not supported", "unknown"))):
                        _DEAD.add((e["key"], model))
                        break                                    # next model
                    if h.code in (401, 402, 403):
                        i = len(models)                          # bad key / no credit → next provider
                        tried_discovery = True
                        break
                    if h.code == 429 or h.code >= 500:
                        time.sleep(8 * (attempt + 1))
                        continue
                    break
                except Exception as exc:  # noqa: BLE001
                    last = f"{e['provider']}/{model} {type(exc).__name__}: {str(exc)[:120]}"
                    print(f"[brain] {last}")
                    time.sleep(3)
            if i >= len(models) and not tried_discovery:
                tried_discovery = True
                extra = [m for m in _discover(e) if m not in models and (e["key"], m) not in _DEAD]
                if extra:
                    print(f"[brain] {e['provider']}: trying discovered models {extra}")
                    models += extra
    raise ProError(last or "no provider answered")


def parse_json(text: str) -> dict:
    t = text.strip()
    t = re.sub(r"^```(?:json)?\s*|\s*```$", "", t).strip()
    try:
        return json.loads(t)
    except Exception:  # noqa: BLE001
        pass
    # first balanced {...} block
    start = t.find("{")
    while start >= 0:
        depth, in_str, esc = 0, False, False
        for j in range(start, len(t)):
            c = t[j]
            if in_str:
                if esc:
                    esc = False
                elif c == "\\":
                    esc = True
                elif c == '"':
                    in_str = False
            elif c == '"':
                in_str = True
            elif c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    try:
                        return json.loads(t[start:j + 1])
                    except Exception:  # noqa: BLE001
                        break
        start = t.find("{", start + 1)
    raise ValueError("no JSON object in answer")


def chat_json(system: str, prompt: str, images=None, max_tokens: int = 32000, fast: bool = False) -> dict:
    sys2 = system + "\nAnswer with ONE valid JSON object only — no prose, no markdown fences."
    msgs = [{"role": "user", "text": prompt, "images": images or []}]
    text, _ = chat(sys2, msgs, max_tokens=max_tokens, fast=fast, need_vision=bool(images))
    try:
        return parse_json(text)
    except ValueError:
        msgs += [{"role": "assistant", "text": text[:4000]},
                 {"role": "user", "text": "That was not valid JSON. Re-send the SAME answer as one valid JSON object only."}]
        text, _ = chat(sys2, msgs, max_tokens=max_tokens, fast=fast)
        return parse_json(text)
