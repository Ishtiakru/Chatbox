export interface WorldLanguageOption {
  code: string;
  nativeLabel: string;
  englishName: string;
  countryName: string;
}

export const WORLD_LANGUAGES: WorldLanguageOption[] = [
  { code: 'en-US', nativeLabel: 'English (US)', englishName: 'English', countryName: 'United States' },
  { code: 'en-GB', nativeLabel: 'English (UK)', englishName: 'English', countryName: 'United Kingdom' },
  { code: 'bn-BD', nativeLabel: 'বাংলা (বাংলাদেশ)', englishName: 'Bengali', countryName: 'Bangladesh' },
  { code: 'bn-IN', nativeLabel: 'বাংলা (ভারত)', englishName: 'Bengali', countryName: 'India' },
  { code: 'hi-IN', nativeLabel: 'हिन्दी (भारत)', englishName: 'Hindi', countryName: 'India' },
  { code: 'ar-SA', nativeLabel: 'العربية (السعودية)', englishName: 'Arabic', countryName: 'Saudi Arabia' },
  { code: 'ar-AE', nativeLabel: 'العربية (الإمارات)', englishName: 'Arabic', countryName: 'United Arab Emirates' },
  { code: 'ar-EG', nativeLabel: 'العربية (مصر)', englishName: 'Arabic', countryName: 'Egypt' },
  { code: 'ur-PK', nativeLabel: 'اردو (پاکستان)', englishName: 'Urdu', countryName: 'Pakistan' },
  { code: 'es-ES', nativeLabel: 'Español (España)', englishName: 'Spanish', countryName: 'Spain' },
  { code: 'es-MX', nativeLabel: 'Español (México)', englishName: 'Spanish', countryName: 'Mexico' },
  { code: 'fr-FR', nativeLabel: 'Français (France)', englishName: 'French', countryName: 'France' },
  { code: 'fr-CA', nativeLabel: 'Français (Canada)', englishName: 'French', countryName: 'Canada' },
  { code: 'de-DE', nativeLabel: 'Deutsch (Deutschland)', englishName: 'German', countryName: 'Germany' },
  { code: 'pt-BR', nativeLabel: 'Português (Brasil)', englishName: 'Portuguese', countryName: 'Brazil' },
  { code: 'pt-PT', nativeLabel: 'Português (Portugal)', englishName: 'Portuguese', countryName: 'Portugal' },
  { code: 'it-IT', nativeLabel: 'Italiano (Italia)', englishName: 'Italian', countryName: 'Italy' },
  { code: 'ru-RU', nativeLabel: 'Русский (Россия)', englishName: 'Russian', countryName: 'Russia' },
  { code: 'zh-CN', nativeLabel: '中文 (简体 · 中国)', englishName: 'Chinese Simplified', countryName: 'China' },
  { code: 'zh-TW', nativeLabel: '中文 (繁體 · 台灣)', englishName: 'Chinese Traditional', countryName: 'Taiwan' },
  { code: 'ja-JP', nativeLabel: '日本語 (日本)', englishName: 'Japanese', countryName: 'Japan' },
  { code: 'ko-KR', nativeLabel: '한국어 (대한민국)', englishName: 'Korean', countryName: 'South Korea' },
  { code: 'id-ID', nativeLabel: 'Bahasa Indonesia', englishName: 'Indonesian', countryName: 'Indonesia' },
  { code: 'ms-MY', nativeLabel: 'Bahasa Melayu', englishName: 'Malay', countryName: 'Malaysia' },
  { code: 'tr-TR', nativeLabel: 'Türkçe (Türkiye)', englishName: 'Turkish', countryName: 'Turkey' },
  { code: 'vi-VN', nativeLabel: 'Tiếng Việt (Việt Nam)', englishName: 'Vietnamese', countryName: 'Vietnam' },
  { code: 'th-TH', nativeLabel: 'ไทย (ประเทศไทย)', englishName: 'Thai', countryName: 'Thailand' },
  { code: 'fil-PH', nativeLabel: 'Filipino (Pilipinas)', englishName: 'Filipino', countryName: 'Philippines' },
  { code: 'fa-IR', nativeLabel: 'فارسی (ایران)', englishName: 'Persian', countryName: 'Iran' },
  { code: 'ne-NP', nativeLabel: 'नेपाली (नेपाल)', englishName: 'Nepali', countryName: 'Nepal' },
  { code: 'si-LK', nativeLabel: 'සිංහල (ශ්‍රී ලංකා)', englishName: 'Sinhala', countryName: 'Sri Lanka' },
  { code: 'ta-IN', nativeLabel: 'தமிழ் (இந்தியா)', englishName: 'Tamil', countryName: 'India' },
  { code: 'te-IN', nativeLabel: 'తెలుగు (భారతదేశం)', englishName: 'Telugu', countryName: 'India' },
  { code: 'mr-IN', nativeLabel: 'मराठी (भारत)', englishName: 'Marathi', countryName: 'India' },
  { code: 'pa-IN', nativeLabel: 'ਪੰਜਾਬੀ (ਭਾਰਤ)', englishName: 'Punjabi', countryName: 'India' },
  { code: 'gu-IN', nativeLabel: 'ગુજરાતી (ભਾਰત)', englishName: 'Gujarati', countryName: 'India' },
  { code: 'sw-KE', nativeLabel: 'Kiswahili (Kenya)', englishName: 'Swahili', countryName: 'Kenya' },
  { code: 'nl-NL', nativeLabel: 'Nederlands (Nederland)', englishName: 'Dutch', countryName: 'Netherlands' },
  { code: 'pl-PL', nativeLabel: 'Polski (Polska)', englishName: 'Polish', countryName: 'Poland' },
  { code: 'uk-UA', nativeLabel: 'Українська (Україна)', englishName: 'Ukrainian', countryName: 'Ukraine' },
  { code: 'el-GR', nativeLabel: 'Ελληνικά (Ελλάδα)', englishName: 'Greek', countryName: 'Greece' },
  { code: 'sv-SE', nativeLabel: 'Svenska (Sverige)', englishName: 'Swedish', countryName: 'Sweden' },
  { code: 'nb-NO', nativeLabel: 'Norsk (Norge)', englishName: 'Norwegian', countryName: 'Norway' },
  { code: 'da-DK', nativeLabel: 'Dansk (Danmark)', englishName: 'Danish', countryName: 'Denmark' },
  { code: 'fi-FI', nativeLabel: 'Suomi (Suomi)', englishName: 'Finnish', countryName: 'Finland' },
  { code: 'cs-CZ', nativeLabel: 'Čeština (Česko)', englishName: 'Czech', countryName: 'Czechia' },
  { code: 'ro-RO', nativeLabel: 'Română (România)', englishName: 'Romanian', countryName: 'Romania' },
  { code: 'hu-HU', nativeLabel: 'Magyar (Magyarország)', englishName: 'Hungarian', countryName: 'Hungary' },
  { code: 'he-IL', nativeLabel: 'עברית (ישראל)', englishName: 'Hebrew', countryName: 'Israel' },
  { code: 'am-ET', nativeLabel: 'አማርኛ (ኢትዮጵያ)', englishName: 'Amharic', countryName: 'Ethiopia' },
  { code: 'ha-NG', nativeLabel: 'Hausa (Nigeria)', englishName: 'Hausa', countryName: 'Nigeria' },
  { code: 'yo-NG', nativeLabel: 'Yorùbá (Nigeria)', englishName: 'Yoruba', countryName: 'Nigeria' },
];

export function normalizeLanguageCode(raw?: string | null): string {
  const trimmed = (raw ?? '').trim();
  if (trimmed === 'bn') return 'bn-BD';
  if (trimmed === 'en' || trimmed === '') return 'en-US';
  return WORLD_LANGUAGES.find((lang) => lang.code.toLowerCase() === trimmed.toLowerCase())?.code ?? 'en-US';
}

export function findLanguageByCode(code?: string | null): WorldLanguageOption {
  const normalized = normalizeLanguageCode(code);
  return WORLD_LANGUAGES.find((lang) => lang.code === normalized) ?? WORLD_LANGUAGES[0];
}

export function isBengaliLanguage(code?: string | null): boolean {
  return normalizeLanguageCode(code).toLowerCase().startsWith('bn');
}
