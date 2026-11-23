import express from 'express';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import JSZip from 'jszip';
import { GoogleGenAI, ThinkingLevel, Type, type Content } from '@google/genai';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = 3000;
const MODEL_NAME = 'gemini-4-hydrogen';
const FALLBACK_MODEL_NAME = 'gemini-3.1-flash-lite';
const RELEASES_DIR = path.resolve(__dirname, 'releases');
const RELEASE_MANIFEST_PATH = path.join(RELEASES_DIR, 'release.json');

const BOOTSTRAPPED_ADMIN_EMAILS = new Set([
  'ishtiakahmedru@gmail.com',
  'ishtiakahmedus@gmail.com',
]);

function readFirebaseApiKey(): string {
  try {
    const raw = fs.readFileSync(path.resolve(__dirname, 'firebase-applet-config.json'), 'utf8');
    const parsed = JSON.parse(raw) as { apiKey?: string };
    return typeof parsed.apiKey === 'string' ? parsed.apiKey.trim() : '';
  } catch {
    return '';
  }
}

const FIREBASE_WEB_API_KEY = readFirebaseApiKey();

const CURATED_KNOWLEDGE = `
# Saira's curated app knowledge

Chatbox is a responsive messaging and companion web application built with React, TypeScript, Tailwind CSS, Express, Firebase Authentication (Google Sign-In), Cloud Firestore, Firebase Storage, and Gemini AI.

Features available in Chatbox:
- Lavender Login & Welcome Screen with 50+ world languages (including Bangla / বাংলা and English US), Google Sign-In, remember-me phone/account preference, and password reset helper.
- WhatsApp-style Profile & 6-Digit ID Code: Every user has a unique 6-digit Chatbox ID code, WhatsApp-style About status, and optional Phone Number. Friends can add each other using either a 6-digit ID code or phone number.
- Demo Conversations: Local sample conversations (Saira offline guide, Maya Rahman, Arif Hasan, Nora Ahmed, The good people) with global search and persistent browser history.
- Live Chats: Real-time direct messaging powered by Cloud Firestore. Users add friends by 6-digit ID, phone number, or UID, send/accept conversation requests, share compressed images and voice notes, close conversations, and block/unblock peers.
- Saira AI: Meta AI-inspired always-on conversational assistant with unlimited seamless session rollover, time-aware greetings, and Auto-Reply rule settings.
- Calls: Planned Voice and Video Calls screen.
- Admin Panel & QE Coder: Owner-only dashboard to control theme, menu position, custom dynamic menu bars via natural-language instructions, and package full app coding releases as ZIP files (replacing old ZIPs automatically) while showing a green Update button on users' home screens.
`.trim();

const CHAT_SYSTEM_INSTRUCTION = `
You are Saira Assistant (সায়রা অ্যাসিস্ট্যান্ট), a friendly, intelligent, always-on personal AI companion inside Chatbox.

Core Behavior Rules:
1. Never initiate a conversation before the user messages first. Only reply to messages sent to you.
2. Always respond naturally in the exact language/style the user writes in (Bengali, Banglish, or English).
3. Greeting Rule:
   - When the prompt marks this as the first message in a fresh conversation (not a continuation), begin warmly with the provided time-of-day greeting (Good Morning, Good Afternoon, or Good Evening — or natural bilingual greeting alongside Bengali).
   - When the user is ending the conversation or saying goodbye/thank you/done at night (isNightTime=true), gracefully wish them "Good Night" (e.g., "Good Night! শুভ রাত্রি।") as a warm closing.
4. Unlimited General Knowledge:
   - Answer ANY question or request the user brings (everyday help, study, technology, coding, writing, advice, general facts, or Chatbox app guidance) clearly, accurately, and warmly.
   - Format answers cleanly using short paragraphs, bullet points when helpful, and fenced code blocks for code.
5. Ground Chatbox-specific questions in this app summary when asked about Chatbox:
${CURATED_KNOWLEDGE}
`.trim();

const REPLY_SYSTEM_INSTRUCTION =
  'You are Saira Assistant writing concise, natural auto-replies on the user\'s behalf. ' +
  'Match the language of the incoming message (Bengali, Banglish, or English). ' +
  'Keep replies warm, polite, 1 to 3 sentences, and follow the exact instruction/context provided.';

