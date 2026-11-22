import { Capacitor, registerPlugin } from '@capacitor/core';
import { ANDROID_AI_LAB_ENABLED, MULTILINGUAL_ADMIN_LAB_ENABLED } from './feature-flags.js';
import { getAI, getGenerativeModel, GoogleAIBackend } from 'firebase/ai';
import { app } from './firebase.js';
import { ensureAppCheck } from './app-check.js';
import { askSairaWithModel } from './saira-ai-adapter.js';
import knowledge from '../docs/saira-knowledge.md?raw';

let model;
export async function askFirebaseSaira(question) {
  // Web App Check is not an Android attestation implementation.
  if (Capacitor.isNativePlatform()) {
    if (!ANDROID_AI_LAB_ENABLED || Capacitor.getPlatform() !== 'android') throw new Error('AI is not configured for native apps.');
    const text = typeof question === 'string' ? question.trim() : '';
    if (!text || text.length > 1000) throw new Error('Invalid question.');
    const native = registerPlugin('NativeSaira');
    const result = await native.ask({question:text, knowledge});
    if (typeof result?.answer !== 'string' || !result.answer.trim()) throw new Error('AI service is unavailable.');
    let answer = result.answer.slice(0, 4000);
    const last = answer.charCodeAt(answer.length - 1);
    if (last >= 0xD800 && last <= 0xDBFF) answer = answer.slice(0, -1);
    return {answer};
  }
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

// Experimental Android-only interpretation. It returns untrusted text; callers
// must validate the proposal and ask for confirmation before any write.
export async function proposeNativeAdminChange(request) {
  if (!MULTILINGUAL_ADMIN_LAB_ENABLED || !Capacitor.isNativePlatform() || Capacitor.getPlatform() !== 'android') throw new Error('Admin AI is not configured.');
  const text = typeof request === 'string' ? request.trim() : '';
  if (!text || text.length > 1000) throw new Error('Invalid request.');
  const native = registerPlugin('NativeSaira');
  const result = await native.proposeAdminChange({request:text});
  if (typeof result?.answer !== 'string' || !result.answer.trim() || result.answer.length > 2048) throw new Error('AI service is unavailable.');
  return result.answer;
}
