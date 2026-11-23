import React, { useEffect, useRef, useState } from 'react';
import {
  ArrowUp,
  Bell,
  Clock,
  Plus,
  RefreshCw,
  SlidersHorizontal,
  Sparkles,
  Trash2,
} from 'lucide-react';
import { streamSairaChat } from '../services/sairaAiService';
import {
  getStoredSairaNotifReplyEnabled,
  getStoredSairaOneMinEnabled,
  getStoredSairaRules,
  saveStoredSairaNotifReplyEnabled,
  saveStoredSairaOneMinEnabled,
  saveStoredSairaRules,
} from '../services/storagePrefs';
import type {
  AiExchange,
  SairaNotificationRule,
  SairaPlatform,
} from '../types/chatbox';
import { SAIRA_PLATFORM_LABELS } from '../types/chatbox';
import { ChatboxLogoWithUpdateBadge, NoticeBanner } from './Common';

export function SairaAiScreen({
  isOnline,
  appUpdateActive,
  onAppUpdateClick,
  oneMinAutoReplyEnabled,
  onChangeOneMinAutoReply,
}: {
  isOnline: boolean;
  appUpdateActive: boolean;
  onAppUpdateClick: () => void;
  oneMinAutoReplyEnabled: boolean;
  onChangeOneMinAutoReply: (enabled: boolean) => void;
}) {
  const [selectedTab, setSelectedTab] = useState<'chat' | 'autoReply'>('chat');
  const [draft, setDraft] = useState('');
  const [exchanges, setExchanges] = useState<AiExchange[]>([]);
  const [isBusy, setIsBusy] = useState(false);
  const [error, setError] = useState('');

  const [notifAutoReply, setNotifAutoReply] = useState<boolean>(() =>
    getStoredSairaNotifReplyEnabled()
  );
  const [rules, setRules] = useState<SairaNotificationRule[]>(() => getStoredSairaRules());
  const [ruleContactDraft, setRuleContactDraft] = useState('');
  const [rulePlatformDraft, setRulePlatformDraft] = useState<SairaPlatform>('ANY');
  const [ruleGuidanceDraft, setRuleGuidanceDraft] = useState('');
  const abortRef = useRef<AbortController | null>(null);
  const endRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [exchanges]);

  const handleSubmit = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const question = draft.trim();
    if (isBusy || !question || !isOnline || question.length > 2000) return;

    const exchangeId = `${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
    const isFirstTurn = exchanges.length === 0;
    const prevExchanges = exchanges;

    setDraft('');
    setIsBusy(true);
    setError('');
    setExchanges((prev) => [...prev, { id: exchangeId, question, answer: '', isStreaming: true }]);

    const controller = new AbortController();
    abortRef.current = controller;

    try {
      const finalAnswer = await streamSairaChat({
        question,
        history: prevExchanges,
        isFirstTurn,
        signal: controller.signal,
        onChunk: (accumulated) => {
          setExchanges((prev) =>
            prev.map((item) =>
              item.id === exchangeId ? { ...item, answer: accumulated, isStreaming: true } : item
            )
          );
        },
      });
      if (finalAnswer) {
        setExchanges((prev) =>
          prev.map((item) =>
            item.id === exchangeId ? { ...item, answer: finalAnswer, isStreaming: false } : item
          )
        );
      } else {
        setExchanges((prev) => prev.filter((item) => item.id !== exchangeId));
        setError('Saira returned an empty response. Please try again.');
      }
    } catch (err) {
      setExchanges((prev) => prev.filter((item) => item.id !== exchangeId || Boolean(item.answer)));
      setExchanges((prev) => prev.map((item) => ({ ...item, isStreaming: false })));
      setError(err instanceof Error ? err.message : 'Saira AI is unavailable right now.');
    } finally {
      setIsBusy(false);
      abortRef.current = null;
    }
  };

  const handleReset = () => {
    abortRef.current?.abort();
    setExchanges([]);
    setError('');
    setIsBusy(false);
  };

  const handleToggleOneMin = (next: boolean) => {
    onChangeOneMinAutoReply(next);
    saveStoredSairaOneMinEnabled(next);
  };

  const handleToggleNotifReply = (next: boolean) => {
    setNotifAutoReply(next);
    saveStoredSairaNotifReplyEnabled(next);
  };

  const handleAddRule = (e: React.FormEvent) => {
    e.preventDefault();
    const contactName = ruleContactDraft.trim().slice(0, 80);
    const taughtReplyGuidance = ruleGuidanceDraft.trim().slice(0, 400);
    if (!contactName || !taughtReplyGuidance) return;
    const nextRules: SairaNotificationRule[] = [
      ...rules,
      {
        id: `${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
        contactName,
        platform: rulePlatformDraft,
        taughtReplyGuidance,
        enabled: true,
      },
    ];
    setRules(nextRules);
    saveStoredSairaRules(nextRules);
    setRuleContactDraft('');
    setRuleGuidanceDraft('');
  };

  const activeRuleCount = rules.filter((r) => r.enabled).length;

  return (
    <div className="h-full overflow-y-auto bg-[var(--chatbox-bg)] px-4 py-4 flex justify-center">
      <div className="w-full max-w-[820px] flex flex-col h-full min-h-0 space-y-3">
        {/* Header */}
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            <ChatboxLogoWithUpdateBadge
              appUpdateActive={appUpdateActive}
              onUpdateClick={onAppUpdateClick}
              size={34}
            />
            <div className="min-w-0">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-[#8f89a3]">
                FREE UNLIMITED ASSISTANT
              </p>
              <div className="flex items-center gap-2 flex-wrap">
                <h1 className="font-display text-xl font-bold text-[var(--chatbox-ink)]">
                  Saira Assistant
                </h1>
                <span className="px-2 py-0.5 rounded-md bg-[#efeaf7] text-[#5b4a8f] text-[10px] font-bold uppercase tracking-wide">
                  UNLIMITED
                </span>
              </div>
              <p className="text-xs text-[#8f89a3] truncate">
                Replies after your message · Time-aware greetings & auto-reply
              </p>
            </div>
          </div>

          {selectedTab === 'chat' && exchanges.length > 0 ? (
            <button
              type="button"
              onClick={handleReset}
              title="Start fresh Saira chat"
              className="w-10 h-10 rounded-xl hover:bg-[#efeaf7] text-[#5b4a8f] flex items-center justify-center shrink-0 cursor-pointer"
            >
              <RefreshCw className="w-4 h-4" />
            </button>
          ) : (
            <div className="w-10 h-10 rounded-xl bg-[#efeaf7] text-[#5b4a8f] flex items-center justify-center shrink-0">
              <Sparkles className="w-4 h-4" />
            </div>
          )}
        </div>

        {/* Sub-tabs */}
        <div className="grid grid-cols-2 p-1 rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)]">
          <button
            type="button"
            onClick={() => setSelectedTab('chat')}
            className={`py-2.5 rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors cursor-pointer ${
              selectedTab === 'chat'
                ? 'bg-[#efeaf7] text-[#5b4a8f]'
                : 'text-[#6b657d] hover:text-[var(--chatbox-ink)]'
            }`}
          >
            <Sparkles className="w-4 h-4" />
            Assistant Chat
          </button>
          <button
            type="button"
            onClick={() => setSelectedTab('autoReply')}
            className={`py-2.5 rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors cursor-pointer ${
              selectedTab === 'autoReply'
                ? 'bg-[#efeaf7] text-[#5b4a8f]'
                : 'text-[#6b657d] hover:text-[var(--chatbox-ink)]'
            }`}
          >
            <SlidersHorizontal className="w-4 h-4" />
            {activeRuleCount > 0 ? `Auto-Reply (${activeRuleCount})` : 'Auto-Reply'}
          </button>
        </div>

        {selectedTab === 'chat' ? (
          <div className="flex-1 min-h-0 flex flex-col space-y-2.5">
            {!isOnline && (
              <NoticeBanner
                isError
                text="Offline · Saira Assistant is disabled without internet."
              />
            )}
            <NoticeBanner text="Powered by Gemini AI. Saira never sends a message first — she replies only after you write." />
            {error && <NoticeBanner isError text={error} />}

            <div className="flex-1 min-h-[300px] rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] flex flex-col overflow-hidden">
              <div className="flex-1 overflow-y-auto p-4 space-y-4">
                {exchanges.length === 0 ? (
                  <div className="py-10 text-center max-w-md mx-auto space-y-2">
                    <div className="w-11 h-11 rounded-2xl bg-[#efeaf7] text-[#5b4a8f] mx-auto flex items-center justify-center">
                      <Sparkles className="w-5 h-5" />
                    </div>
                    <h3 className="font-display text-sm font-bold text-[var(--chatbox-ink)]">
                      Saira Assistant is quiet until you message first.
                    </h3>
                    <p className="text-xs text-[#6b657d] leading-relaxed">
                      Send any question in Bangla or English. Saira will greet you by time of day (Morning, Afternoon, Evening) and say Good Night when wrapping up at night.
                    </p>
                  </div>
                ) : (
                  exchanges.map((ex) => (
                    <div key={ex.id} className="space-y-2">
                      <div className="flex justify-end">
                        <div className="max-w-[82%] sm:max-w-[460px] px-4 py-2.5 rounded-2xl rounded-br-xs bg-[var(--chatbox-outgoing-bubble)] text-[var(--chatbox-outgoing-text)] text-sm whitespace-pre-wrap">
                          {ex.question}
                        </div>
                      </div>
                      <div className="flex justify-start">
                        <div className="max-w-[88%] sm:max-w-[540px] px-4 py-2.5 rounded-2xl rounded-bl-xs bg-[var(--chatbox-incoming-bubble)] text-[var(--chatbox-incoming-text)] text-sm whitespace-pre-wrap leading-relaxed">
                          {!ex.answer && ex.isStreaming ? '…' : ex.answer}
                        </div>
                      </div>
                    </div>
                  ))
                )}
                <div ref={endRef} />
              </div>

              <form
                onSubmit={handleSubmit}
                className="p-3 border-t border-[var(--chatbox-border)] space-y-1.5"
              >
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    value={draft}
                    disabled={!isOnline || isBusy}
                    onChange={(e) => setDraft(e.target.value.slice(0, 2000))}
                    placeholder={isOnline ? 'Message Saira Assistant…' : 'Offline · Saira disabled'}
                    className="flex-1 h-11 px-4 rounded-full bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] text-sm text-[var(--chatbox-ink)] placeholder:text-[#8f89a3] focus:outline-none focus:border-[#5b4a8f] disabled:opacity-60"
                  />
                  <button
                    type="submit"
                    disabled={!isOnline || isBusy || !draft.trim()}
                    aria-label="Send"
                    className="w-11 h-11 rounded-full bg-[#6250a0] hover:bg-[#463a76] disabled:bg-[#ece9f3] disabled:text-[#8f89a3] text-white flex items-center justify-center shrink-0 transition-colors cursor-pointer"
                  >
                    <ArrowUp className="w-4 h-4" />
                  </button>
                </div>
                <p className="text-[11px] text-[#8f89a3] px-1">
                  {isBusy
                    ? 'Saira Assistant is typing…'
                    : 'Only messages you send here go to Gemini.'}
                </p>
              </form>
            </div>
          </div>
        ) : (
          <div className="flex-1 overflow-y-auto space-y-3 pb-4">
            {/* 1-Min Auto-Reply Card */}
            <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-4 flex items-center gap-3">
              <Clock className="w-5 h-5 text-[#5b4a8f] shrink-0" />
              <div className="flex-1 min-w-0">
                <h3 className="font-display text-sm font-bold text-[var(--chatbox-ink)]">
                  Chatbox 1-Min Absence Auto-Reply
                </h3>
                <p className="text-xs text-[#6b657d] mt-0.5">
                  If a friend hasn’t replied in Live chats for 1 minute, Saira greets you by name and assists while you wait.
                </p>
              </div>
              <input
                type="checkbox"
                checked={oneMinAutoReplyEnabled}
                onChange={(e) => handleToggleOneMin(e.target.checked)}
                className="w-5 h-5 accent-[#6250a0] cursor-pointer shrink-0"
              />
            </div>

            {/* Notification Auto-Reply Card */}
            <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-4 space-y-3">
              <div className="flex items-center gap-3">
                <Bell className="w-5 h-5 text-[#5b4a8f] shrink-0" />
                <div className="flex-1 min-w-0">
                  <h3 className="font-display text-sm font-bold text-[var(--chatbox-ink)]">
                    WhatsApp & Messenger Auto-Reply Rules
                  </h3>
                  <p className="text-xs text-[#6b657d] mt-0.5">
                    Configure taught auto-reply instructions for specific contacts.
                  </p>
                </div>
                <input
                  type="checkbox"
                  checked={notifAutoReply}
                  onChange={(e) => handleToggleNotifReply(e.target.checked)}
                  className="w-5 h-5 accent-[#6250a0] cursor-pointer shrink-0"
                />
              </div>
              <NoticeBanner text="Note: External Android notification interception runs natively on Android devices; your saved rules remain stored locally in this browser." />
            </div>

            {/* Teach Rule Form */}
            <form
              onSubmit={handleAddRule}
              className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-4 space-y-3"
            >
              <h3 className="font-display text-sm font-bold text-[var(--chatbox-ink)]">
                Teach Saira a Contact Rule
              </h3>
              <input
                type="text"
                value={ruleContactDraft}
                onChange={(e) => setRuleContactDraft(e.target.value.slice(0, 80))}
                placeholder="Contact name (e.g., Rahima)"
                className="w-full h-10 px-3.5 rounded-xl bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] text-sm focus:outline-none focus:border-[#5b4a8f]"
              />
              <div className="flex flex-wrap gap-2">
                {(['ANY', 'WHATSAPP', 'MESSENGER'] as SairaPlatform[]).map((platform) => (
                  <button
                    key={platform}
                    type="button"
                    onClick={() => setRulePlatformDraft(platform)}
                    className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors cursor-pointer ${
                      rulePlatformDraft === platform
                        ? 'bg-[#efeaf7] border-[#5b4a8f] text-[#5b4a8f]'
                        : 'bg-white border-[var(--chatbox-border)] text-[#6b657d]'
                    }`}
                  >
                    {SAIRA_PLATFORM_LABELS[platform]}
                  </button>
                ))}
              </div>
              <textarea
                rows={2}
                value={ruleGuidanceDraft}
                onChange={(e) => setRuleGuidanceDraft(e.target.value.slice(0, 400))}
                placeholder="What should Saira reply when they message? (e.g., Tell them I am busy right now and will call back soon.)"
                className="w-full p-3 rounded-xl bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] text-sm focus:outline-none focus:border-[#5b4a8f]"
              />
              <button
                type="submit"
                disabled={!ruleContactDraft.trim() || !ruleGuidanceDraft.trim()}
                className="w-full py-2.5 rounded-xl bg-[#6250a0] hover:bg-[#463a76] disabled:opacity-50 text-white text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
              >
                <Plus className="w-4 h-4" />
                Save Reply Rule for This Contact
              </button>
            </form>

            {rules.length > 0 && (
              <div className="space-y-2">
                <p className="text-[11px] font-semibold uppercase tracking-wider text-[#8f89a3] px-1">
                  TAUGHT CONTACT RULES ({rules.length})
                </p>
                {rules.map((rule) => (
                  <div
                    key={rule.id}
                    className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-3.5 flex items-center gap-3"
                  >
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-display text-sm font-bold text-[var(--chatbox-ink)]">
                          {rule.contactName}
                        </span>
                        <span className="px-2 py-0.5 rounded-md bg-[#efeaf7] text-[#5b4a8f] text-[10px] font-semibold">
                          {SAIRA_PLATFORM_LABELS[rule.platform]}
                        </span>
                      </div>
                      <p className="text-xs text-[#6b657d] mt-1">{rule.taughtReplyGuidance}</p>
                    </div>
                    <input
                      type="checkbox"
                      checked={rule.enabled}
                      onChange={(e) => {
                        const updated = rules.map((r) =>
                          r.id === rule.id ? { ...r, enabled: e.target.checked } : r
                        );
                        setRules(updated);
                        saveStoredSairaRules(updated);
                      }}
                      className="w-4 h-4 accent-[#6250a0] cursor-pointer"
                    />
                    <button
                      type="button"
                      onClick={() => {
                        const updated = rules.filter((r) => r.id !== rule.id);
                        setRules(updated);
                        saveStoredSairaRules(updated);
                      }}
                      className="p-2 rounded-lg hover:bg-[#fff1ed] text-[#9b5145] cursor-pointer"
                      aria-label="Delete rule"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

export function getInitialSairaOneMinSetting(): boolean {
  return getStoredSairaOneMinEnabled();
}
