import React, { useEffect, useRef, useState } from 'react';
import {
  ArrowUp,
  Bell,
  Check,
  Clock,
  Copy,
  History,
  MessageSquarePlus,
  Plus,
  RotateCcw,
  SlidersHorizontal,
  Square,
  Trash2,
  X,
} from 'lucide-react';
import {
  loadSairaSessionExchanges,
  observeSairaSessions,
  persistSairaExchange,
  streamSairaChat,
} from '../services/sairaAiService';
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
  SairaChatSession,
  SairaNotificationRule,
  SairaPlatform,
} from '../types/chatbox';
import { SAIRA_PLATFORM_LABELS } from '../types/chatbox';
import { ChatboxLogoWithUpdateBadge, NoticeBanner } from './Common';

const SESSION_ROLLOVER_LIMIT = 20;

const STARTER_PROMPTS = [
  'বাংলায় একটি সুন্দর শুভ সকালের মেসেজ লিখে দাও',
  'Explain quantum computing simply in 3 points',
  'Help me plan a productive daily routine',
  'Write a friendly reply when I am busy at work',
];

function SairaMetaOrb({ size = 28, animate = false }: { size?: number; animate?: boolean }) {
  return (
    <div
      style={{ width: size, height: size }}
      className={`relative rounded-full shrink-0 flex items-center justify-center ${
        animate ? 'animate-spin' : ''
      }`}
    >
      <div
        className="w-full h-full rounded-full p-[2.5px]"
        style={{
          background:
            'conic-gradient(from 130deg, #8b5cf6, #6366f1, #ec4899, #06b6d4, #8b5cf6)',
        }}
      >
        <div className="w-full h-full rounded-full bg-white flex items-center justify-center">
          <div
            className="w-[52%] h-[52%] rounded-full opacity-90"
            style={{
              background:
                'radial-gradient(circle at 30% 30%, #a78bfa, #6366f1 65%, #38bdf8)',
            }}
          />
        </div>
      </div>
    </div>
  );
}

function FormattedSairaReply({ text }: { text: string }) {
  const parts = text.split(/(```[\s\S]*?```)/g);
  return (
    <div className="space-y-2 text-sm leading-relaxed text-[var(--chatbox-ink)]">
      {parts.map((part, idx) => {
        if (part.startsWith('```') && part.endsWith('```')) {
          const inner = part.slice(3, -3).replace(/^[a-zA-Z0-9_-]+\n/, '');
          return (
            <pre
              key={idx}
              className="rounded-2xl bg-[#1e1b2e] text-[#f3effc] p-3.5 text-xs font-mono overflow-x-auto"
            >
              <code>{inner}</code>
            </pre>
          );
        }
        return (
          <div key={idx} className="whitespace-pre-wrap break-words">
            {part}
          </div>
        );
      })}
    </div>
  );
}

