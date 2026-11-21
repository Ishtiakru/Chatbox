// Stateless questions only; no other chat history is sent to the AI provider.
export async function askSairaWithModel(question, model) {
  const text = typeof question === 'string' ? question.trim() : '';
  if (!text || text.length > 1000) throw new Error('Invalid question.');
  if (!model || typeof model.generateContent !== 'function') throw new Error('AI service is not configured.');
  const result = await model.generateContent(text);
  const answer = result?.response?.text();
  if (typeof answer !== 'string' || !answer.trim()) throw new Error('No answer available.');
  return { answer: answer.slice(0, 4000) };
}
