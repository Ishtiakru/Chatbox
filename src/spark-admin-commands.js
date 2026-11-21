// Deliberately narrow, deterministic command parser; no AI or arbitrary execution.
// Natural-language templates require quoted exact text, so the preview and
// committed value are unambiguous. Other operations always remain unsupported.
export function parseSparkCommand(input) {
  if (typeof input !== 'string') return null;
  const text = input.trim();
  const direct = text.match(/^(?:welcome|স্বাগতম)\s*:\s*([\s\S]+)$/i);
  let value = direct?.[1];
  if (value === undefined) {
    // Matched opening and closing delimiters; no instructions outside them.
    const quote = '(?:"([^"\\n\\r]*)"|“([^”\\n\\r]*)”|\'([^\'\\n\\r]*)\'|‘([^’\\n\\r]*)’)';
    const english = new RegExp(`^(?:please\\s+)?(?:set|change|update)\\s+(?:the\\s+)?welcome(?:\\s+message)?\\s+to\\s+${quote}\\s*[.!]?\\s*$`, 'i');
    const bengali = new RegExp(`^(?:স্বাগত|স্বাগতম)(?:\\s+বার্তা(?:টি)?)?\\s+${quote}\\s+(?:করো|করুন|করে\\s+দাও|করে\\s+দিন)\\s*[।.!]?\\s*$`, 'u');
    const match = text.match(english) || text.match(bengali);
    value = match?.slice(1).find(part => part !== undefined);
  }
  value = value?.trim();
  if (!value || value.length > 160) return null;
  return { action: 'set_welcome', value };
}
export function validSparkTarget(input) {
  return typeof input === 'string' && /^[a-z0-9][a-z0-9._%+-]{0,63}@gmail\.com$/.test(input);
}
