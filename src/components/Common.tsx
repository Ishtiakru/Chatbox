import React from 'react';
import { ExternalLink, LogIn, Search, X } from 'lucide-react';
import type { AvatarStyle, DemoModal, SparkAppUpdate } from '../types/chatbox';
import { APP_VERSION } from '../types/chatbox';

export function ChatboxLogoWithUpdateBadge({
  appUpdateActive,
  onUpdateClick,
  size = 38,
}: {
  appUpdateActive: boolean;
  onUpdateClick: () => void;
  size?: number;
}) {
  return (
    <div className="relative inline-flex items-center gap-1.5">
      <div
        style={{ width: size, height: size }}
        className="rounded-full overflow-hidden border border-[#8b5cf6]/30 bg-white shrink-0 shadow-xs"
      >
        <img
          src="/assets/chatbox_logo_lavender.jpg"
          alt="Chatbox logo"
          className="w-full h-full object-cover"
        />
      </div>
      {appUpdateActive && (
        <button
          type="button"
          onClick={onUpdateClick}
          className="px-2 py-0.5 rounded-md bg-[#16a34a] hover:bg-[#15803d] text-white text-[10px] font-bold tracking-wide uppercase shadow-xs cursor-pointer transition-colors"
        >
          update
        </button>
      )}
    </div>
  );
}

const AVATAR_GRADIENTS: Record<AvatarStyle, string> = {
  SAIRA: 'from-[#ede9fe] to-[#c4b5fd] text-[#5b4a8f]',
  PEACH: 'from-[#f1d9cc] to-[#d5a897] text-[#865e4c]',
  LAVENDER: 'from-[#e6e2ef] to-[#bdb3cf] text-[#665c7a]',
  YELLOW: 'from-[#f9e8c5] to-[#e4c78e] text-[#8b6f3d]',
  BLUE: 'from-[#dce9e4] to-[#b7d4cc] text-[#597e72]',
};

export function DemoAvatar({
  label,
  style,
  online = false,
  size = 42,
}: {
  label: string;
  style: AvatarStyle;
  online?: boolean;
  size?: number;
}) {
  return (
    <div className="relative shrink-0" style={{ width: size, height: size }}>
      <div
        className={`w-full h-full rounded-full bg-gradient-to-br ${AVATAR_GRADIENTS[style]} flex items-center justify-center font-display font-bold text-sm select-none`}
      >
        {label}
      </div>
      {online && (
        <span className="absolute bottom-0 right-0 w-3 h-3 rounded-full bg-[#a48fd8] border-2 border-white" />
      )}
    </div>
  );
}

const MESSENGER_PALETTES = [
  'from-[#ede9fe] to-[#c4b5fd] text-[#4c1d95]',
  'from-[#f1d9cc] to-[#d5a897] text-[#865e4c]',
  'from-[#f9e8c5] to-[#e4c78e] text-[#8b6f3d]',
  'from-[#dce9e4] to-[#b7d4cc] text-[#3f6257]',
  'from-[#e0e7ff] to-[#a5b4fc] text-[#3730a3]',
];

export function MessengerAvatar({
  label,
  size = 38,
}: {
  label: string;
  size?: number;
}) {
  const clean = (label || 'U').trim();
  const initial = clean.slice(0, 1).toUpperCase();
  const code = clean.charCodeAt(0) || 0;
  const palette = MESSENGER_PALETTES[code % MESSENGER_PALETTES.length];
  return (
    <div
      style={{ width: size, height: size, fontSize: Math.max(11, Math.round(size * 0.38)) }}
      className={`rounded-full bg-gradient-to-br ${palette} flex items-center justify-center font-display font-bold shrink-0 select-none`}
    >
      {initial}
    </div>
  );
}

export function NoticeBanner({
  text,
  isError = false,
  className = '',
}: {
  text: string;
  isError?: boolean;
  className?: string;
}) {
  return (
    <div
      className={`rounded-xl px-3.5 py-2.5 text-xs leading-relaxed border ${
        isError
          ? 'bg-[#fff1ed] border-[#f4c7bc] text-[#9b5145]'
          : 'bg-[var(--chatbox-notice-bg)] border-[var(--chatbox-border)] text-[var(--chatbox-notice-ink)]'
      } ${className}`}
    >
      {text}
    </div>
  );
}

