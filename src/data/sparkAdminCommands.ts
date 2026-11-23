import type { SparkCommandProposal } from '../types/chatbox';
import {
  MAX_UPDATE_NOTE_LENGTH,
  MAX_UPDATE_URL_LENGTH,
  MAX_UPDATE_VERSION_LENGTH,
  MAX_WELCOME_LENGTH,
  VALID_GMAIL_REGEX,
} from '../types/chatbox';

const themeLavenderRegex =
  /^(?:(?:আমার\s+)?হোম(?:\s*পেজ(?:টা|টি)?)?\s*(?:ল্যাভেন্ডার|lavender)\s*(?:কালার|রঙ)?\s*(?:করো|করুন|করে\s*দাও)|(?:set|make)\s+(?:the\s+)?home(?:\s+page)?\s+(?:to\s+)?lavender|theme\s*:\s*lavender)[।.!]?$/i;

const themeDefaultRegex =
  /^(?:theme\s*:\s*default|হোম(?:\s*পেজ(?:টা|টি)?)?\s*(?:আগের|ডিফল্ট)\s*(?:রঙ|কালার)?\s*(?:করো|করুন|করে\s*দাও))[।.!]?$/i;

const menuTopRegex =
  /^(?:menu\s*:\s*top|মেনু(?:বার|টি|টা)?\s*(?:উপরে|উপরে\s*নিয়ে|উপরে\s*নিয়ে)\s*(?:আনো|রাখো|করো|দাও))[।.!]?$/i;

const menuBottomRegex =
  /^(?:menu\s*:\s*bottom|মেনু(?:বার|টি|টা)?\s*(?:নিচে|নিচে\s*নিয়ে|নিচে\s*নিয়ে)\s*(?:আনো|রাখো|করো|দাও))[।.!]?$/i;

const directWelcomeRegex = /^(?:welcome|স্বাগতম)\s*:\s*([\s\S]+)$/i;

const QUOTE_GROUP = '(?:"([^"\\n\\r]*)"|“([^”\\n\\r]*)”|\'([^\'\\n\\r]*)\'|‘([^’\\n\\r]*)’)';
const englishWelcomeRegex = new RegExp(
  `^(?:please\\s+)?(?:set|change|update)\\s+(?:the\\s+)?welcome(?:\\s+message)?\\s+to\\s+${QUOTE_GROUP}\\s*[.!]?\\s*$`,
  'i'
);
const bengaliWelcomeRegex = new RegExp(
  `^(?:স্বাগত|স্বাগতম)(?:\\s+বার্তা(?:টি)?)?\\s+${QUOTE_GROUP}\\s+(?:করো|করুন|করে\\s+দাও|করে\\s+দিন)\\s*[।.!]?\\s*$`
);
const updateCommandRegex = /^(?:update|app\s*update)\s*:\s*([\s\S]+)$/i;

export function parseSparkCommand(input: string): SparkCommandProposal {
  const text = input.trim();
  if (!text) return { kind: 'unsupported' };
  if (themeLavenderRegex.test(text)) return { kind: 'setTheme', value: 'lavender' };
  if (themeDefaultRegex.test(text)) return { kind: 'setTheme', value: 'default' };
  if (menuTopRegex.test(text)) return { kind: 'setMenu', value: 'top' };
  if (menuBottomRegex.test(text)) return { kind: 'setMenu', value: 'bottom' };

  const updateMatch = updateCommandRegex.exec(text);
  if (updateMatch) {
    const raw = updateMatch[1].trim();
    const lower = raw.toLowerCase();
    if (lower === 'off' || lower === 'clear' || lower === 'none') {
      return {
        kind: 'setAppUpdate',
        active: false,
        version: '',
        note: '',
        downloadUrl: '',
      };
    }
    const parts = raw.split('|').map((p) => p.trim());
    const version = (parts[0] ?? '').slice(0, MAX_UPDATE_VERSION_LENGTH);
    const note = (parts[1] ?? '').slice(0, MAX_UPDATE_NOTE_LENGTH);
    const downloadUrl = (parts[2] ?? '').slice(0, MAX_UPDATE_URL_LENGTH);
    if (!version || (downloadUrl && !downloadUrl.startsWith('https://'))) {
      return { kind: 'unsupported' };
    }
    return {
      kind: 'setAppUpdate',
      active: true,
      version,
      note,
      downloadUrl,
    };
  }

  const directMatch = directWelcomeRegex.exec(text);
  let rawValue: string | undefined = directMatch?.[1];
  if (!rawValue) {
    const quotedMatch = englishWelcomeRegex.exec(text) ?? bengaliWelcomeRegex.exec(text);
    rawValue = quotedMatch?.slice(1).find((group) => Boolean(group));
  }

  const value = rawValue?.trim() ?? '';
  if (!value || value.length > MAX_WELCOME_LENGTH) {
    return { kind: 'unsupported' };
  }
  return { kind: 'setWelcome', value };
}

export function isValidSparkTarget(input: string): boolean {
  return VALID_GMAIL_REGEX.test(input.trim().toLowerCase());
}
