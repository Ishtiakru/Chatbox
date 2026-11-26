#!/usr/bin/env python3
"""Saira coding agent — turns the admin's natural-language request into a real
code change in this repository, compiles it, and fixes its own compile errors.

Flow (all inside GitHub Actions, triggered from the app by the admin):
  1. index the Android sources, ask Gemini which files the change needs
  2. send those files, receive search/replace edits (JSON), apply them
  3. ./gradlew :app:compileDebugKotlin — on errors feed them back (max 3 rounds)
  4. the workflow then runs unit tests + a release build; only a fully green
     change is pushed to main and released (the app then self-updates).

Outputs: GITHUB_OUTPUT changed=true|false ; SAIRA annotations for the app.
Keys come from the AI_KEY_POOL secret and are never printed.
"""
import base64
import json
import os
import random
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = os.getcwd()
BASE = "https://generativelanguage.googleapis.com/v1beta/models"
STRONG = ["gemini-3.8-flash", "gemini-3.5-flash", "gemini-flash-latest"]
LITE = ["gemini-3.5-flash-lite", "gemini-flash-lite-latest"]
ALLOWED_PREFIXES = ("app/src/main/java/", "app/src/main/res/", "app/src/main/AndroidManifest.xml")
FORBIDDEN = ("google-services.json", "saira_pool.dat", "firebase_applet_config.xml")
MAX_FIX_ROUNDS = 3


def notice(msg: str) -> None:
    clean = str(msg).replace("%", "%25").replace("\r", "%0D").replace("\n", " ;; ")
    print(f"::notice title=SAIRA::{clean[:900]}")
    print(msg)


def set_output(name: str, value: str) -> None:
    path = os.environ.get("GITHUB_OUTPUT")
    if path:
        with open(path, "a", encoding="utf-8") as fh:
            fh.write(f"{name}={value}\n")


def load_keys() -> list[str]:
    raw = os.environ.get("AI_KEY_POOL") or ""
    text = raw
    if raw.strip() and not re.search(r"AQ\.|AIza", raw):
        text = base64.b64decode(re.sub(r"\s+", "", raw)).decode()
    keys = []
    for line in text.splitlines():
        k = line.strip()
        if k and not k.startswith("#") and k not in keys:
            keys.append(k)
    return keys


KEYS = load_keys()


DEAD_MODELS: set[str] = set()


