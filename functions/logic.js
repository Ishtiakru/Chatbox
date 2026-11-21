export function cleanInput(value, max = 1000) {
  if (typeof value !== 'string') throw new TypeError('Text is required.');
  const text = value.trim();
  if (!text || text.length > max) throw new RangeError(`Text must be between 1 and ${max} characters.`);
  return text;
}

// AI output is untrusted. This allowlist is also applied at the mutation endpoint.
export function validateProposal(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) throw new TypeError('Invalid proposal.');
  const keys = Object.keys(input).sort().join(',');
  if (input.action === 'unsupported' && keys === 'action') return { action: 'unsupported' };
  if (input.action === 'set_welcome' && keys === 'action,value') {
    return { action: 'set_welcome', value: cleanInput(input.value, 160) };
  }
  throw new TypeError('Unsupported action.');
}
export function parseProposal(text) {
  try { return validateProposal(JSON.parse(text)); } catch { return { action: 'unsupported' }; }
}
export function responseText(payload) {
  const output = Array.isArray(payload?.output) ? payload.output : [];
  return output.flatMap(item => Array.isArray(item?.content) ? item.content : [])
    .filter(item => item?.type === 'output_text' && typeof item.text === 'string')
    .map(item => item.text).join('\n').trim();
}
export function isAdministrator(record, uid) {
  return Boolean(uid && record && typeof record.uid === 'string' && record.uid === uid);
}
