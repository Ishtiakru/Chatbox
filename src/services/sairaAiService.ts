import {
  collection,
  doc,
  getDocs,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  writeBatch,
  type Unsubscribe,
} from 'firebase/firestore';
import { db, handleFirestoreError, OperationType } from '../firebase';
import type { AiExchange, SairaChatSession } from '../types/chatbox';

async function streamSairaChatOnce(params: {
  question: string;
  history: AiExchange[];
  isFirstTurn: boolean;
  isContinuation?: boolean;
  onChunk: (accumulatedText: string) => void;
  signal?: AbortSignal;
}): Promise<string> {
  const response = await fetch('/api/saira/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    signal: params.signal,
    body: JSON.stringify({
      question: params.question,
      history: params.history.slice(-16).map((ex) => ({
        question: ex.question,
        answer: ex.answer,
      })),
      isFirstTurn: params.isFirstTurn,
      isContinuation: Boolean(params.isContinuation),
      hourOfDay: new Date().getHours(),
    }),
  });

  if (!response.ok || !response.body) {
    let errText = 'Saira AI is unavailable right now.';
    try {
      const json = (await response.json()) as { error?: string };
      if (json.error) errText = json.error;
    } catch {
      // Ignore parse failure
    }
    throw new Error(errText);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let accumulated = '';

  const processLine = (line: string) => {
    const trimmed = line.trim();
    if (!trimmed.startsWith('data:')) return;
    const jsonStr = trimmed.slice(5).trim();
    if (!jsonStr) return;
    let parsed: { text?: string; error?: string; done?: boolean };
    try {
      parsed = JSON.parse(jsonStr) as { text?: string; error?: string; done?: boolean };
    } catch {
      return;
    }
    if (parsed.error) {
      throw new Error(parsed.error);
    }
    if (parsed.text) {
      accumulated += parsed.text;
      params.onChunk(accumulated);
    }
  };

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() ?? '';

    for (const line of lines) {
      processLine(line);
    }
  }

  buffer += decoder.decode();
  if (buffer.trim()) {
    for (const line of buffer.split('\n')) {
      processLine(line);
    }
  }

  return accumulated.trim();
}

export async function streamSairaChat(params: {
  question: string;
  history: AiExchange[];
  isFirstTurn: boolean;
  isContinuation?: boolean;
  onChunk: (accumulatedText: string) => void;
  signal?: AbortSignal;
}): Promise<string> {
  try {
    return await streamSairaChatOnce(params);
  } catch (err) {
    if (params.signal?.aborted) {
      throw err;
    }
    // Automatic silent recovery retry once with trimmed context so Saira never gets stuck
    return await streamSairaChatOnce({
      ...params,
      history: params.history.slice(-6),
    });
  }
}

export async function askSairaCompanion(params: {
  userName: string;
  peerName: string;
  userQuestion: string;
}): Promise<string> {
  const response = await fetch('/api/saira/companion', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      userName: params.userName,
      peerName: params.peerName,
      userQuestion: params.userQuestion,
      hourOfDay: new Date().getHours(),
    }),
  });

  if (!response.ok) {
    throw new Error('Could not reach Saira companion right now.');
  }
  const data = (await response.json()) as { reply?: string };
  return data.reply?.trim() || 'I am here if you need anything while waiting.';
}

export async function generateOneMinuteAutoReply(params: {
  senderName: string;
  peerName: string;
  lastMessageText: string;
  isSenderWaitingOnPeer: boolean;
}): Promise<string> {
  const response = await fetch('/api/saira/auto-reply', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      senderName: params.senderName,
      peerName: params.peerName,
      lastMessageText: params.lastMessageText,
      isSenderWaitingOnPeer: params.isSenderWaitingOnPeer,
      hourOfDay: new Date().getHours(),
    }),
  });

  if (!response.ok) {
    throw new Error('Auto-reply request failed.');
  }
  const data = (await response.json()) as { reply?: string };
  return data.reply?.trim() || '';
}

export function observeSairaSessions(
  uid: string,
  onSessions: (sessions: SairaChatSession[]) => void
): Unsubscribe {
  const path = `users/${uid}/sairaSessions`;
  const q = query(collection(db, 'users', uid, 'sairaSessions'), orderBy('updatedAt', 'desc'));
  return onSnapshot(
    q,
    (snap) => {
      const list: SairaChatSession[] = snap.docs.map((d) => {
        const data = d.data();
        return {
          id: d.id,
          title: String(data.title ?? 'Saira Chat'),
          lastPreview: String(data.lastPreview ?? ''),
          exchangeCount: Number(data.exchangeCount ?? 0),
          createdAt: data.createdAt ?? null,
          updatedAt: data.updatedAt ?? null,
        };
      });
      onSessions(list);
    },
    (error) => {
      handleFirestoreError(error, OperationType.LIST, path, false);
    }
  );
}

export async function loadSairaSessionExchanges(
  uid: string,
  sessionId: string
): Promise<AiExchange[]> {
  const path = `users/${uid}/sairaSessions/${sessionId}/exchanges`;
  try {
    const q = query(
      collection(db, 'users', uid, 'sairaSessions', sessionId, 'exchanges'),
      orderBy('sequence', 'asc')
    );
    const snap = await getDocs(q);
    return snap.docs.map((d) => {
      const data = d.data();
      return {
        id: d.id,
        question: String(data.question ?? ''),
        answer: String(data.answer ?? ''),
        isStreaming: false,
      };
    });
  } catch (error) {
    handleFirestoreError(error, OperationType.LIST, path, false);
    return [];
  }
}

export async function persistSairaExchange(params: {
  uid: string;
  sessionId: string;
  isNewSession: boolean;
  title: string;
  question: string;
  answer: string;
  sequence: number;
}): Promise<void> {
  const sessionPath = `users/${params.uid}/sairaSessions/${params.sessionId}`;
  try {
    const batch = writeBatch(db);
    const sessionRef = doc(db, 'users', params.uid, 'sairaSessions', params.sessionId);
    const cleanTitle = params.title.trim().slice(0, 120) || 'Saira Chat';
    const preview = params.answer.trim().slice(0, 240);

    if (params.isNewSession) {
      batch.set(sessionRef, {
        title: cleanTitle,
        lastPreview: preview,
        exchangeCount: params.sequence + 1,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
    } else {
      batch.update(sessionRef, {
        title: cleanTitle,
        lastPreview: preview,
        exchangeCount: params.sequence + 1,
        updatedAt: serverTimestamp(),
      });
    }

    const exId = `ex_${params.sequence}_${Date.now()}`;
    const exRef = doc(db, 'users', params.uid, 'sairaSessions', params.sessionId, 'exchanges', exId);
    batch.set(exRef, {
      question: params.question.trim().slice(0, 2000),
      answer: params.answer.trim().slice(0, 12000),
      sequence: params.sequence,
      createdAt: serverTimestamp(),
    });

    await batch.commit();
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, sessionPath, false);
  }
}
