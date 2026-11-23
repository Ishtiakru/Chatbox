import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { GoogleGenAI } from '@google/genai';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = 3000;
const MODEL_NAME = 'gemini-4-hydrogen';

const CURATED_KNOWLEDGE = `
# Saira's curated app knowledge

Chatbox is a responsive messaging and companion web application built with React, TypeScript, Tailwind CSS, Express, Firebase Authentication (Google Sign-In), Cloud Firestore, Firebase Storage, and Gemini AI.

Features available in Chatbox:
- Lavender Login & Welcome Screen with 50+ world languages (including Bangla / বাংলা and English US), Google Sign-In, remember-me phone/account preference, and password reset helper.
- Demo Conversations: Local sample conversations (Saira offline guide, Maya Rahman, Arif Hasan, Nora Ahmed, The good people) with local search, quick emoji, and persistent browser history.
- Live Chats: Real-time direct messaging powered by Cloud Firestore. Users share their Chatbox ID (Firebase UID) privately to send conversation requests, accept or decline incoming requests, exchange real-time messages with pagination for earlier messages, attach compressed images and browser-recorded voice notes, close conversations, and block/unblock peers. Includes Saira 1-minute inactivity companion & absence auto-reply. Display names are unverified and messages are not end-to-end encrypted.
- Saira AI: Conversational assistant powered by Gemini (server-side @google/genai), grounded in this curated app knowledge, plus saved session history and Auto-Reply rule settings. Only the submitted question and current session context are sent; chat history from other conversations is never shared.
- Calls: Shows the planned Voice and Video Calls screen. Real-time audio/video calling is not connected yet; it requires a signaling and media relay (TURN) backend.
- Spark Admin Studio: Owner and delegate console protected by Firestore security rules and recent Google account verification. Supports deterministic commands to preview and update the public welcome banner, bounded home appearance theme (default or lavender), mobile navigation menu position (bottom or top), app update badge notices, and managing Gmail delegate invitations.

Important notes:
- Live conversations, Saira AI, and Spark Admin require signing in with Google.
- Do not share sensitive or private information in experimental chats or AI queries.
`.trim();

const CHAT_SYSTEM_INSTRUCTION = `
You are Saira Assistant (সায়রা অ্যাসিস্ট্যান্ট), a friendly, intelligent, free and unlimited personal AI companion inside Chatbox.

Core Behavior Rules:
1. Never initiate a conversation before the user messages first. Only reply to messages sent to you.
2. Always respond naturally in the exact language/style the user writes in (Bengali, Banglish, or English).
3. Greeting Rule:
   - When the prompt marks this as the first message in a conversation, begin warmly with the provided time-of-day greeting (Good Morning, Good Afternoon, or Good Evening — or a natural bilingual greeting like "Good Evening!" alongside Bengali if the user writes in Bengali).
   - When the user is ending the conversation or saying goodbye/thank you/done at night (isNightTime=true), gracefully wish them "Good Night" (e.g., "Good Night! শুভ রাত্রি।") as a warm closing.
4. Unlimited General Knowledge:
   - Answer ANY question or request the user brings (everyday help, study, technology, coding, writing, advice, general facts, or Chatbox app guidance) clearly, accurately, and warmly.
5. Ground Chatbox-specific questions in this app summary when asked about Chatbox:
${CURATED_KNOWLEDGE}
`.trim();

const REPLY_SYSTEM_INSTRUCTION =
  'You are Saira Assistant writing concise, natural auto-replies on the user\'s behalf. ' +
  'Match the language of the incoming message (Bengali, Banglish, or English). ' +
  'Keep replies warm, polite, 1 to 3 sentences, and follow the exact instruction/context provided.';

function getAiClient(): GoogleGenAI | null {
  const apiKey = process.env.GEMINI_API_KEY?.trim();
  if (!apiKey || apiKey === 'your_gemini_api_key_here') {
    return null;
  }
  return new GoogleGenAI({
    apiKey,
    httpOptions: {
      headers: {
        'User-Agent': 'aistudio-build',
      },
    },
  });
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

async function startServer() {
  const app = express();
  app.use(express.json({ limit: '1mb' }));

  app.get('/api/health', (_req, res) => {
    res.json({ ok: true });
  });

  app.post('/api/saira/chat', async (req, res) => {
    const body = req.body as {
      question?: unknown;
      history?: Array<{ question?: unknown; answer?: unknown }>;
      isFirstTurn?: unknown;
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

    const historyItems = Array.isArray(body.history) ? body.history.slice(-20) : [];
    const isFirstTurn = Boolean(body.isFirstTurn) && historyItems.length === 0;
    const hourOfDay = typeof body.hourOfDay === 'number' ? body.hourOfDay : undefined;
    const greeting = getTimeGreeting(hourOfDay);
    const night = isNightTime(hourOfDay);

    let contextualPrompt = `[Context: localTimeGreeting="${greeting}", isNightTime=${night}, isFirstTurn=${isFirstTurn}]`;
    if (isFirstTurn) {
      contextualPrompt += ` Start your reply warmly with "${greeting}" suited to the user's language.`;
    }
    if (night) {
      contextualPrompt +=
        ' If the user is ending the conversation or saying goodbye/thanks/goodnight, include a warm Good Night farewell.';
    }
    contextualPrompt += `\nUser message: ${question}`;

    const contents: Array<{ role: 'user' | 'model'; parts: Array<{ text: string }> }> = [];
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

    try {
      const stream = await ai.models.generateContentStream({
        model: MODEL_NAME,
        contents,
        config: {
          systemInstruction: CHAT_SYSTEM_INSTRUCTION,
          maxOutputTokens: 1024,
        },
      });

      for await (const chunk of stream) {
        const text = chunk.text;
        if (text) {
          res.write(`data: ${JSON.stringify({ text })}\n\n`);
        }
      }
      res.write(`data: ${JSON.stringify({ done: true })}\n\n`);
      res.end();
    } catch (error) {
      const message =
        error instanceof Error ? error.message : 'Saira AI is unavailable right now.';
      res.write(`data: ${JSON.stringify({ error: message })}\n\n`);
      res.end();
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

        const response = await ai.models.generateContent({
          model: MODEL_NAME,
          contents: prompt,
          config: {
            systemInstruction: REPLY_SYSTEM_INSTRUCTION,
            maxOutputTokens: 256,
          },
        });
        const reply = response.text?.trim();
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

        const response = await ai.models.generateContent({
          model: MODEL_NAME,
          contents: prompt,
          config: {
            systemInstruction: REPLY_SYSTEM_INSTRUCTION,
            maxOutputTokens: 256,
          },
        });
        const reply = response.text?.trim();
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
