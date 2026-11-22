// Stateless questions only; no other chat history is sent to the AI provider.
export async function askSairaWithModel(question, model) {
  const text = typeof question === 'string' ? question.trim() : '';
  if (!text || text.length > 1000) throw new Error('Invalid question.');
  if (!model || typeof model.generateContent !== 'function') throw new Error('AI service is not configured.');
  let answer;
  try {
    const result = await model.generateContent(text);
    answer = typeof result?.response?.text === 'function' ? result.response.text() : '';
  } catch {
    // Provider errors may contain request details; never expose them to UI.
    throw new Error('AI service is unavailable.');
  }
  if (typeof answer !== 'string' || !answer.trim()) throw new Error('No answer available.');
  let bounded = answer.slice(0, 4000);
  // Avoid creating a lone surrogate when truncating an emoji or other pair.
  const last = bounded.charCodeAt(bounded.length - 1);
  if (last >= 0xD800 && last <= 0xDBFF) bounded = bounded.slice(0, -1);
  return { answer: bounded };
}