const ADMIN_INTERPRETER_INSTRUCTION = `
You are Chatbox Admin App Controller. Convert the admin's natural-language request (in Bengali, Banglish, or English) into a structured JSON update plan for the live Chatbox app.

Available live changes you can return:
- theme: "default" or "lavender" (only if the admin asks to change theme/color)
- menuPosition: "bottom" or "top" (only if the admin asks to move the navigation menu bar to top or bottom)
- welcomeBanner: string up to 160 chars (only if the admin asks to set/update the welcome message)
- appTitle: string up to 40 chars (only if the admin asks to change the app header title)
- homeAnnouncement: string up to 240 chars (use "" to clear, only if the admin asks to show/clear an announcement banner on the home screen)
- addOrUpdateMenu: object { id, label, icon, title, body } when the admin wants to ADD or UPDATE a navigation menu bar / tab in the app:
  - id: lowercase alphanumeric/dash slug (2..24 chars, e.g. "notice-board", "rules", "offers")
  - label: short 1-2 word tab label (1..20 chars, e.g. "Notice", "Rules", "Updates")
  - icon: one of "sparkles" | "bell" | "megaphone" | "book" | "star" | "info" | "grid"
  - title: page heading (1..80 chars)
  - body: helpful multi-line details/content for that menu screen (1..900 chars)
- removeMenuId: string id of a custom menu to remove (only if the admin asks to delete/remove a custom menu)
- appUpdate: { active: boolean, version: string, note: string } (only if the admin asks to show/publish or hide/clear the green Update button)
- summary: concise 1-sentence confirmation in the same language the admin used (Bengali/Banglish/English).

Only include fields the admin asked to change. Keep all text clean, safe, and ready to display live to users.
`.trim();

function getConfiguredApiKeys(): string[] {
  const keys: string[] = [];
  const primary = process.env.GEMINI_API_KEY?.trim();
  if (primary && primary !== 'your_gemini_api_key_here') {
    keys.push(primary);
  }
  const backup = process.env.GEMINI_BACKUP_API_KEY?.trim();
  if (backup && backup !== 'your_gemini_api_key_here' && !keys.includes(backup)) {
    keys.push(backup);
  }
  const pool = process.env.GEMINI_API_KEYS?.split(',') ?? [];
  for (const raw of pool) {
    const trimmed = raw.trim();
    if (trimmed && trimmed !== 'your_gemini_api_key_here' && !keys.includes(trimmed)) {
      keys.push(trimmed);
    }
  }
  return keys;
}

function createAiClient(apiKey: string): GoogleGenAI {
  return new GoogleGenAI({
    apiKey,
    httpOptions: {
      headers: {
        'User-Agent': 'aistudio-build',
      },
    },
  });
}

