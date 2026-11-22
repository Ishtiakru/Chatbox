// Format-only helper for a future phone contact feature. It does not verify
// that a number exists, belongs to someone, or can receive calls or messages.
// An explicit +country code avoids guessing a locale or merging two numbers.
export function normalizeInternationalPhone(input) {
  if (typeof input !== 'string' || input.length > 40) return null;
  const trimmed = input.trim();
  if (!/^\+[0-9 ()-]+$/.test(trimmed)) return null;
  const normalized = '+' + trimmed.slice(1).replace(/[ ()-]/g, '');
  return /^\+[1-9][0-9]{7,14}$/.test(normalized) ? normalized : null;
}
