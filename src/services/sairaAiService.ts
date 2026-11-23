import type { AiExchange } from '../types/chatbox';

export async function streamSairaChat(params: {
  question: string;
  history: AiExchange[];
  isFirstTurn: boolean;
  onChunk: (accumulatedText: string) => void;
  signal?: AbortSignal;
}): Promise<string> {
  const response = await fetch('/api/saira/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    signal: params.signal,
    body: JSON.stringify({
      question: params.question,
      history: params.history.slice(-20).map((ex) => ({
        question: ex.question,
        answer: ex.answer,
      })),
      isFirstTurn: params.isFirstTurn,
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

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() ?? '';

    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed.startsWith('data:')) continue;
      const jsonStr = trimmed.slice(5).trim();
      if (!jsonStr) continue;
      const parsed = JSON.parse(jsonStr) as { text?: string; error?: string; done?: boolean };
      if (parsed.error) {
        throw new Error(parsed.error);
      }
      if (parsed.text) {
        accumulated += parsed.text;
        params.onChunk(accumulated);
      }
    }
  }

  return accumulated.trim();
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