export function SearchBar({
  value,
  onChange,
  placeholder,
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
}) {
  return (
    <div className="relative flex items-center">
      <Search className="w-4 h-4 text-[#8f89a3] absolute left-3 pointer-events-none" />
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full h-10 pl-9 pr-9 rounded-xl bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] text-sm text-[var(--chatbox-ink)] placeholder:text-[#8f89a3] focus:outline-none focus:border-[#5b4a8f] transition-colors"
      />
      {value && (
        <button
          type="button"
          onClick={() => onChange('')}
          aria-label="Clear search"
          className="absolute right-2 w-7 h-7 rounded-lg flex items-center justify-center text-[#8f89a3] hover:text-[#2a2340] cursor-pointer"
        >
          <X className="w-4 h-4" />
        </button>
      )}
    </div>
  );
}

export function HighlightText({
  text,
  query,
  highlightClassName = 'bg-[#5b4a8f]/15 text-[#5b4a8f] font-semibold rounded-xs px-0.5',
}: {
  text: string;
  query: string;
  highlightClassName?: string;
}) {
  const trimmed = query.trim();
  if (!trimmed) return <>{text}</>;
  const escaped = trimmed.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const regex = new RegExp(`(${escaped})`, 'gi');
  const parts = text.split(regex);
  return (
    <>
      {parts.map((part, i) =>
        part.toLowerCase() === trimmed.toLowerCase() ? (
          <mark key={i} className={highlightClassName}>
            {part}
          </mark>
        ) : (
          <React.Fragment key={i}>{part}</React.Fragment>
        )
      )}
    </>
  );
}

