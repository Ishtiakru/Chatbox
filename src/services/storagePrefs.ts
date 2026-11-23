import { DEMO_SEEDS } from '../data/demoCatalog';
import { normalizeLanguageCode } from '../data/worldLanguages';
import type { DemoMessage, SairaNotificationRule } from '../types/chatbox';

const KEYS = {
  demoMessages: 'chatbox_demo_messages_v1',
  loginLanguage: 'chatbox_login_language_v1',
  rememberMe: 'chatbox_remember_me_v1',
  rememberedPhone: 'chatbox_remembered_phone_v1',
  quickReaction: 'chatbox_quick_reaction_v1',
  sairaOneMinEnabled: 'chatbox_saira_one_min_enabled_v1',
  sairaNotifReplyEnabled: 'chatbox_saira_notif_reply_enabled_v1',
  sairaNotifRules: 'chatbox_saira_notif_rules_v1',
} as const;

export function getStoredDemoMessages(): Record<string, DemoMessage[]> {
  try {
    const raw = localStorage.getItem(KEYS.demoMessages);
    if (!raw) return { ...DEMO_SEEDS };
    const parsed = JSON.parse(raw) as Record<string, DemoMessage[]>;
    return { ...DEMO_SEEDS, ...parsed };
  } catch {
    return { ...DEMO_SEEDS };
  }
}

export function saveStoredDemoMessages(messages: Record<string, DemoMessage[]>): void {
  try {
    localStorage.setItem(KEYS.demoMessages, JSON.stringify(messages));
  } catch {
    // Ignore storage quota errors
  }
}

export function getStoredLoginLanguage(): string {
  try {
    return normalizeLanguageCode(localStorage.getItem(KEYS.loginLanguage));
  } catch {
    return 'en-US';
  }
}

export function saveStoredLoginLanguage(code: string): void {
  try {
    localStorage.setItem(KEYS.loginLanguage, normalizeLanguageCode(code));
  } catch {
    // Ignore
  }
}

export function getStoredRememberMe(): { enabled: boolean; phone: string } {
  try {
    const enabled = localStorage.getItem(KEYS.rememberMe) === 'true';
    const phone = enabled ? localStorage.getItem(KEYS.rememberedPhone) ?? '' : '';
    return { enabled, phone };
  } catch {
    return { enabled: false, phone: '' };
  }
}

export function saveStoredRememberMe(enabled: boolean, phone: string): void {
  try {
    localStorage.setItem(KEYS.rememberMe, String(enabled));
    if (enabled) {
      localStorage.setItem(KEYS.rememberedPhone, phone.trim().slice(0, 32));
    } else {
      localStorage.removeItem(KEYS.rememberedPhone);
    }
  } catch {
    // Ignore
  }
}

export function getStoredQuickReaction(): string {
  try {
    return localStorage.getItem(KEYS.quickReaction) || '💜';
  } catch {
    return '💜';
  }
}

export function saveStoredQuickReaction(emoji: string): void {
  try {
    localStorage.setItem(KEYS.quickReaction, emoji || '💜');
  } catch {
    // Ignore
  }
}

export function getStoredSairaOneMinEnabled(): boolean {
  try {
    const val = localStorage.getItem(KEYS.sairaOneMinEnabled);
    return val === null ? true : val === 'true';
  } catch {
    return true;
  }
}

export function saveStoredSairaOneMinEnabled(enabled: boolean): void {
  try {
    localStorage.setItem(KEYS.sairaOneMinEnabled, String(enabled));
  } catch {
    // Ignore
  }
}

export function getStoredSairaNotifReplyEnabled(): boolean {
  try {
    return localStorage.getItem(KEYS.sairaNotifReplyEnabled) === 'true';
  } catch {
    return false;
  }
}

export function saveStoredSairaNotifReplyEnabled(enabled: boolean): void {
  try {
    localStorage.setItem(KEYS.sairaNotifReplyEnabled, String(enabled));
  } catch {
    // Ignore
  }
}

export function getStoredSairaRules(): SairaNotificationRule[] {
  try {
    const raw = localStorage.getItem(KEYS.sairaNotifRules);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as SairaNotificationRule[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function saveStoredSairaRules(rules: SairaNotificationRule[]): void {
  try {
    localStorage.setItem(KEYS.sairaNotifRules, JSON.stringify(rules));
  } catch {
    // Ignore
  }
}
