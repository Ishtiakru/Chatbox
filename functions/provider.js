import { responseText } from './logic.js';

export async function askProvider({ key, instructions, input, fetchImpl = fetch }) {
  if (!key) throw new Error('AI service is not configured.');
  const response = await fetchImpl('https://api.openai.com/v1/responses', {
    method: 'POST',
    headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ model: 'gpt-4.1-mini', instructions, input, max_output_tokens: 400, store: false }),
    signal: AbortSignal.timeout(20000),
  });
  if (!response.ok) throw new Error('AI provider request failed.');
  const text = responseText(await response.json());
  if (!text) throw new Error('AI provider returned an empty response.');
  return text;
}
