import type { Timestamp } from 'firebase/firestore';

export const APP_VERSION = '0.1.0';
export const MAX_DISPLAY_NAME_LENGTH = 80;
export const MAX_ABOUT_LENGTH = 140;
export const MAX_PHONE_LENGTH = 24;
export const MAX_MESSAGE_BODY_LENGTH = 2000;
export const MAX_WELCOME_LENGTH = 160;
export const MAX_UPDATE_VERSION_LENGTH = 32;
export const MAX_UPDATE_NOTE_LENGTH = 240;
export const MAX_UPDATE_URL_LENGTH = 512;
export const VALID_ID_REGEX = /^[a-zA-Z0-9_-]{1,128}$/;
export const VALID_SHORT_ID_REGEX = /^[0-9]{6}$/;
export const VALID_PHONE_KEY_REGEX = /^[0-9]{7,15}$/;
export const VALID_GMAIL_REGEX = /^[a-z0-9][a-z0-9._%+-]{0,63}@gmail\.com$/;

export type CoreAppTab = 'demo' | 'live' | 'saira' | 'calls' | 'admin';
export type AppTab = CoreAppTab | `custom:${string}`;

export type CustomMenuIconName =
  | 'sparkles'
  | 'bell'
  | 'megaphone'
  | 'book'
  | 'star'
  | 'info'
  | 'grid';

export interface SparkCustomMenu {
  id: string;
  label: string;
  icon: CustomMenuIconName;
  title: string;
  body: string;
}

export interface SparkDynamicConfig {
  appTitle: string;
  homeAnnouncement: string;
  customMenus: SparkCustomMenu[];
}

export interface QeReleaseInfo {
  version: string;
  filename: string;
  sizeBytes: number;
  fileCount: number;
  note: string;
  createdAt: string;
  downloadUrl: string;
}

export type AvatarStyle = 'SAIRA' | 'PEACH' | 'LAVENDER' | 'YELLOW' | 'BLUE';

export interface DemoContact {
  id: string;
  name: string;
  role: string;
  avatar: string;
  style: AvatarStyle;
  label?: string;
  online: boolean;
}

export interface DemoMessage {
  from: 'me' | 'them';
  text: string;
  time: string;
}

export type DemoModal = 'CALL' | 'VIDEO' | 'ATTACH' | 'NEW_CHAT' | 'ABOUT' | null;

export interface UserProfile {
  uid: string;
  displayName: string;
  shortId?: string;
  about?: string;
}

export interface UserPrivateProfile {
  phoneNumber: string;
}

export interface Conversation {
  id: string;
  participants: string[];
  createdBy: string;
  status: 'pending' | 'active' | 'closed';
  createdAt: Timestamp | null;
  updatedAt: Timestamp | null;
}

export interface LiveMessage {
  id: string;
  senderId: string;
  body: string;
  imageUrl?: string | null;
  audioUrl?: string | null;
  audioDurationMs?: number | null;
  sentAt: Timestamp | null;
  pendingWrites: boolean;
}

export interface SparkDelegation {
  email: string;
  uid: string;
  invitedBy: string;
  status: 'invited' | 'active' | 'revoked';
  createdAt: Timestamp | null;
  updatedAt: Timestamp | null;
}

export interface SparkAppearance {
  theme: 'default' | 'lavender';
  menu: 'bottom' | 'top';
}

export interface SparkAppUpdate {
  active: boolean;
  version: string;
  note: string;
  downloadUrl: string;
}

export type SparkCommandProposal =
  | { kind: 'setWelcome'; value: string }
  | { kind: 'setTheme'; value: 'default' | 'lavender' }
  | { kind: 'setMenu'; value: 'bottom' | 'top' }
  | { kind: 'setAppUpdate'; active: boolean; version: string; note: string; downloadUrl: string }
  | { kind: 'unsupported' };

export interface AdminAiUpdatePlan {
  summary: string;
  theme?: 'default' | 'lavender';
  menuPosition?: 'bottom' | 'top';
  welcomeBanner?: string;
  appTitle?: string;
  homeAnnouncement?: string;
  addOrUpdateMenu?: SparkCustomMenu;
  removeMenuId?: string;
  appUpdate?: {
    active: boolean;
    version: string;
    note: string;
  };
}

export type AdminAuthStatus =
  | 'IDLE'
  | 'CHECKING'
  | 'OWNER'
  | 'DELEGATE'
  | 'INVITED_DELEGATE'
  | 'UNCLAIMED_OWNER'
  | 'DENIED'
  | 'ERROR';

export interface AiExchange {
  id: string;
  question: string;
  answer: string;
  isStreaming?: boolean;
}

export interface SairaChatSession {
  id: string;
  title: string;
  lastPreview: string;
  exchangeCount: number;
  createdAt: Timestamp | null;
  updatedAt: Timestamp | null;
}

export type SairaPlatform = 'ANY' | 'WHATSAPP' | 'MESSENGER';

export const SAIRA_PLATFORM_LABELS: Record<SairaPlatform, string> = {
  ANY: 'WhatsApp & Messenger',
  WHATSAPP: 'WhatsApp',
  MESSENGER: 'Messenger',
};

export interface SairaNotificationRule {
  id: string;
  contactName: string;
  platform: SairaPlatform;
  taughtReplyGuidance: string;
  enabled: boolean;
}

export interface SairaCompanionMessage {
  id: string;
  text: string;
  isFromUser: boolean;
  timestampMs: number;
}
