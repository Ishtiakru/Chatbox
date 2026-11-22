// Deliberately narrow, deterministic command parser; no AI or arbitrary execution.
// Natural-language templates require quoted exact text, so the preview and
// committed value are unambiguous. Other operations always remain unsupported.
export function parseSparkCommand(input) {
  if (typeof input !== 'string') return null;
  const text = input.trim();
  // Fixed commands only: never execute code or inject arbitrary CSS.
  if (/^(?:(?:আমার\s+)?হোম(?:\s*পেজ(?:টা|টি)?)?\s*(?:ল্যাভেন্ডার|lavender)\s*(?:কালার|রঙ)?\s*(?:করো|করুন|করে\s*দাও)|(?:set|make)\s+(?:the\s+)?home(?:\s+page)?\s+(?:to\s+)?lavender|theme\s*:\s*lavender)[।.!]?$/iu.test(text)) return {action:'set_theme', value:'lavender'};
  if (/^(?:theme\s*:\s*default|হোম(?:\s*পেজ(?:টা|টি)?)?\s*(?:আগের|ডিফল্ট)\s*(?:রঙ|কালার)?\s*(?:করো|করুন|করে\s*দাও))[।.!]?$/iu.test(text)) return {action:'set_theme', value:'default'};
  if (/^(?:menu\s*:\s*top|মেনু(?:বার|টি|টা)?\s*(?:উপরে|উপরে\s*নিয়ে|উপরে\s*নিয়ে)\s*(?:আনো|রাখো|করো|দাও))[।.!]?$/iu.test(text)) return {action:'set_menu', value:'top'};
  if (/^(?:menu\s*:\s*bottom|মেনু(?:বার|টি|টা)?\s*(?:নিচে|নিচে\s*নিয়ে|নিচে\s*নিয়ে)\s*(?:আনো|রাখো|করো|দাও))[।.!]?$/iu.test(text)) return {action:'set_menu', value:'bottom'};
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
