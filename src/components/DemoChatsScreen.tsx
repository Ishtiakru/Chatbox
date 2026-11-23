import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowLeft,
  ArrowUp,
  HelpCircle,
  Info,
  LogOut,
  Paperclip,
  Phone,
  Plus,
  Smile,
  Video,
} from 'lucide-react';
import {
  DEMO_CONTACTS,
  getSairaOfflineGuideReply,
} from '../data/demoCatalog';
import {
  getStoredDemoMessages,
  saveStoredDemoMessages,
} from '../services/storagePrefs';
import type { DemoContact, DemoMessage, DemoModal } from '../types/chatbox';
import {
  ChatboxLogoWithUpdateBadge,
  DemoAvatar,
  DemoInfoModalDialog,
  HighlightText,
  NoticeBanner,
  SearchBar,
} from './Common';

export function DemoChatsScreen({
  publicWelcomeBanner,
  userDisplayName,
  userEmail,
  isOnline,
  appUpdateActive,
  onAppUpdateClick,
  onSignOut,
  onOpenCalls,
  selectedContactId,
  onSelectContactId,
  activeModal,
  onSetModal,
}: {
  publicWelcomeBanner: string;
  userDisplayName: string | null;
  userEmail: string | null;
  isOnline: boolean;
  appUpdateActive: boolean;
  onAppUpdateClick: () => void;
  onSignOut: () => void;
  onOpenCalls: () => void;
  selectedContactId: string;
  onSelectContactId: (id: string) => void;
  activeModal: DemoModal;
  onSetModal: (modal: DemoModal) => void;
}) {
  const [messagesByContact, setMessagesByContact] = useState<Record<string, DemoMessage[]>>(() =>
    getStoredDemoMessages()
  );
  const [searchQuery, setSearchQuery] = useState('');
  const [draft, setDraft] = useState('');
  const [mobilePane, setMobilePane] = useState<'list' | 'thread'>('list');
  const [showDetails, setShowDetails] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const searchInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      const isTypingField =
        target?.tagName === 'INPUT' ||
        target?.tagName === 'TEXTAREA' ||
        target?.isContentEditable;
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setMobilePane('list');
        window.setTimeout(() => searchInputRef.current?.focus(), 0);
      } else if (e.key === '/' && !isTypingField) {
        e.preventDefault();
        setMobilePane('list');
        window.setTimeout(() => searchInputRef.current?.focus(), 0);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  const selectedContact: DemoContact = useMemo(
    () => DEMO_CONTACTS.find((c) => c.id === selectedContactId) ?? DEMO_CONTACTS[0],
    [selectedContactId]
  );

  const filteredContacts = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return DEMO_CONTACTS;
    return DEMO_CONTACTS.filter((contact) => {
      const msgs = messagesByContact[contact.id] ?? [];
      return (
        contact.name.toLowerCase().includes(q) ||
        contact.role.toLowerCase().includes(q) ||
        msgs.some((m) => m.text.toLowerCase().includes(q))
      );
    });
  }, [searchQuery, messagesByContact]);

  const activeMessages = useMemo(
    () => messagesByContact[selectedContact.id] ?? [],
    [messagesByContact, selectedContact.id]
  );

  const handleSelectContact = (id: string) => {
    onSelectContactId(id);
    setMobilePane('thread');
  };

  const handleSend = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const trimmed = draft.trim();
    if (!trimmed) return;
    const nowTime = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const nextUserMsg: DemoMessage = { from: 'me', text: trimmed, time: nowTime };
    const existing = messagesByContact[selectedContact.id] ?? [];
    const updatedList = [...existing, nextUserMsg];

    if (selectedContact.id === 'saira') {
      updatedList.push({
        from: 'them',
        text: getSairaOfflineGuideReply(trimmed),
        time: nowTime,
      });
    }

    const nextMap = { ...messagesByContact, [selectedContact.id]: updatedList };
    setMessagesByContact(nextMap);
    saveStoredDemoMessages(nextMap);
    setDraft('');
    setTimeout(() => messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }), 40);
  };

  return (
    <div className="h-full flex bg-[var(--chatbox-bg)] overflow-hidden">
      {/* Left Conversation List */}
      <aside
        className={`${
          mobilePane === 'list' ? 'flex' : 'hidden'
        } md:flex flex-col w-full md:w-[320px] lg:w-[340px] shrink-0 border-r border-[var(--chatbox-border)] bg-[var(--chatbox-surface)]`}
      >
        <div className="p-4 border-b border-[var(--chatbox-border)] space-y-3">
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2.5 min-w-0">
              <ChatboxLogoWithUpdateBadge
                appUpdateActive={appUpdateActive}
                onUpdateClick={onAppUpdateClick}
                size={34}
              />
              <div className="min-w-0">
                <p className="text-[11px] font-semibold uppercase tracking-wider text-[#8f89a3]">
                  Demo Explorer
                </p>
                <h2 className="font-display text-lg font-bold text-[var(--chatbox-ink)] truncate">
                  Demo Chats
                </h2>
              </div>
            </div>
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => onSetModal('NEW_CHAT')}
                title="New conversation"
                className="w-9 h-9 rounded-xl hover:bg-[#f7f5fb] text-[#5b4a8f] flex items-center justify-center cursor-pointer"
              >
                <Plus className="w-4 h-4" />
              </button>
              <button
                type="button"
                onClick={() => onSetModal('ABOUT')}
                title="About Chatbox"
                className="w-9 h-9 rounded-xl hover:bg-[#f7f5fb] text-[#8f89a3] flex items-center justify-center cursor-pointer"
              >
                <HelpCircle className="w-4 h-4" />
              </button>
            </div>
          </div>

          <div className="space-y-1.5">
            <SearchBar
              value={searchQuery}
              onChange={setSearchQuery}
              placeholder="Search conversations by name or message…"
              ariaLabel="Global search across Demo Chats by contact name or message content"
              inputRef={searchInputRef}
            />
            {searchQuery.trim() && (
              <p className="text-[11px] font-medium text-[#5b4a8f] px-1">
                {filteredContacts.length === 0
                  ? 'No matching conversations'
                  : `${filteredContacts.length} matching ${
                      filteredContacts.length === 1 ? 'conversation' : 'conversations'
                    }`}
              </p>
            )}
          </div>

          {publicWelcomeBanner && (
            <NoticeBanner text={publicWelcomeBanner} />
          )}
          {!isOnline && (
            <NoticeBanner
              isError
              text="Offline · Demo mode works locally on this device."
            />
          )}
        </div>

        <div className="flex-1 overflow-y-auto p-2 space-y-1">
          {filteredContacts.length === 0 ? (
            <div className="p-6 text-center space-y-2.5">
              <p className="text-sm font-medium text-[var(--chatbox-ink)]">
                No conversations match &ldquo;{searchQuery.trim()}&rdquo;
              </p>
              <p className="text-xs text-[#8f89a3]">
                Try another contact name or message keyword.
              </p>
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                className="inline-flex items-center px-3 py-1.5 rounded-xl bg-[#efeaf7] hover:bg-[#e6dff5] text-[#5b4a8f] text-xs font-semibold cursor-pointer"
              >
                Clear search
              </button>
            </div>
          ) : (
            filteredContacts.map((contact) => {
              const msgs = messagesByContact[contact.id] ?? [];
              const q = searchQuery.trim().toLowerCase();
              const matchedMsg = q
                ? [...msgs].reverse().find((m) => m.text.toLowerCase().includes(q))
                : undefined;
              const previewMsg = matchedMsg ?? msgs[msgs.length - 1];
              const selected = contact.id === selectedContact.id;
              return (
                <button
                  key={contact.id}
                  type="button"
                  onClick={() => handleSelectContact(contact.id)}
                  className={`w-full flex items-center gap-3 p-3 rounded-2xl text-left transition-colors cursor-pointer ${
                    selected
                      ? 'bg-[var(--chatbox-selected-bg)]'
                      : 'hover:bg-[var(--chatbox-field-bg)]'
                  }`}
                >
                  <DemoAvatar
                    label={contact.avatar}
                    style={contact.style}
                    online={contact.online}
                  />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-display text-sm font-semibold text-[var(--chatbox-ink)] truncate">
                        <HighlightText text={contact.name} query={searchQuery} />
                      </span>
                      {contact.label && (
                        <span className="px-1.5 py-0.5 rounded-md bg-[#efeaf7] text-[#5b4a8f] text-[10px] font-bold uppercase shrink-0">
                          {contact.label}
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-[#6b657d] truncate mt-0.5">
                      {previewMsg ? (
                        <>
                          {previewMsg.from === 'me' && <span>You: </span>}
                          <HighlightText text={previewMsg.text} query={searchQuery} />
                        </>
                      ) : (
                        <HighlightText text={contact.role} query={searchQuery} />
                      )}
                    </p>
                  </div>
                </button>
              );
            })
          )}
        </div>

        {/* Account Footer */}
        <div className="p-3 border-t border-[var(--chatbox-border)] flex items-center justify-between gap-2 bg-[var(--chatbox-field-bg)]">
          <div className="min-w-0">
            <p className="text-xs font-semibold text-[var(--chatbox-ink)] truncate">
              {userDisplayName || 'Chatbox User'}
            </p>
            {userEmail && (
              <p className="text-[11px] text-[#8f89a3] truncate">{userEmail}</p>
            )}
          </div>
          <button
            type="button"
            onClick={onSignOut}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-[var(--chatbox-border)] bg-white hover:bg-[#fff1ed] hover:text-[#9b5145] text-xs font-medium text-[#5a5468] transition-colors cursor-pointer shrink-0"
          >
            <LogOut className="w-3.5 h-3.5" />
            Sign out
          </button>
        </div>
      </aside>

      {/* Center Thread Pane */}
      <main
        className={`${
          mobilePane === 'thread' ? 'flex' : 'hidden'
        } md:flex flex-1 flex-col min-w-0 bg-[var(--chatbox-bg)]`}
      >
        {/* Thread Header */}
        <header className="px-4 py-3 border-b border-[var(--chatbox-border)] bg-[var(--chatbox-surface)] flex items-center justify-between gap-2">
          <div className="flex items-center gap-3 min-w-0">
            <button
              type="button"
              onClick={() => setMobilePane('list')}
              className="md:hidden w-9 h-9 rounded-xl hover:bg-[#f7f5fb] flex items-center justify-center text-[#5b4a8f] cursor-pointer"
              aria-label="Back"
            >
              <ArrowLeft className="w-5 h-5" />
            </button>
            <DemoAvatar
              label={selectedContact.avatar}
              style={selectedContact.style}
              online={selectedContact.online}
              size={38}
            />
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <h3 className="font-display text-sm font-bold text-[var(--chatbox-ink)] truncate">
                  {selectedContact.name}
                </h3>
                {selectedContact.label && (
                  <span className="px-1.5 py-0.5 rounded-md bg-[#efeaf7] text-[#5b4a8f] text-[10px] font-bold">
                    {selectedContact.label}
                  </span>
                )}
              </div>
              <p className="text-xs text-[#8f89a3] truncate">{selectedContact.role}</p>
            </div>
          </div>

          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => onSetModal('CALL')}
              title="Voice call"
              className="w-9 h-9 rounded-xl hover:bg-[#f7f5fb] text-[#5b4a8f] flex items-center justify-center cursor-pointer"
            >
              <Phone className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={() => onSetModal('VIDEO')}
              title="Video call"
              className="w-9 h-9 rounded-xl hover:bg-[#f7f5fb] text-[#5b4a8f] flex items-center justify-center cursor-pointer"
            >
              <Video className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={() => setShowDetails((v) => !v)}
              title="Conversation information"
              className={`w-9 h-9 rounded-xl flex items-center justify-center cursor-pointer ${
                showDetails
                  ? 'bg-[#efeaf7] text-[#5b4a8f]'
                  : 'hover:bg-[#f7f5fb] text-[#5b4a8f]'
              }`}
            >
              <Info className="w-4 h-4" />
            </button>
          </div>
        </header>

        {/* Messages */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          <NoticeBanner
            text="Local Demo preview — messages stay saved in this browser. Use Live chats for real-time Firestore messaging."
          />
          {activeMessages.map((msg, idx) => {
            const isMe = msg.from === 'me';
            return (
              <div
                key={idx}
                className={`flex flex-col ${isMe ? 'items-end' : 'items-start'}`}
              >
                <div
                  className={`max-w-[80%] sm:max-w-[460px] px-4 py-2.5 rounded-2xl text-sm leading-relaxed ${
                    isMe
                      ? 'bg-[var(--chatbox-outgoing-bubble)] text-[var(--chatbox-outgoing-text)] rounded-br-xs'
                      : 'bg-[var(--chatbox-incoming-bubble)] text-[var(--chatbox-incoming-text)] rounded-bl-xs'
                  }`}
                >
                  <HighlightText
                    text={msg.text}
                    query={searchQuery}
                    highlightClassName={
                      isMe
                        ? 'bg-[#5b4a8f]/25 text-[#2a2340] font-semibold rounded-xs px-0.5'
                        : 'bg-[#5b4a8f]/15 text-[#5b4a8f] font-semibold rounded-xs px-0.5'
                    }
                  />
                </div>
                <span className="text-[11px] text-[#8f89a3] mt-1 px-1">{msg.time}</span>
              </div>
            );
          })}
          <div ref={messagesEndRef} />
        </div>

        {/* Composer */}
        <form
          onSubmit={handleSend}
          className="p-3 border-t border-[var(--chatbox-border)] bg-[var(--chatbox-surface)] flex items-center gap-2"
        >
          <button
            type="button"
            onClick={() => onSetModal('ATTACH')}
            title="Attach a file"
            className="w-10 h-10 rounded-full hover:bg-[#f7f5fb] text-[#5b4a8f] flex items-center justify-center shrink-0 cursor-pointer"
          >
            <Paperclip className="w-4 h-4" />
          </button>
          <input
            type="text"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder={`Message ${selectedContact.name}…`}
            className="flex-1 h-10 px-4 rounded-full bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] text-sm text-[var(--chatbox-ink)] placeholder:text-[#8f89a3] focus:outline-none focus:border-[#5b4a8f]"
          />
          <button
            type="button"
            onClick={() => setDraft((prev) => `${prev}${prev && !prev.endsWith(' ') ? ' ' : ''}😊`)}
            title="Add a smile"
            className="w-10 h-10 rounded-full hover:bg-[#f7f5fb] text-[#5b4a8f] flex items-center justify-center shrink-0 cursor-pointer"
          >
            <Smile className="w-4 h-4" />
          </button>
          <button
            type="submit"
            disabled={!draft.trim()}
            aria-label="Send"
            className="w-10 h-10 rounded-full bg-[#6250a0] hover:bg-[#463a76] disabled:bg-[#ece9f3] disabled:text-[#8f89a3] text-white flex items-center justify-center shrink-0 transition-colors cursor-pointer"
          >
            <ArrowUp className="w-4 h-4" />
          </button>
        </form>
      </main>

      {/* Optional Right Details Pane */}
      {showDetails && (
        <aside className="hidden lg:flex w-[280px] shrink-0 flex-col border-l border-[var(--chatbox-border)] bg-[var(--chatbox-surface)] p-4 space-y-4">
          <div className="flex flex-col items-center text-center pt-2 space-y-2">
            <DemoAvatar
              label={selectedContact.avatar}
              style={selectedContact.style}
              online={selectedContact.online}
              size={60}
            />
            <h4 className="font-display text-base font-bold text-[var(--chatbox-ink)]">
              {selectedContact.name}
            </h4>
            <p className="text-xs text-[#8f89a3]">{selectedContact.role}</p>
          </div>
          <div className="border-t border-[var(--chatbox-border)] pt-4 space-y-2">
            <p className="text-xs text-[#5a5468] leading-relaxed">
              This is a local Demo workspace. Switch to the Saira contact for an offline guide or open Live chats to message friends in real time.
            </p>
            {selectedContact.id !== 'saira' && (
              <button
                type="button"
                onClick={() => handleSelectContact('saira')}
                className="w-full py-2.5 rounded-xl bg-[#efeaf7] hover:bg-[#e6dff5] text-[#5b4a8f] text-xs font-semibold transition-colors cursor-pointer"
              >
                Say hello to Saira
              </button>
            )}
          </div>
        </aside>
      )}

      <DemoInfoModalDialog
        modal={activeModal}
        contactName={selectedContact.name}
        onDismiss={() => onSetModal(null)}
        onOpenCalls={onOpenCalls}
      />
    </div>
  );
}