export function SignInRequiredCard({
  title,
  body,
  icon,
  isBusy,
  onSignIn,
  secondaryButtonLabel,
  onSecondaryClick,
}: {
  title: string;
  body: string;
  icon: React.ReactNode;
  isBusy: boolean;
  onSignIn: () => void;
  secondaryButtonLabel?: string;
  onSecondaryClick?: () => void;
}) {
  return (
    <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-6 max-w-lg mx-auto text-center space-y-4">
      <div className="w-12 h-12 rounded-2xl bg-[#efeaf7] text-[#5b4a8f] mx-auto flex items-center justify-center">
        {icon}
      </div>
      <div className="space-y-1.5">
        <h3 className="font-display text-lg font-bold text-[var(--chatbox-ink)]">{title}</h3>
        <p className="text-sm text-[#6b657d] leading-relaxed">{body}</p>
      </div>
      <div className="flex flex-col sm:flex-row gap-2.5 justify-center pt-1">
        <button
          type="button"
          disabled={isBusy}
          onClick={onSignIn}
          className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl bg-[#6250a0] hover:bg-[#463a76] disabled:opacity-60 text-white text-sm font-medium transition-colors cursor-pointer"
        >
          <LogIn className="w-4 h-4" />
          {isBusy ? 'Signing in…' : 'Sign in with Google'}
        </button>
        {secondaryButtonLabel && onSecondaryClick && (
          <button
            type="button"
            onClick={onSecondaryClick}
            className="inline-flex items-center justify-center px-4 py-2.5 rounded-xl border border-[var(--chatbox-border)] hover:bg-[#f7f5fb] text-sm font-medium text-[#5b4a8f] transition-colors cursor-pointer"
          >
            {secondaryButtonLabel}
          </button>
        )}
      </div>
    </div>
  );
}

export function AppUpdateDetailsDialog({
  appUpdate,
  onDismiss,
}: {
  appUpdate: SparkAppUpdate;
  onDismiss: () => void;
}) {
  return (
    <div
      className="fixed inset-0 z-50 bg-black/45 backdrop-blur-xs flex items-center justify-center p-4"
      onClick={onDismiss}
    >
      <div
        className="max-w-md w-full rounded-2xl bg-white border border-[#ece9f3] p-5 shadow-xl space-y-4"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <span className="inline-block px-2 py-0.5 rounded-md bg-[#dcfce7] text-[#15803d] text-[11px] font-bold uppercase tracking-wide">
              Update Available
            </span>
            <h3 className="font-display text-lg font-bold text-[#2a2340] mt-1">
              Chatbox v{appUpdate.version}
            </h3>
            <p className="text-xs text-[#8f89a3]">Current version: v{APP_VERSION}</p>
          </div>
          <button
            type="button"
            onClick={onDismiss}
            className="p-1.5 rounded-lg hover:bg-[#f6f4fa] text-[#8f89a3] cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {appUpdate.note && (
          <p className="text-sm text-[#3a3347] bg-[#f7f5fb] rounded-xl p-3 border border-[#ece9f3]">
            {appUpdate.note}
          </p>
        )}

        <div className="flex items-center justify-end gap-2">
          {appUpdate.downloadUrl && (
            <a
              href={appUpdate.downloadUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-[#16a34a] hover:bg-[#15803d] text-white text-sm font-medium transition-colors"
            >
              Open Release
              <ExternalLink className="w-3.5 h-3.5" />
            </a>
          )}
          <button
            type="button"
            onClick={onDismiss}
            className="px-4 py-2 rounded-xl bg-[#efeaf7] text-[#5b4a8f] text-sm font-medium hover:bg-[#e6dff5] cursor-pointer"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}

export function DemoInfoModalDialog({
  modal,
  contactName,
  onDismiss,
  onOpenCalls,
}: {
  modal: DemoModal;
  contactName: string;
  onDismiss: () => void;
  onOpenCalls?: () => void;
}) {
  if (!modal) return null;

  const content: Record<
    Exclude<DemoModal, null>,
    { title: string; body: string }
  > = {
    CALL: {
      title: `Voice call with ${contactName}`,
      body: 'Voice calling UI preview is ready in the Calls tab. Live WebRTC signaling & TURN media relay are not connected in this build.',
    },
    VIDEO: {
      title: `Video call with ${contactName}`,
      body: 'Video calling UI preview is ready in the Calls tab. Switch to Live chats for real-time text, images, and voice notes.',
    },
    ATTACH: {
      title: 'Demo attachments',
      body: 'Demo chats store local text messages in your browser. Open Live chats to send real compressed images and voice notes via Firebase Storage.',
    },
    NEW_CHAT: {
      title: 'Start a live conversation',
      body: 'Demo contacts are fixed sample conversations. Open the Live chats tab and enter a friend’s Chatbox ID to start a real-time Firestore chat.',
    },
    ABOUT: {
      title: `About Chatbox v${APP_VERSION}`,
      body: 'Chatbox — a calmer space to stay close, with Demo conversations, Live Firestore messaging (images & voice notes), Saira AI powered by Gemini, and Spark Admin Studio.',
    },
  };

  const info = content[modal];

  return (
    <div
      className="fixed inset-0 z-50 bg-black/45 backdrop-blur-xs flex items-center justify-center p-4"
      onClick={onDismiss}
    >
      <div
        className="max-w-md w-full rounded-2xl bg-white border border-[#ece9f3] p-5 shadow-xl space-y-4"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-2">
          <h3 className="font-display text-lg font-bold text-[#2a2340]">{info.title}</h3>
          <button
            type="button"
            onClick={onDismiss}
            className="p-1.5 rounded-lg hover:bg-[#f6f4fa] text-[#8f89a3] cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
        <p className="text-sm text-[#5a5468] leading-relaxed">{info.body}</p>
        <div className="flex justify-end gap-2">
          {(modal === 'CALL' || modal === 'VIDEO') && onOpenCalls && (
            <button
              type="button"
              onClick={() => {
                onDismiss();
                onOpenCalls();
              }}
              className="px-4 py-2 rounded-xl bg-[#6250a0] text-white text-sm font-medium hover:bg-[#463a76] cursor-pointer"
            >
              Open Calls tab
            </button>
          )}
          <button
            type="button"
            onClick={onDismiss}
            className="px-4 py-2 rounded-xl bg-[#efeaf7] text-[#5b4a8f] text-sm font-medium hover:bg-[#e6dff5] cursor-pointer"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