export function SairaAiScreen({
  currentUserId,
  isOnline,
  appUpdateActive,
  onAppUpdateClick,
  oneMinAutoReplyEnabled,
  onChangeOneMinAutoReply,
}: {
  currentUserId: string;
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
  const [copiedExId, setCopiedExId] = useState<string | null>(null);

  // Seamless session rollover & saved history
  const [sessionId, setSessionId] = useState<string>(
    () => `saira_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
  );
  const [sessionIsNew, setSessionIsNew] = useState(true);
  const [rolloverCount, setRolloverCount] = useState(0);
  const [rolloverContextNote, setRolloverContextNote] = useState('');
  const [savedSessions, setSavedSessions] = useState<SairaChatSession[]>([]);
  const [showHistoryDrawer, setShowHistoryDrawer] = useState(false);

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
    if (!currentUserId) return;
    const unsub = observeSairaSessions(currentUserId, setSavedSessions);
    return () => unsub();
  }, [currentUserId]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [exchanges]);

  const startNewThread = (carryContext?: string) => {
    abortRef.current?.abort();
    setSessionId(`saira_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`);
    setSessionIsNew(true);
    setExchanges([]);
    setError('');
    setIsBusy(false);
    setRolloverContextNote(carryContext || '');
  };

  const handleOpenSavedSession = async (session: SairaChatSession) => {
    if (!currentUserId) return;
    abortRef.current?.abort();
    setShowHistoryDrawer(false);
    setIsBusy(false);
    setError('');
    setSessionId(session.id);
    setSessionIsNew(false);
    setRolloverContextNote('');
    const loaded = await loadSairaSessionExchanges(currentUserId, session.id);
    setExchanges(loaded);
  };

  const sendPrompt = async (rawQuestion: string) => {
    const question = rawQuestion.trim();
    if (isBusy || !question || !isOnline || question.length > 2000) return;

    // Seamless auto-rollover: if current thread hits the soft exchange cap, auto-create a fresh unlimited thread while keeping compact context
    let activeSessionId = sessionId;
    let activeIsNew = sessionIsNew;
    let prevExchanges = exchanges;
    let activeContextNote = rolloverContextNote;

    if (prevExchanges.length >= SESSION_ROLLOVER_LIMIT) {
      const recentSummary = prevExchanges
        .slice(-3)
        .map((ex) => `User: ${ex.question.slice(0, 120)} | Saira: ${ex.answer.slice(0, 160)}`)
        .join('\n');
      activeSessionId = `saira_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
      activeIsNew = true;
      activeContextNote = recentSummary;
      setSessionId(activeSessionId);
      setSessionIsNew(true);
      setRolloverContextNote(recentSummary);
      setRolloverCount((c) => c + 1);
      prevExchanges = [];
      setExchanges([]);
    }

    const exchangeId = `${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
    const isFirstTurn = prevExchanges.length === 0 && !activeContextNote;
    const historyForModel: AiExchange[] = activeContextNote
      ? [
          {
            id: 'carryover_ctx',
            question: 'Earlier conversation summary',
            answer: activeContextNote,
          },
          ...prevExchanges,
        ]
      : prevExchanges;

    setDraft('');
    setIsBusy(true);
    setError('');
    setExchanges((prev) => [...prev, { id: exchangeId, question, answer: '', isStreaming: true }]);

    const controller = new AbortController();
    abortRef.current = controller;
    let streamedAny = false;

    try {
      const finalAnswer = await streamSairaChat({
        question,
        history: historyForModel,
        isFirstTurn,
        isContinuation: Boolean(activeContextNote),
        signal: controller.signal,
        onChunk: (accumulated) => {
          streamedAny = true;
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
        const sessionTitle =
          (prevExchanges[0]?.question || question).slice(0, 90) || 'Saira Chat';
        setSessionIsNew(false);
        void persistSairaExchange({
          uid: currentUserId,
          sessionId: activeSessionId,
          isNewSession: activeIsNew,
          title: sessionTitle,
          question,
          answer: finalAnswer,
          sequence: prevExchanges.length,
        });
      } else {
        setExchanges((prev) => prev.filter((item) => item.id !== exchangeId));
        setDraft((cur) => cur || question);
        setError('Saira returned an empty response. Please try again.');
      }
    } catch (err) {
      if (controller.signal.aborted) {
        setExchanges((prev) =>
          prev
            .filter((item) => item.id !== exchangeId || Boolean(item.answer))
            .map((item) => ({ ...item, isStreaming: false }))
        );
      } else {
        setExchanges((prev) =>
          prev
            .filter((item) => item.id !== exchangeId || Boolean(item.answer))
            .map((item) => ({ ...item, isStreaming: false }))
        );
        if (!streamedAny) {
          setDraft((cur) => cur || question);
        }
        setError(err instanceof Error ? err.message : 'Saira AI is unavailable right now.');
      }
    } finally {
      setIsBusy(false);
      abortRef.current = null;
    }
  };

  const handleSubmit = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    void sendPrompt(draft);
  };

  const handleCopyReply = async (id: string, text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedExId(id);
      window.setTimeout(() => setCopiedExId(null), 1600);
    } catch {
      // Ignore clipboard failure
    }
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
    <div className="h-full flex flex-col bg-[var(--chatbox-bg)] overflow-hidden relative">
      {/* Compact Meta-AI Style Top Bar */}
      <header className="px-4 py-3 border-b border-[var(--chatbox-border)] bg-[var(--chatbox-surface)] flex items-center justify-between gap-2 shrink-0">
        <div className="flex items-center gap-2.5 min-w-0">
          <div className="md:hidden">
            <ChatboxLogoWithUpdateBadge
              appUpdateActive={appUpdateActive}
              onUpdateClick={onAppUpdateClick}
              size={30}
            />
          </div>
          <SairaMetaOrb size={32} animate={isBusy} />
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <h1 className="font-display text-base font-bold text-[var(--chatbox-ink)] truncate">
                Saira AI
              </h1>
              {appUpdateActive && (
                <button
                  type="button"
                  onClick={onAppUpdateClick}
                  className="hidden md:inline-flex px-2 py-0.5 rounded-full bg-[#16a34a] text-white text-[10px] font-bold cursor-pointer"
                >
                  Update
                </button>
              )}
            </div>
            <p className="text-[11px] text-[#8f89a3] truncate">
              Always on · Unlimited auto-continuity{rolloverCount > 0 ? ` · Thread ${rolloverCount + 1}` : ''}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-1.5 shrink-0">
          {/* Segmented Chat / Auto-Reply Switch */}
          <div className="flex items-center p-0.5 rounded-xl bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)]">
            <button
              type="button"
              onClick={() => setSelectedTab('chat')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors cursor-pointer ${
                selectedTab === 'chat'
                  ? 'bg-white text-[#5b4a8f] shadow-xs'
                  : 'text-[#6b657d] hover:text-[var(--chatbox-ink)]'
              }`}
            >
              Chat
            </button>
            <button
              type="button"
              onClick={() => setSelectedTab('autoReply')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1 transition-colors cursor-pointer ${
                selectedTab === 'autoReply'
                  ? 'bg-white text-[#5b4a8f] shadow-xs'
                  : 'text-[#6b657d] hover:text-[var(--chatbox-ink)]'
              }`}
            >
              <SlidersHorizontal className="w-3.5 h-3.5" />
              <span>{activeRuleCount > 0 ? `Rules (${activeRuleCount})` : 'Auto-Reply'}</span>
            </button>
          </div>

          {selectedTab === 'chat' && (
            <>
              <button
                type="button"
                onClick={() => setShowHistoryDrawer((v) => !v)}
                title="Saved Saira threads"
                className="w-9 h-9 rounded-xl hover:bg-[#efeaf7] text-[#5b4a8f] flex items-center justify-center cursor-pointer"
              >
                <History className="w-4 h-4" />
              </button>
              <button
                type="button"
                onClick={() => startNewThread()}
                title="New Saira chat"
                className="w-9 h-9 rounded-xl hover:bg-[#efeaf7] text-[#5b4a8f] flex items-center justify-center cursor-pointer"
              >
                <MessageSquarePlus className="w-4 h-4" />
              </button>
            </>
          )}
        </div>
      </header>

      {/* Saved History Slide Drawer */}
      {showHistoryDrawer && selectedTab === 'chat' && (
        <div
          className="absolute inset-0 z-30 bg-black/30 flex justify-end"
          onClick={() => setShowHistoryDrawer(false)}
        >
          <div
            className="w-80 max-w-[85vw] h-full bg-white border-l border-[#ece9f3] shadow-xl p-4 flex flex-col space-y-3"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between">
              <h2 className="font-display text-sm font-bold text-[#2a2340]">
                Saved Saira Threads
              </h2>
              <button
                type="button"
                onClick={() => setShowHistoryDrawer(false)}
                className="p-1.5 rounded-lg hover:bg-[#f6f4fa] text-[#8f89a3] cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <button
              type="button"
              onClick={() => {
                setShowHistoryDrawer(false);
                startNewThread();
              }}
              className="w-full py-2.5 rounded-xl bg-[#6250a0] hover:bg-[#463a76] text-white text-xs font-semibold flex items-center justify-center gap-1.5 cursor-pointer"
            >
              <MessageSquarePlus className="w-4 h-4" />
              New Unlimited Thread
            </button>
            <div className="flex-1 overflow-y-auto space-y-1.5">
              {savedSessions.length === 0 ? (
                <p className="text-xs text-[#8f89a3] text-center py-8">
                  Your saved Saira conversations appear here automatically.
                </p>
              ) : (
                savedSessions.map((s) => (
                  <button
                    key={s.id}
                    type="button"
                    onClick={() => void handleOpenSavedSession(s)}
                    className={`w-full text-left p-3 rounded-xl border transition-colors cursor-pointer ${
                      s.id === sessionId
                        ? 'bg-[#efeaf7] border-[#6250a0]/40'
                        : 'bg-[#f7f5fb] border-transparent hover:border-[#ece9f3]'
                    }`}
                  >
                    <p className="text-xs font-semibold text-[#2a2340] truncate">{s.title}</p>
                    <p className="text-[11px] text-[#6b657d] truncate mt-0.5">{s.lastPreview}</p>
                  </button>
                ))
              )}
            </div>
          </div>
        </div>
      )}

      {selectedTab === 'chat' ? (
        <div className="flex-1 min-h-0 flex flex-col">
          {!isOnline && (
            <div className="px-4 pt-2 max-w-3xl w-full mx-auto">
              <NoticeBanner isError text="Offline · Connect to the internet to chat with Saira AI." />
            </div>
          )}
          {error && (
            <div className="px-4 pt-2 max-w-3xl w-full mx-auto">
              <NoticeBanner isError text={error} />
            </div>
          )}

          {/* Meta-AI Scrollable Canvas */}
          <div className="flex-1 overflow-y-auto px-4 py-4">
            <div className="max-w-3xl mx-auto space-y-5">
              {rolloverContextNote && (
                <div className="text-center py-1">
                  <span className="text-[11px] font-medium text-[#8f89a3]">
                    Auto-continued in fresh unlimited thread · context preserved
                  </span>
                </div>
              )}

              {exchanges.length === 0 ? (
                <div className="py-10 sm:py-16 flex flex-col items-center text-center space-y-6">
                  <SairaMetaOrb size={64} animate={isBusy} />
                  <div className="space-y-1.5 max-w-md">
                    <h2 className="font-display text-2xl font-bold text-[var(--chatbox-ink)] tracking-tight">
                      Ask Saira AI anything
                    </h2>
                    <p className="text-xs sm:text-sm text-[#6b657d] leading-relaxed">
                      বাংলা, Banglish বা ইংরেজিতে যেকোনো প্রশ্ন করুন — আমি সবসময় অনলাইনে আছি। লিমিট শেষ হলে অটোমেটিক নতুন থ্রেডে চলতে থাকবে।
                    </p>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 w-full max-w-xl">
                    {STARTER_PROMPTS.map((prompt) => (
                      <button
                        key={prompt}
                        type="button"
                        disabled={!isOnline || isBusy}
                        onClick={() => void sendPrompt(prompt)}
                        className="text-left px-4 py-3 rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] hover:border-[#8b5cf6] hover:bg-[#f7f5fb] text-xs font-medium text-[var(--chatbox-ink)] transition-colors cursor-pointer disabled:opacity-55"
                      >
                        {prompt}
                      </button>
                    ))}
                  </div>
                </div>
              ) : (
                exchanges.map((ex) => (
                  <div key={ex.id} className="space-y-3">
                    {/* User Prompt Bubble (Meta AI right-aligned rounded-3xl) */}
                    <div className="flex justify-end">
                      <div className="max-w-[85%] sm:max-w-[75%] px-4 py-2.5 rounded-3xl rounded-br-md bg-[#6250a0] text-white text-sm whitespace-pre-wrap leading-relaxed shadow-xs">
                        {ex.question}
                      </div>
                    </div>

                    {/* Saira Meta-AI Style Response */}
                    <div className="flex items-start gap-3">
                      <div className="pt-0.5">
                        <SairaMetaOrb size={26} animate={Boolean(ex.isStreaming)} />
                      </div>
                      <div className="flex-1 min-w-0 space-y-1.5">
                        <div className="text-[11px] font-semibold text-[#5b4a8f]">
                          Saira AI
                        </div>
                        <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] px-4 py-3 shadow-2xs">
                          {!ex.answer && ex.isStreaming ? (
                            <div className="flex items-center gap-2 text-xs text-[#8f89a3]">
                              <span>Thinking…</span>
                            </div>
                          ) : (
                            <FormattedSairaReply text={ex.answer} />
                          )}
                        </div>
                        {!ex.isStreaming && ex.answer && (
                          <div className="flex items-center gap-3 px-1">
                            <button
                              type="button"
                              onClick={() => void handleCopyReply(ex.id, ex.answer)}
                              className="inline-flex items-center gap-1 text-[11px] text-[#8f89a3] hover:text-[#5b4a8f] cursor-pointer"
                            >
                              {copiedExId === ex.id ? (
                                <Check className="w-3.5 h-3.5 text-[#16a34a]" />
                              ) : (
                                <Copy className="w-3.5 h-3.5" />
                              )}
                              <span>{copiedExId === ex.id ? 'Copied' : 'Copy'}</span>
                            </button>
                            <button
                              type="button"
                              disabled={isBusy || !isOnline}
                              onClick={() => void sendPrompt(ex.question)}
                              className="inline-flex items-center gap-1 text-[11px] text-[#8f89a3] hover:text-[#5b4a8f] cursor-pointer disabled:opacity-50"
                            >
                              <RotateCcw className="w-3 h-3" />
                              <span>Ask again</span>
                            </button>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                ))
              )}
              <div ref={endRef} />
            </div>
          </div>

          {/* Meta AI Floating Pill Bottom Composer */}
          <div className="px-4 pb-4 pt-2 bg-gradient-to-t from-[var(--chatbox-bg)] via-[var(--chatbox-bg)] to-transparent shrink-0">
            <form onSubmit={handleSubmit} className="max-w-3xl mx-auto">
              <div className="flex items-center gap-2.5 p-1.5 pl-3 rounded-[28px] bg-white border border-[#ddd5ee] focus-within:border-[#7c5ce6] shadow-sm transition-colors">
                <SairaMetaOrb size={24} animate={isBusy} />
                <input
                  type="text"
                  value={draft}
                  disabled={!isOnline || isBusy}
                  onChange={(e) => setDraft(e.target.value.slice(0, 2000))}
                  placeholder={isOnline ? 'Ask Saira AI anything…' : 'Offline · Saira disabled'}
                  className="flex-1 h-10 bg-transparent text-sm text-[#2a2340] placeholder:text-[#8f89a3] focus:outline-none disabled:opacity-60"
                />
                {isBusy ? (
                  <button
                    type="button"
                    onClick={() => abortRef.current?.abort()}
                    aria-label="Stop response"
                    className="w-10 h-10 rounded-full bg-[#efeaf7] hover:bg-[#e6dff5] text-[#5b4a8f] flex items-center justify-center shrink-0 cursor-pointer"
                  >
                    <Square className="w-4 h-4 fill-current" />
                  </button>
                ) : (
                  <button
                    type="submit"
                    disabled={!isOnline || !draft.trim()}
                    aria-label="Send"
                    className="w-10 h-10 rounded-full text-white flex items-center justify-center shrink-0 transition-opacity disabled:opacity-35 cursor-pointer"
                    style={{
                      background:
                        'linear-gradient(135deg, #7c3aed 0%, #6366f1 60%, #06b6d4 100%)',
                    }}
                  >
                    <ArrowUp className="w-4 h-4" />
                  </button>
                )}
              </div>
            </form>
          </div>
        </div>
      ) : (
        <div className="flex-1 overflow-y-auto px-4 py-4">
          <div className="max-w-2xl mx-auto space-y-3 pb-6">
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
                placeholder="What should Saira reply when they message?"
                className="w-full p-3 rounded-xl bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] text-sm focus:outline-none focus:border-[#5b4a8f]"
              />
              <button
                type="submit"
                disabled={!ruleContactDraft.trim() || !ruleGuidanceDraft.trim()}
                className="w-full py-2.5 rounded-xl bg-[#6250a0] hover:bg-[#463a76] disabled:opacity-50 text-white text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
              >
                <Plus className="w-4 h-4" />
                Save Reply Rule
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
                        <span className="text-[11px] text-[#5b4a8f] font-medium">
                          · {SAIRA_PLATFORM_LABELS[rule.platform]}
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
        </div>
      )}
    </div>
  );
}

export function getInitialSairaOneMinSetting(): boolean {
  return getStoredSairaOneMinEnabled();
}