function getAiClient(): GoogleGenAI | null {
  const keys = getConfiguredApiKeys();
  return keys.length > 0 ? createAiClient(keys[0]) : null;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function extractRawErrorText(error: unknown): string {
  if (error instanceof Error) return error.message;
  if (typeof error === 'string') return error;
  try {
    return JSON.stringify(error);
  } catch {
    return 'Unknown AI error';
  }
}

function tryUnwrapNestedMessage(raw: string): string {
  let current = raw.trim();
  for (let i = 0; i < 3; i += 1) {
    if (!current.startsWith('{')) break;
    try {
      const parsed = JSON.parse(current) as {
        message?: unknown;
        error?: { message?: unknown } | unknown;
      };
      if (typeof parsed.error === 'object' && parsed.error !== null && 'message' in parsed.error) {
        const inner = (parsed.error as { message?: unknown }).message;
        if (typeof inner === 'string' && inner.trim()) {
          current = inner.trim();
          continue;
        }
      }
      if (typeof parsed.message === 'string' && parsed.message.trim()) {
        current = parsed.message.trim();
        continue;
      }
      break;
    } catch {
      break;
    }
  }
  return current;
}

function extractGeminiErrorMessage(error: unknown): string {
  const raw = extractRawErrorText(error);
  const unwrapped = tryUnwrapNestedMessage(raw);
  const lower = unwrapped.toLowerCase();

  if (lower.includes('api_key_invalid') || lower.includes('api key not valid') || lower.includes('permission_denied')) {
    return 'Gemini API key is invalid or unauthorized. Check GEMINI_API_KEY in Settings > Secrets.';
  }
  if (lower.includes('503') || lower.includes('unavailable') || lower.includes('high demand')) {
    return 'Saira AI is temporarily busy due to high model demand. Retrying shortly…';
  }
  if (lower.includes('429') || lower.includes('resource_exhausted') || lower.includes('quota')) {
    return 'Saira AI reached a temporary rate limit. Automatically switching to backup capacity — please try again.';
  }
  return unwrapped || 'Saira AI is unavailable right now.';
}

function isTransientGeminiError(error: unknown): boolean {
  const raw = extractRawErrorText(error).toLowerCase();
  return (
    raw.includes('503') ||
    raw.includes('unavailable') ||
    raw.includes('high demand') ||
    raw.includes('429') ||
    raw.includes('resource_exhausted') ||
    raw.includes('quota') ||
    raw.includes('overloaded') ||
    raw.includes('timeout') ||
    raw.includes('timed out') ||
    raw.includes('econnreset')
  );
}

function withTimeout<T>(promise: Promise<T>, ms: number, label: string): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${label} timed out`)), ms);
    promise.then(
      (val) => {
        clearTimeout(timer);
        resolve(val);
      },
      (err) => {
        clearTimeout(timer);
        reject(err);
      }
    );
  });
}

function getModelThinkingConfig(model: string) {
  return model === MODEL_NAME
    ? { thinkingConfig: { thinkingLevel: ThinkingLevel.LOW } }
    : {};
}

async function generateTextWithFallback(
  contents: string | Content[],
  systemInstruction: string
): Promise<string> {
  const apiKeys = getConfiguredApiKeys();
  if (apiKeys.length === 0) {
    throw new Error('GEMINI_API_KEY missing');
  }

  const models = [MODEL_NAME, FALLBACK_MODEL_NAME];
  let lastError: unknown;

  for (const key of apiKeys) {
    const ai = createAiClient(key);
    for (const model of models) {
      try {
        const response = await withTimeout(
          ai.models.generateContent({
            model,
            contents,
            config: {
              systemInstruction,
              ...getModelThinkingConfig(model),
            },
          }),
          8000,
          model
        );
        const text = response.text?.trim();
        if (text) return text;
      } catch (error) {
        lastError = error;
        if (!isTransientGeminiError(error)) {
          throw error;
        }
      }
    }
  }
  throw lastError ?? new Error('Saira AI did not return a response.');
}

async function streamTextWithFallback(
  contents: Content[],
  systemInstruction: string,
  onText: (text: string) => void
): Promise<boolean> {
  const apiKeys = getConfiguredApiKeys();
  if (apiKeys.length === 0) {
    throw new Error('GEMINI_API_KEY missing');
  }

  const attempts: Array<{ apiKey: string; model: string; delayMs: number; firstChunkTimeoutMs: number }> = [];
  for (let i = 0; i < apiKeys.length; i += 1) {
    attempts.push({ apiKey: apiKeys[i], model: MODEL_NAME, delayMs: i === 0 ? 0 : 120, firstChunkTimeoutMs: 7000 });
    attempts.push({ apiKey: apiKeys[i], model: FALLBACK_MODEL_NAME, delayMs: 100, firstChunkTimeoutMs: 7000 });
  }
  attempts.push({ apiKey: apiKeys[0], model: FALLBACK_MODEL_NAME, delayMs: 250, firstChunkTimeoutMs: 9000 });

  let lastError: unknown;
  for (const attempt of attempts) {
    if (attempt.delayMs > 0) {
      await sleep(attempt.delayMs);
    }
    const ai = createAiClient(attempt.apiKey);
    let emittedAny = false;
    try {
      const stream = await withTimeout(
        ai.models.generateContentStream({
          model: attempt.model,
          contents,
          config: {
            systemInstruction,
            ...getModelThinkingConfig(attempt.model),
          },
        }),
        attempt.firstChunkTimeoutMs,
        attempt.model
      );
      const iterator = stream[Symbol.asyncIterator]();
      while (true) {
        const step = emittedAny
          ? await withTimeout(iterator.next(), 15000, `${attempt.model}-chunk`)
          : await withTimeout(iterator.next(), attempt.firstChunkTimeoutMs, attempt.model);
        if (step.done) break;
        const text = step.value.text;
        if (text) {
          emittedAny = true;
          onText(text);
        }
      }
      if (emittedAny) {
        return true;
      }
    } catch (error) {
      lastError = error;
      if (emittedAny || !isTransientGeminiError(error)) {
        throw error;
      }
    }
  }
  if (lastError) {
    throw lastError;
  }
  return false;
}

function getTimeGreeting(hourOfDay?: number): string {
  const hour =
    typeof hourOfDay === 'number' && hourOfDay >= 0 && hourOfDay <= 23
      ? hourOfDay
      : new Date().getHours();
  if (hour >= 5 && hour <= 11) return 'Good Morning';
  if (hour >= 12 && hour <= 16) return 'Good Afternoon';
  return 'Good Evening';
}

function isNightTime(hourOfDay?: number): boolean {
  const hour =
    typeof hourOfDay === 'number' && hourOfDay >= 0 && hourOfDay <= 23
      ? hourOfDay
      : new Date().getHours();
  return hour >= 21 || hour < 5;
}

function containsBengali(text: string): boolean {
  return /[\u0980-\u09FF]/.test(text);
}

async function verifyFirebaseBearerUser(
  authHeader: string | undefined
): Promise<{ uid: string; email: string } | null> {
  if (!authHeader || !authHeader.startsWith('Bearer ') || !FIREBASE_WEB_API_KEY) {
    return null;
  }
  const idToken = authHeader.slice(7).trim();
  if (!idToken) return null;

  try {
    const resp = await fetch(
      `https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=${encodeURIComponent(FIREBASE_WEB_API_KEY)}`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ idToken }),
      }
    );
    if (!resp.ok) return null;
    const data = (await resp.json()) as {
      users?: Array<{ localId?: string; email?: string; emailVerified?: boolean }>;
    };
    const user = data.users?.[0];
    if (!user?.localId || !user.email || !user.emailVerified) {
      return null;
    }
    return { uid: user.localId, email: user.email.trim().toLowerCase() };
  } catch {
    return null;
  }
}

interface StoredQeRelease {
  version: string;
  filename: string;
  sizeBytes: number;
  fileCount: number;
  note: string;
  createdAt: string;
  downloadUrl: string;
}

function ensureReleasesDir(): void {
  if (!fs.existsSync(RELEASES_DIR)) {
    fs.mkdirSync(RELEASES_DIR, { recursive: true });
  }
}

function readCurrentReleaseManifest(): StoredQeRelease | null {
  try {
    if (!fs.existsSync(RELEASE_MANIFEST_PATH)) return null;
    const raw = fs.readFileSync(RELEASE_MANIFEST_PATH, 'utf8');
    const parsed = JSON.parse(raw) as StoredQeRelease;
    if (!parsed.filename) return null;
    const zipPath = path.join(RELEASES_DIR, path.basename(parsed.filename));
    if (!fs.existsSync(zipPath)) return null;
    return parsed;
  } catch {
    return null;
  }
}

function addProjectFilesToZip(zip: JSZip, rootDir: string): number {
  const ALLOWED_ROOT_DIRS = new Set(['src', 'public', '.github', 'scripts']);
  const ALLOWED_ROOT_FILES = new Set([
    'package.json',
    'package-lock.json',
    'server.ts',
    'index.html',
    'tsconfig.json',
    'vite.config.ts',
    'eslint.config.js',
    'firestore.rules',
    'firebase-blueprint.json',
    'firebase-applet-config.json',
    'metadata.json',
    'APK_GITHUB_RELEASE_GUIDE.md',
    '.env.example',
  ]);

  let count = 0;

  const walk = (absDir: string, relBase: string) => {
    const entries = fs.readdirSync(absDir, { withFileTypes: true });
    for (const entry of entries) {
      const absPath = path.join(absDir, entry.name);
      const relPath = relBase ? `${relBase}/${entry.name}` : entry.name;
      if (entry.isDirectory()) {
        if (entry.name === 'node_modules' || entry.name === '.git' || entry.name === 'dist' || entry.name === 'releases') {
          continue;
        }
        walk(absPath, relPath);
      } else if (entry.isFile()) {
        const stat = fs.statSync(absPath);
        if (stat.size > 5 * 1024 * 1024) continue;
        zip.file(relPath, fs.readFileSync(absPath));
        count += 1;
      }
    }
  };

  for (const fileName of ALLOWED_ROOT_FILES) {
    const absPath = path.join(rootDir, fileName);
    if (fs.existsSync(absPath) && fs.statSync(absPath).isFile()) {
      zip.file(fileName, fs.readFileSync(absPath));
      count += 1;
    }
  }

  for (const dirName of ALLOWED_ROOT_DIRS) {
    const absDir = path.join(rootDir, dirName);
    if (fs.existsSync(absDir) && fs.statSync(absDir).isDirectory()) {
      walk(absDir, dirName);
    }
  }

  return count;
}

async function startServer() {
  const app = express();
  app.use((_req, res, next) => {
    // Allow Firebase Google Sign-In popup windows to keep opener communication intact
    res.setHeader('Cross-Origin-Opener-Policy', 'same-origin-allow-popups');
    next();
  });
  app.use(express.json({ limit: '1mb' }));

  app.get('/.well-known/assetlinks.json', (_req, res) => {
    const packageName = (process.env.ANDROID_PACKAGE_NAME || 'com.chatbox.saira').trim();
    const rawFingerprints = (process.env.ANDROID_SHA256_FINGERPRINT || '')
      .split(',')
      .map((s) => s.trim().toUpperCase())
      .filter((s) => /^[0-9A-F]{2}(:[0-9A-F]{2}){31}$/.test(s));

    if (rawFingerprints.length === 0) {
      res.json([]);
      return;
    }

    res.json([
      {
        relation: ['delegate_permission/common.handle_all_urls'],
        target: {
          namespace: 'android_app',
          package_name: packageName,
          sha256_cert_fingerprints: rawFingerprints,
        },
      },
    ]);
  });

  app.get('/api/health', (_req, res) => {
    res.json({ ok: true });
  });

  app.post('/api/saira/chat', async (req, res) => {
    const body = req.body as {
      question?: unknown;
      history?: Array<{ question?: unknown; answer?: unknown }>;
      isFirstTurn?: unknown;
      isContinuation?: unknown;
      hourOfDay?: unknown;
    };

    const question = typeof body.question === 'string' ? body.question.trim() : '';
    if (!question || question.length > 2000) {
      res.status(400).json({ error: 'Message must be between 1 and 2000 characters.' });
      return;
    }

    const ai = getAiClient();
    if (!ai) {
      res.status(503).json({
        error: 'Saira AI service is not configured on the server (missing GEMINI_API_KEY).',
      });
      return;
    }

    const historyItems = Array.isArray(body.history) ? body.history.slice(-16) : [];
    const isContinuation = Boolean(body.isContinuation);
    const isFirstTurn = Boolean(body.isFirstTurn) && historyItems.length === 0 && !isContinuation;
    const hourOfDay = typeof body.hourOfDay === 'number' ? body.hourOfDay : undefined;
    const greeting = getTimeGreeting(hourOfDay);
    const night = isNightTime(hourOfDay);

    let contextualPrompt = `[Context: localTimeGreeting="${greeting}", isNightTime=${night}, isFirstTurn=${isFirstTurn}, isContinuation=${isContinuation}]`;
    if (isFirstTurn) {
      contextualPrompt += ` Start your reply warmly with "${greeting}" suited to the user's language.`;
    }
    if (night) {
      contextualPrompt +=
        ' If the user is ending the conversation or saying goodbye/thanks/goodnight, include a warm Good Night farewell.';
    }
    contextualPrompt += `\nUser message: ${question}`;

    const contents: Content[] = [];
    for (const item of historyItems) {
      const q = typeof item.question === 'string' ? item.question.trim() : '';
      const a = typeof item.answer === 'string' ? item.answer.trim() : '';
      if (q) {
        contents.push({ role: 'user', parts: [{ text: q }] });
      }
      if (a) {
        contents.push({ role: 'model', parts: [{ text: a }] });
      }
    }
    contents.push({ role: 'user', parts: [{ text: contextualPrompt }] });

    res.setHeader('Content-Type', 'text/event-stream; charset=utf-8');
    res.setHeader('Cache-Control', 'no-cache, no-transform');
    res.setHeader('Connection', 'keep-alive');
    res.setHeader('X-Accel-Buffering', 'no');
    res.flushHeaders?.();
    res.write(': stream-start\n\n');

    let closed = false;
    const heartbeat = setInterval(() => {
      if (!closed && !res.writableEnded) {
        res.write(': keepalive\n\n');
      }
    }, 3000);

    res.on('close', () => {
      closed = true;
      clearInterval(heartbeat);
    });

    try {
      const produced = await streamTextWithFallback(
        contents,
        CHAT_SYSTEM_INSTRUCTION,
        (text) => {
          if (!closed && !res.writableEnded) {
            res.write(`data: ${JSON.stringify({ text })}\n\n`);
          }
        }
      );
      clearInterval(heartbeat);
      if (!closed && !res.writableEnded) {
        if (!produced) {
          res.write(`data: ${JSON.stringify({ error: 'Saira returned an empty response. Please try again.' })}\n\n`);
        } else {
          res.write(`data: ${JSON.stringify({ done: true })}\n\n`);
        }
        res.end();
      }
    } catch (error) {
      clearInterval(heartbeat);
      if (!closed && !res.writableEnded) {
        const message = extractGeminiErrorMessage(error);
        res.write(`data: ${JSON.stringify({ error: message })}\n\n`);
        res.end();
      }
    }
  });

  app.post('/api/saira/companion', async (req, res) => {
    const body = req.body as {
      userName?: unknown;
      peerName?: unknown;
      userQuestion?: unknown;
      hourOfDay?: unknown;
    };
    const cleanUser =
      typeof body.userName === 'string' && body.userName.trim()
        ? body.userName.trim().slice(0, 80)
        : 'Friend';
    const cleanPeer =
      typeof body.peerName === 'string' && body.peerName.trim()
        ? body.peerName.trim().slice(0, 80)
        : 'your friend';
    const userQuestion =
      typeof body.userQuestion === 'string' ? body.userQuestion.trim().slice(0, 1000) : '';
    const hourOfDay = typeof body.hourOfDay === 'number' ? body.hourOfDay : undefined;
    const greeting = getTimeGreeting(hourOfDay);
    const night = isNightTime(hourOfDay);

    if (!userQuestion) {
      res.status(400).json({ error: 'Question cannot be empty.' });
      return;
    }

    const ai = getAiClient();
    if (ai) {
      try {
        const prompt = [
          `User "${cleanUser}" is currently waiting for "${cleanPeer}" in Chatbox and just wrote to you (Saira Assistant):`,
          `"${userQuestion}"`,
          `Current greeting: "${greeting}", isNightTime=${night}.`,
          `Reply directly and helpfully to "${cleanUser}" in the same language they wrote in.`,
        ].join('\n');

        const reply = await generateTextWithFallback(prompt, REPLY_SYSTEM_INSTRUCTION);
        if (reply) {
          res.json({ reply });
          return;
        }
      } catch {
        // Fall back to deterministic friendly message below
      }
    }

    const fallback = containsBengali(userQuestion)
      ? `${greeting} ${cleanUser}! আমি সায়রা অ্যাসিস্ট্যান্ট। "${userQuestion}" সম্পর্কে এখনই পূর্ণ উত্তর দিতে পারিনি, একটু পরে আবার চেষ্টা করুন।`
      : `${greeting} ${cleanUser}! I'm Saira Assistant. Please try asking again in a moment.`;
    res.json({ reply: fallback });
  });

  app.post('/api/saira/auto-reply', async (req, res) => {
    const body = req.body as {
      senderName?: unknown;
      peerName?: unknown;
      lastMessageText?: unknown;
      isSenderWaitingOnPeer?: unknown;
      hourOfDay?: unknown;
    };
    const cleanSender =
      typeof body.senderName === 'string' && body.senderName.trim()
        ? body.senderName.trim().slice(0, 80)
        : 'Friend';
    const cleanPeer =
      typeof body.peerName === 'string' && body.peerName.trim()
        ? body.peerName.trim().slice(0, 80)
        : 'your friend';
    const lastMessageText =
      typeof body.lastMessageText === 'string' ? body.lastMessageText.trim().slice(0, 1000) : '';
    const isSenderWaitingOnPeer = Boolean(body.isSenderWaitingOnPeer);
    const hourOfDay = typeof body.hourOfDay === 'number' ? body.hourOfDay : undefined;
    const greeting = getTimeGreeting(hourOfDay);
    const night = isNightTime(hourOfDay);
    const useBengali = containsBengali(lastMessageText) || containsBengali(cleanSender);

    const ai = getAiClient();
    if (ai) {
      try {
        const prompt = isSenderWaitingOnPeer
          ? [
              `In Chatbox, "${cleanSender}" sent "${lastMessageText}" to "${cleanPeer}", and "${cleanPeer}" has not replied for 1 minute (likely offline or busy).`,
              `Write a friendly 1-2 sentence message as Saira Assistant directly to "${cleanSender}":`,
              `- Start with "${greeting} ${cleanSender}"`,
              `- Kindly let ${cleanSender} know that ${cleanPeer} hasn't replied for 1 minute / may be offline right now, and ask how you (Saira Assistant) can help in the meantime.`,
              `- Match language: ${useBengali ? 'Bengali (natural conversational Bangla)' : "English or user's message language"}.`,
              `- isNightTime=${night}`,
            ].join('\n')
          : [
              `In Chatbox, "${cleanSender}" sent "${lastMessageText}" to "${cleanPeer}", and "${cleanPeer}" has been away for 1 minute.`,
              `Write a polite 1-2 sentence auto-reply as Saira Assistant on ${cleanPeer}'s behalf:`,
              `- Start with "${greeting} ${cleanSender}"`,
              `- Let ${cleanSender} know ${cleanPeer} is away right now and will reply as soon as possible.`,
              `- Match language: ${useBengali ? 'Bengali (natural conversational Bangla)' : "English or user's message language"}.`,
            ].join('\n');

        const reply = await generateTextWithFallback(prompt, REPLY_SYSTEM_INSTRUCTION);
        if (reply) {
          res.json({ reply });
          return;
        }
      } catch {
        // Fall back to deterministic friendly message below
      }
    }

    const fallback = isSenderWaitingOnPeer
      ? useBengali
        ? `${greeting} ${cleanSender}, কেমন আছো? ${cleanPeer} ১ মিনিট ধরে রিপ্লাই দেয়নি বা এখন অফলাইনে আছে। অপেক্ষা করার সময় আমি সায়রা অ্যাসিস্ট্যান্ট তোমাকে যেকোনো তথ্যে সাহায্য করতে পারি!`
        : `${greeting} ${cleanSender}, how are you? ${cleanPeer} hasn't replied for 1 minute and may be offline right now. I'm Saira Assistant — feel free to ask me anything while you wait!`
      : useBengali
        ? `[Saira Assistant] ${greeting} ${cleanSender}! ${cleanPeer} এই মুহূর্তে ব্যস্ত বা দূরে আছেন। তিনি ফিরে এলেই উত্তর দেবেন।`
        : `[Saira Assistant] ${greeting} ${cleanSender}! ${cleanPeer} is currently away and will reply as soon as they return.`;

    res.json({ reply: fallback });
  });

  app.post('/api/admin/interpret-update', async (req, res) => {
    const verifiedUser = await verifyFirebaseBearerUser(req.headers.authorization);
    if (!verifiedUser) {
      res.status(401).json({ error: 'Google sign-in authorization required.' });
      return;
    }

    const body = req.body as {
      instruction?: unknown;
      currentMenus?: Array<{ id?: string; label?: string }>;
    };
    const instruction = typeof body.instruction === 'string' ? body.instruction.trim() : '';
    if (!instruction || instruction.length > 1500) {
      res.status(400).json({ error: 'Instruction must be between 1 and 1500 characters.' });
      return;
    }

    const existingMenusSummary = Array.isArray(body.currentMenus)
      ? body.currentMenus
          .slice(0, 5)
          .map((m) => `${m.id ?? ''}(${m.label ?? ''})`)
          .join(', ')
      : 'none';

    const apiKeys = getConfiguredApiKeys();
    if (apiKeys.length > 0) {
      for (const key of apiKeys) {
        for (const model of [MODEL_NAME, FALLBACK_MODEL_NAME]) {
          try {
            const ai = createAiClient(key);
            const response = await withTimeout(
              ai.models.generateContent({
                model,
                contents: `Current custom menus: [${existingMenusSummary || 'none'}].\nAdmin instruction: ${instruction}`,
                config: {
                  systemInstruction: ADMIN_INTERPRETER_INSTRUCTION,
                  responseMimeType: 'application/json',
                  ...getModelThinkingConfig(model),
                  responseSchema: {
                    type: Type.OBJECT,
                    properties: {
                      summary: { type: Type.STRING },
                      theme: { type: Type.STRING },
                      menuPosition: { type: Type.STRING },
                      welcomeBanner: { type: Type.STRING },
                      appTitle: { type: Type.STRING },
                      homeAnnouncement: { type: Type.STRING },
                      removeMenuId: { type: Type.STRING },
                      addOrUpdateMenu: {
                        type: Type.OBJECT,
                        properties: {
                          id: { type: Type.STRING },
                          label: { type: Type.STRING },
                          icon: { type: Type.STRING },
                          title: { type: Type.STRING },
                          body: { type: Type.STRING },
                        },
                      },
                      appUpdate: {
                        type: Type.OBJECT,
                        properties: {
                          active: { type: Type.BOOLEAN },
                          version: { type: Type.STRING },
                          note: { type: Type.STRING },
                        },
                      },
                    },
                    required: ['summary'],
                  },
                },
              }),
              10000,
              model
            );
            const rawText = (response.text ?? '{}').trim();
            const parsed = JSON.parse(rawText) as Record<string, unknown>;
            res.json({ plan: parsed });
            return;
          } catch (error) {
            if (!isTransientGeminiError(error)) {
              break;
            }
          }
        }
      }
    }

    // Deterministic fallback when AI is unavailable
    const lower = instruction.toLowerCase();
    const fallbackPlan: Record<string, unknown> = {
      summary: 'Prepared update from your instruction.',
    };
    if (lower.includes('lavender') || instruction.includes('ল্যাভেন্ডার')) {
      fallbackPlan.theme = 'lavender';
      fallbackPlan.summary = 'App theme updated to Lavender.';
    } else if (lower.includes('default')) {
      fallbackPlan.theme = 'default';
      fallbackPlan.summary = 'App theme updated to Default.';
    }
    if (lower.includes('top') || instruction.includes('উপরে')) {
      fallbackPlan.menuPosition = 'top';
    } else if (lower.includes('bottom') || instruction.includes('নিচে')) {
      fallbackPlan.menuPosition = 'bottom';
    }
    if (
      lower.includes('menu') ||
      instruction.includes('মেনু') ||
      instruction.includes('বাটন')
    ) {
      const slug = `menu-${Date.now().toString().slice(-4)}`;
      fallbackPlan.addOrUpdateMenu = {
        id: slug,
        label: 'Custom Menu',
        icon: 'sparkles',
        title: 'Custom Menu',
        body: instruction.slice(0, 900),
      };
      fallbackPlan.summary = 'Created a new custom menu tab with your instructions.';
    }
    res.json({ plan: fallbackPlan });
  });

  app.get('/api/releases/current', (_req, res) => {
    const manifest = readCurrentReleaseManifest();
    res.json({ release: manifest });
  });

  app.get('/api/releases/latest.zip', (_req, res) => {
    const manifest = readCurrentReleaseManifest();
    if (!manifest) {
      res.status(404).json({ error: 'No release ZIP has been generated yet.' });
      return;
    }
    const zipPath = path.join(RELEASES_DIR, path.basename(manifest.filename));
    if (!fs.existsSync(zipPath)) {
      res.status(404).json({ error: 'Release ZIP file not found.' });
      return;
    }
    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', `attachment; filename="${manifest.filename}"`);
    res.sendFile(zipPath);
  });

  app.post('/api/admin/qe-release', async (req, res) => {
    const verifiedUser = await verifyFirebaseBearerUser(req.headers.authorization);
    if (!verifiedUser || !BOOTSTRAPPED_ADMIN_EMAILS.has(verifiedUser.email)) {
      res.status(403).json({ error: 'Only verified Admin Gmail can build and replace release ZIPs.' });
      return;
    }

    const body = req.body as { version?: unknown; note?: unknown };
    const rawVersion = typeof body.version === 'string' ? body.version.trim() : '';
    const safeVersion = rawVersion.replace(/[^a-zA-Z0-9._-]/g, '').slice(0, 32);
    const note = typeof body.note === 'string' ? body.note.trim().slice(0, 240) : '';

    if (!safeVersion) {
      res.status(400).json({ error: 'Enter a valid version number (e.g., 1.1.0).' });
      return;
    }

    try {
      ensureReleasesDir();

      const zip = new JSZip();
      const fileCount = addProjectFilesToZip(zip, __dirname);
      zip.file(
        'RELEASE_INFO.txt',
        `Chatbox Release v${safeVersion}\nBuilt At: ${new Date().toISOString()}\nBy: ${verifiedUser.email}\nNotes: ${note || 'App update release'}\n`
      );

      const zipBuffer = await zip.generateAsync({
        type: 'nodebuffer',
        compression: 'DEFLATE',
        compressionOptions: { level: 6 },
      });

      // Delete ALL previous ZIP & manifest files so only the newest version remains
      for (const existingEntry of fs.readdirSync(RELEASES_DIR)) {
        if (existingEntry.endsWith('.zip') || existingEntry === 'release.json') {
          fs.unlinkSync(path.join(RELEASES_DIR, existingEntry));
        }
      }

      const filename = `chatbox-v${safeVersion}.zip`;
      const zipPath = path.join(RELEASES_DIR, filename);
      fs.writeFileSync(zipPath, zipBuffer);

      const manifest: StoredQeRelease = {
        version: safeVersion,
        filename,
        sizeBytes: zipBuffer.length,
        fileCount: fileCount + 1,
        note,
        createdAt: new Date().toISOString(),
        downloadUrl: '/api/releases/latest.zip',
      };
      fs.writeFileSync(RELEASE_MANIFEST_PATH, JSON.stringify(manifest, null, 2));

      res.json({ release: manifest });
    } catch (error) {
      res.status(500).json({
        error: error instanceof Error ? error.message : 'Failed to build release ZIP archive.',
      });
    }
  });

  if (process.env.NODE_ENV !== 'production') {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true, hmr: false },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.resolve(__dirname, 'dist');
    app.use(express.static(distPath));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Chatbox server listening on http://0.0.0.0:${PORT}`);
  });
}

startServer().catch((error) => {
  console.error('Failed to start server:', error);
  process.exit(1);
});