def _call(model: str, key: str, body: bytes, timeout: int) -> dict:
    req = urllib.request.Request(
        f"{BASE}/{model}:generateContent?key={key}", data=body, method="POST",
        headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        obj = json.loads(r.read().decode())
    cands = obj.get("candidates") or []
    if not cands:
        raise ValueError("no candidates")
    parts = (cands[0].get("content") or {}).get("parts") or []
    text = "".join(p.get("text", "") for p in parts if not p.get("thought")).strip()
    text = re.sub(r"^```(?:json)?|```$", "", text).strip()
    try:
        return json.loads(text)
    except Exception as exc:  # noqa: BLE001
        raise ValueError(f"bad json (finish={cands[0].get('finishReason')})") from exc


def gemini(system: str, prompt: str, max_tokens: int = 32768) -> dict:
    """JSON-mode call, raced for speed: every strong model + one lite model are asked
    at the same time on different keys. The first strong answer wins; a lite answer
    is used if no strong model answers within a short grace period. Models that
    return 404 are skipped for the rest of the run."""
    import concurrent.futures as cf
    if not KEYS:
        raise RuntimeError("AI_KEY_POOL empty")
    body = json.dumps({
        "system_instruction": {"parts": [{"text": system}]},
        "contents": [{"role": "user", "parts": [{"text": prompt}]}],
        "generationConfig": {
            "maxOutputTokens": max_tokens,
            "temperature": 0.2,
            "responseMimeType": "application/json",
        },
    }).encode()
    last = ""
    for wave in range(3):
        strong = [m for m in STRONG if m not in DEAD_MODELS]
        lite = [m for m in LITE if m not in DEAD_MODELS]
        models = strong + lite[:1 if wave == 0 else 2]
        keys = random.sample(KEYS, len(KEYS))
        jobs = [(m, keys[i % len(keys)]) for i, m in enumerate(models)]
        t0 = time.time()
        lite_answer = None
        pool = cf.ThreadPoolExecutor(max_workers=len(jobs))
        futs = {pool.submit(_call, m, k, body, 150): m for m, k in jobs}
        pending = set(futs)
        try:
            while pending:
                # After a lite answer, wait at most 25 s more for a stronger model.
                timeout = None if lite_answer is None else max(0.0, 25 - (time.time() - lite_answer[1]))
                done, pending = cf.wait(pending, timeout=timeout, return_when=cf.FIRST_COMPLETED)
                if not done:
                    break
                for f in done:
                    m = futs[f]
                    try:
                        out = f.result()
                    except urllib.error.HTTPError as e:
                        last = f"{m} HTTP {e.code}"
                        if e.code == 404:
                            DEAD_MODELS.add(m)
                        continue
                    except Exception as e:  # noqa: BLE001
                        last = f"{m} {type(e).__name__}: {str(e)[:80]}"
                        continue
                    if m in STRONG:
                        print(f"[gemini] model={m} ok in {time.time() - t0:.0f}s (wave {wave + 1})")
                        return out
                    if lite_answer is None:
                        lite_answer = (out, time.time(), m)
            if lite_answer is not None:
                print(f"[gemini] model={lite_answer[2]} (lite) used after {time.time() - t0:.0f}s")
                return lite_answer[0]
        finally:
            pool.shutdown(wait=False, cancel_futures=True)
        print(f"[gemini] wave {wave + 1} failed: {last}")
        time.sleep(5)
    raise RuntimeError(f"AI did not answer: {last}")


DECL = re.compile(r"^\s*(?:@\w+\s+)*(?:private |internal |public |protected )?(?:override )?(?:suspend )?"
                  r"(?:data class|enum class|sealed class|class|object|interface|fun)\s+([A-Za-z_][\w.<>]*)")


def index() -> str:
    lines = []
    for base in ("app/src/main/java", "app/src/main/res/values"):
        for dp, _, files in os.walk(base):
            for f in sorted(files):
                p = os.path.join(dp, f)
                if not f.endswith((".kt", ".xml")):
                    continue
                try:
                    src = open(p, encoding="utf-8").read().splitlines()
                except Exception:  # noqa: BLE001
                    continue
                names = []
                if f.endswith(".kt"):
                    for ln in src:
                        m = DECL.match(ln)
                        if m and m.group(1) not in names:
                            names.append(m.group(1))
                lines.append(f"{p} ({len(src)} lines): {', '.join(names[:45])}")
    lines.append("app/src/main/AndroidManifest.xml")
    return "\n".join(lines)


def read(p: str) -> str:
    return open(os.path.join(ROOT, p), encoding="utf-8").read()


def safe_path(p: str) -> bool:
    p = p.strip().lstrip("./")
    return (".." not in p and p.startswith(ALLOWED_PREFIXES)
            and not any(f in p for f in FORBIDDEN))


def numbered(p: str) -> str:
    return f"=== FILE: {p} ===\n{read(p)}\n=== END {p} ==="


EDIT_RULES = """
Return ONLY JSON:
{"summary_bn": "<1-2 sentence Bengali summary of what you changed>",
 "edits": [{"path": "<repo path>", "search": "<exact existing text>", "replace": "<new text>"}],
 "new_files": [{"path": "<repo path>", "content": "<full file content>"}]}
Rules:
- "search" must be copied EXACTLY (same whitespace/indentation) from the given file and must occur exactly ONCE. Keep it short (3-15 lines) but unique.
- Make the smallest correct change. Keep all existing behaviour that the admin did not ask to change.
- The code must compile: Kotlin 2.2, Jetpack Compose Material3, minSdk 26. Add every import you use. Do not invent library APIs or new Gradle dependencies — only use what the project already imports.
- Only paths under app/src/main/java/, app/src/main/res/ or app/src/main/AndroidManifest.xml.
- User-facing text may be Bengali.
"""


def apply_changes(change: dict) -> list[str]:
    problems = []
    for nf in change.get("new_files") or []:
        p = (nf.get("path") or "").strip()
        if not safe_path(p):
            problems.append(f"forbidden path {p}")
            continue
        full = os.path.join(ROOT, p)
        if os.path.exists(full):
            problems.append(f"{p} already exists — use edits instead")
            continue
        os.makedirs(os.path.dirname(full), exist_ok=True)
        open(full, "w", encoding="utf-8").write(nf.get("content") or "")
        print(f"[apply] new file {p}")
    for ed in change.get("edits") or []:
        p = (ed.get("path") or "").strip()
        search = ed.get("search") or ""
        replace = ed.get("replace") if ed.get("replace") is not None else ""
        if not safe_path(p) or not os.path.exists(os.path.join(ROOT, p)):
            problems.append(f"bad path {p}")
            continue
        src = read(p)
        n = src.count(search) if search else 0
        if n == 1:
            src = src.replace(search, replace)
        else:
            # tolerate trailing-whitespace / indentation drift
            pat = r"\n".join(r"[ \t]*" + re.escape(l.strip()) + r"[ \t]*" for l in search.strip("\n").split("\n"))
            ms = list(re.finditer(pat, src))
            if len(ms) == 1:
                m = ms[0]
                src = src[:m.start()] + replace.rstrip("\n") + src[m.end():]
            else:
                problems.append(f"search text not found exactly once in {p} (found {n}): {search[:160]!r}")
                continue
        open(os.path.join(ROOT, p), "w", encoding="utf-8").write(src)
        print(f"[apply] edit {p}")
    return problems


WARMUP: "subprocess.Popen | None" = None


def start_warmup() -> None:
    """Compile the untouched project on a Gradle daemon while Gemini is thinking, so the
    real compile after the edits is incremental (seconds instead of minutes)."""
    global WARMUP
    try:
        WARMUP = subprocess.Popen(["./gradlew", ":app:compileDebugKotlin", "-q"], cwd=ROOT,
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except Exception as exc:  # noqa: BLE001
        print(f"[agent] warm-up not started: {exc}")


def wait_warmup() -> None:
    global WARMUP
    if WARMUP is not None:
        t0 = time.time()
        try:
            WARMUP.wait(timeout=1200)
        except Exception:  # noqa: BLE001
            WARMUP.kill()
        print(f"[agent] warm-up finished (+{time.time() - t0:.0f}s wait)")
        WARMUP = None


def compile_errors() -> list[str]:
    wait_warmup()
    proc = subprocess.run(
        ["./gradlew", ":app:compileDebugKotlin", "-q"],
        cwd=ROOT, capture_output=True, text=True, timeout=1500)
    out = proc.stdout + "\n" + proc.stderr
    if proc.returncode == 0:
        return []
    errs = [l for l in out.splitlines() if l.startswith("e: ")]
    if not errs:
        errs = [l for l in out.splitlines() if "error" in l.lower() or "What went wrong" in l][:20] or [out[-1500:]]
    return errs[:25]


def files_from_errors(errs: list[str]) -> list[str]:
    found = []
    for e in errs:
        m = re.search(r"file://(\S+?\.kt)", e) or re.search(r"(app/src/main/\S+?\.kt)", e)
        if m:
            p = os.path.relpath(m.group(1), ROOT) if m.group(1).startswith("/") else m.group(1)
            if p not in found and os.path.exists(os.path.join(ROOT, p)):
                found.append(p)
    return found[:6]


ASSET_DIR = "app/src/main/res/drawable-nodpi"
ASSET_URL = re.compile(r"^https://files\.catbox\.moe/[A-Za-z0-9._-]+$")


def fetch_assets() -> list[str]:
    """Download the admin's attached images (uploaded by the app) as drawable resources."""
    urls = (os.environ.get("ASSETS") or "").split()
    tag = re.sub(r"[^a-z0-9]", "", (os.environ.get("REQUEST_ID") or "").lower())[-10:] or str(int(time.time()))
    names = []
    for i, url in enumerate(urls[:4]):
        if not ASSET_URL.match(url):
            print(f"[agent] skip asset url {url[:80]}")
            continue
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Saira agent)"})
            data = urllib.request.urlopen(req, timeout=90).read()
        except Exception as exc:  # noqa: BLE001
            print(f"[agent] asset download failed: {exc}")
            continue
        if data[:8] == b"\x89PNG\r\n\x1a\n":
            ext = "png"
        elif data[:3] == b"\xff\xd8\xff":
            ext = "jpg"
        else:
            print("[agent] asset is not png/jpg, skipped")
            continue
        name = f"saira_asset_{tag}_{i + 1}"
        os.makedirs(os.path.join(ROOT, ASSET_DIR), exist_ok=True)
        with open(os.path.join(ROOT, ASSET_DIR, f"{name}.{ext}"), "wb") as fh:
            fh.write(data)
        names.append(name)
        print(f"[agent] asset {name}.{ext} ({len(data) // 1024} KB)")
    return names


def main() -> int:
    instruction = (os.environ.get("INSTRUCTION") or "").strip()
    if not instruction:
        notice("কোনো নির্দেশ পাইনি।")
        set_output("changed", "false")
        return 1
    start_warmup()
    assets = fetch_assets()
    if assets:
        refs = ", ".join(f"R.drawable.{n}" for n in assets)
        instruction += (
            f"\n\n[The admin attached {len(assets)} image(s). They are ALREADY added to the project as "
            f"drawable resources: {refs} (import com.example.R). Use them where the request says, e.g. "
            "Image(painter = painterResource(R.drawable.NAME), contentDescription = ...). The app's current logo is "
            "R.drawable.chatbox_logo_lavender, used in ui/ChatboxRoot.kt, ui/components/ChatboxCommon.kt and "
            "ui/screens/AuthLoginScreen.kt — to replace the logo everywhere, change those painterResource calls. "
            "Launcher icon = res/mipmap-anydpi-v26/ic_launcher.xml + ic_launcher_round.xml (adaptive icon); to use "
            "the image there, set the foreground to an <inset android:inset=\"22%\" android:drawable=\"@drawable/NAME\"/>.]"
        )
    print(f"[agent] instruction: {instruction[:300]}")

    system = ("You are Saira, a senior Android/Kotlin engineer working on the 'Chatbox' app "
              "(Jetpack Compose, Firebase Firestore/Auth, package com.example). You change the real "
              "source code to fulfil the admin's request, carefully and minimally.")

    plan = gemini(system, f"""Admin request (Bengali/Banglish/English):
<<<{instruction}>>>

Project source index (path, line count, declarations):
{index()}

Decide which existing files must be read/edited (max 8, most relevant first) to implement the request.
Return ONLY JSON: {{"feasible": true|false, "reason_bn": "<why not, if not feasible>", "files": ["path", ...], "plan": "<short English plan>"}}
Not feasible = needs a paid service, a server, new third-party libraries, or is harmful.""", max_tokens=4096)

    if not plan.get("feasible", True):
        notice("এই পরিবর্তনটা অ্যাপের কোডে করা সম্ভব নয়: " + str(plan.get("reason_bn") or ""))
        set_output("changed", "false")
        return 1
    files = [f for f in (plan.get("files") or []) if safe_path(f) and os.path.exists(os.path.join(ROOT, f))][:8]
    print(f"[agent] plan: {plan.get('plan')} files={files}")

    ctx = "\n\n".join(numbered(f) for f in files)
    if len(ctx) > 600_000:
        ctx = ctx[:600_000]
    change = gemini(system, f"""Admin request:
<<<{instruction}>>>

Plan: {plan.get('plan')}

Current files:
{ctx}

{EDIT_RULES}""")

    wait_warmup()  # never edit files while the warm-up compile is reading them
    problems = apply_changes(change)
    if problems:
        retry_files = sorted({p.split(" in ")[-1].split(" ")[0] for p in problems if " in " in p} & set(files)) or files
        change2 = gemini(system, f"""Some of your edits could not be applied:
{chr(10).join(problems)}

Re-send ONLY the failed edits, with "search" copied exactly from these CURRENT files:
{chr(10).join(numbered(f) for f in retry_files)}

{EDIT_RULES}""")
        problems = apply_changes(change2)
        if problems:
            print("[agent] still unapplied:", problems)

    diff = subprocess.run(["git", "status", "--porcelain"], cwd=ROOT, capture_output=True, text=True).stdout
    changed = [l for l in diff.splitlines() if "google-services.json" not in l and "drawable-nodpi" not in l]
    if not changed:
        notice("কোনো পরিবর্তন তৈরি করা যায়নি। নির্দেশটা আরেকটু নির্দিষ্ট করে বলুন।")
        set_output("changed", "false")
        return 1

    for rnd in range(1, MAX_FIX_ROUNDS + 1):
        errs = compile_errors()
        if not errs:
            break
        print(f"[agent] compile round {rnd}: {len(errs)} errors")
        if rnd == MAX_FIX_ROUNDS:
            notice("কোড কম্পাইল হয়নি, তাই অ্যাপে কিছু বদলাইনি। প্রথম এরর: " + errs[0][:300])
            set_output("changed", "false")
            return 1
        bad = files_from_errors(errs) or files
        fix = gemini(system, f"""Your change for this request does not compile:
<<<{instruction}>>>

Compiler errors:
{chr(10).join(errs)}

Current files:
{chr(10).join(numbered(f) for f in bad)}

Fix the errors (keep the requested feature).
{EDIT_RULES}""")
        apply_changes(fix)

    summary = str(change.get("summary_bn") or plan.get("plan") or "অ্যাপ আপডেট করা হয়েছে")
    open("/tmp/agent_summary.txt", "w", encoding="utf-8").write(summary)
    notice("পরিবর্তন তৈরি ও কম্পাইল সফল: " + summary)
    set_output("changed", "true")
    return 0


if __name__ == "__main__":
    try:
        code = main()
    except Exception as exc:  # noqa: BLE001
        notice(f"Saira agent ব্যর্থ: {type(exc).__name__}: {str(exc)[:300]}")
        set_output("changed", "false")
        code = 1
    sys.stdout.flush()
    sys.stderr.flush()
    # Don't wait for slower losing AI requests still running in background threads.
    os._exit(code)
