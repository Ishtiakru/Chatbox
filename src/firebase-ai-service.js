import { Capacitor } from '@capacitor/core';
import { getAI, getGenerativeModel, GoogleAIBackend } from 'firebase/ai';
import { app } from './firebase.js';
import { ensureAppCheck } from './app-check.js';
import { askSairaWithModel } from './saira-ai-adapter.js';
import knowledge from '../docs/saira-knowledge.md?raw';

let model;
export async function askFirebaseSaira(question) {
  // Web App Check is not an Android attestation implementation.
  if (Capacitor.isNativePlatform()) throw new Error('AI is not configured for native apps.');
  ensureAppCheck();
  if (!model) {
    const ai = getAI(app, { backend: new GoogleAIBackend() });
    model = getGenerativeModel(ai, {
      model: 'gemini-3.8-flash',
      systemInstruction: `You are Saira, a helpful guide to Chatbox. Answer in the user's language and ground app facts in this reviewed release knowledge. If unsure, say so; never claim planned features are active. Do not take or suggest administrative actions.\n\n${knowledge}`,
      generationConfig: { maxOutputTokens: 1024 }
    });
  }
  return askSairaWithModel(question, model);
}
