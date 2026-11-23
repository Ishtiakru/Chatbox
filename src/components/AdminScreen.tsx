import React, { useEffect, useState } from 'react';
import {
  CheckCircle2,
  Download,
  FileArchive,
  LayoutDashboard,
  RefreshCw,
  Shield,
  ShieldCheck,
  Sparkles,
  Trash2,
  UserPlus,
  Wand2,
  XCircle,
} from 'lucide-react';
import { isValidSparkTarget } from '../data/sparkAdminCommands';
import {
  acceptSparkDelegation,
  applyAdminAiUpdatePlan,
  applySparkProposal,
  buildAndReleaseQeZip,
  checkSparkAdminStatus,
  claimSparkOwnership,
  fetchCurrentQeRelease,
  interpretAdminUpdatePrompt,
  inviteSparkDelegate,
  observeSparkDelegations,
  reauthenticateWithGooglePopup,
  revokeSparkDelegate,
  saveSparkDynamicConfig,
} from '../services/sparkAdminService';
import { friendlyFirestoreMessage } from '../firebase';
import type {
  AdminAiUpdatePlan,
  AdminAuthStatus,
  QeReleaseInfo,
  SparkAppearance,
  SparkAppUpdate,
  SparkDelegation,
  SparkDynamicConfig,
} from '../types/chatbox';
import {
  MAX_UPDATE_NOTE_LENGTH,
  MAX_UPDATE_VERSION_LENGTH,
  MAX_WELCOME_LENGTH,
} from '../types/chatbox';
import {
  ChatboxLogoWithUpdateBadge,
  CustomMenuIcon,
  NoticeBanner,
} from './Common';

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

export function AdminScreen({
  currentUserId,
  currentUserEmail,
  isOnline,
  appearance,
  welcomeBanner,
  appUpdate,
  dynamicConfig,
  onAppUpdateClick,
}: {
  currentUserId: string;
  currentUserEmail: string | null;
  isOnline: boolean;
  appearance: SparkAppearance;
  welcomeBanner: string;
  appUpdate: SparkAppUpdate;
  dynamicConfig: SparkDynamicConfig;
  onAppUpdateClick: () => void;
}) {
  const [adminTab, setAdminTab] = useState<'dashboard' | 'qeCoder'>('dashboard');
  const [status, setStatus] = useState<AdminAuthStatus>('CHECKING');
  const [statusError, setStatusError] = useState('');
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; isError: boolean } | null>(null);

  // Dashboard AI Prompt & Quick Controls
  const [promptDraft, setPromptDraft] = useState('');
  const [pendingPlan, setPendingPlan] = useState<AdminAiUpdatePlan | null>(null);
  const [welcomeDraft, setWelcomeDraft] = useState(welcomeBanner);

  // QE Coder ZIP Release State
  const [qeRelease, setQeRelease] = useState<QeReleaseInfo | null>(null);
  const [qeVersion, setQeVersion] = useState(() => {
    const cur = appUpdate.version || '1.0.0';
    return cur;
  });
  const [qeNote, setQeNote] = useState(appUpdate.note || '');

  // Optional Owner Gmail Delegation
  const [showDelegates, setShowDelegates] = useState(false);
  const [targetEmail, setTargetEmail] = useState('');
  const [delegations, setDelegations] = useState<SparkDelegation[]>([]);

  useEffect(() => {
    setWelcomeDraft(welcomeBanner);
  }, [welcomeBanner]);

  const runAccessCheck = async () => {
    setStatus('CHECKING');
    setStatusError('');
    try {
      const nextStatus = await checkSparkAdminStatus(currentUserId, currentUserEmail);
      setStatus(nextStatus);
    } catch (err) {
      setStatus('ERROR');
      setStatusError(friendlyFirestoreMessage(err));
    }
  };

  useEffect(() => {
    void runAccessCheck();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUserId, currentUserEmail]);

  useEffect(() => {
    if (status !== 'OWNER') {
      setDelegations([]);
      return;
    }
    const unsub = observeSparkDelegations(setDelegations);
    return () => unsub();
  }, [status]);

  useEffect(() => {
    if (adminTab === 'qeCoder') {
      void fetchCurrentQeRelease().then(setQeRelease);
    }
  }, [adminTab]);

  const handleReauth = async () => {
    setBusy(true);
    setFeedback(null);
    try {
      await reauthenticateWithGooglePopup();
      if (status === 'UNCLAIMED_OWNER' && currentUserEmail) {
        await claimSparkOwnership(currentUserId, currentUserEmail);
      }
      await runAccessCheck();
      setFeedback({
        text: 'Google verified! Admin control window refreshed.',
        isError: false,
      });
    } catch (err) {
      setFeedback({ text: friendlyFirestoreMessage(err), isError: true });
    } finally {
      setBusy(false);
    }
  };

  const handleInterpretAndPreview = async (e: React.FormEvent) => {
    e.preventDefault();
    const instruction = promptDraft.trim();
    if (!instruction || busy || !isOnline) return;
    setBusy(true);
    setFeedback(null);
    try {
      const plan = await interpretAdminUpdatePrompt(instruction, dynamicConfig.customMenus);
      setPendingPlan(plan);
    } catch (err) {
      setFeedback({
        text: err instanceof Error ? err.message : 'Unable to parse instruction.',
        isError: true,
      });
    } finally {
      setBusy(false);
    }
  };

  const handleApplyPlan = async () => {
    if (!pendingPlan || busy || !isOnline) return;
    setBusy(true);
    setFeedback(null);
    try {
      const msg = await applyAdminAiUpdatePlan(currentUserId, dynamicConfig, pendingPlan);
      setPendingPlan(null);
      setPromptDraft('');
      setFeedback({ text: msg, isError: false });
    } catch (err) {
      setFeedback({ text: friendlyFirestoreMessage(err), isError: true });
    } finally {
      setBusy(false);
    }
  };

  const handleQuickAction = async (fn: () => Promise<string | void>, successMsg?: string) => {
    if (busy || !isOnline) return;
    setBusy(true);
    setFeedback(null);
    try {
      const msg = await fn();
      setFeedback({ text: msg || successMsg || 'Updated!', isError: false });
    } catch (err) {
      setFeedback({ text: friendlyFirestoreMessage(err), isError: true });
    } finally {
      setBusy(false);
    }
  };

  const handleBuildQeRelease = async (e: React.FormEvent) => {
    e.preventDefault();
    const version = qeVersion.trim().slice(0, MAX_UPDATE_VERSION_LENGTH);
    const note = qeNote.trim().slice(0, MAX_UPDATE_NOTE_LENGTH);
    if (!version || busy || !isOnline) return;
    setBusy(true);
    setFeedback(null);
    try {
      const release = await buildAndReleaseQeZip({
        uid: currentUserId,
        version,
        note,
      });
      setQeRelease(release);
      setFeedback({
        text: `Released v${release.version}! Old ZIP deleted, ${release.filename} saved, and green Update button enabled on users' home screens.`,
        isError: false,
      });
    } catch (err) {
      setFeedback({
        text: err instanceof Error ? err.message : friendlyFirestoreMessage(err),
        isError: true,
      });
    } finally {
      setBusy(false);
    }
  };

  const handleInviteDelegate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isValidSparkTarget(targetEmail) || busy || !isOnline) return;
    setBusy(true);
    setFeedback(null);
    try {
      await inviteSparkDelegate(currentUserId, targetEmail);
      setTargetEmail('');
      setFeedback({ text: 'Admin invitation sent.', isError: false });
    } catch (err) {
      setFeedback({ text: friendlyFirestoreMessage(err), isError: true });
    } finally {
      setBusy(false);
    }
  };

  const handleAcceptDelegation = async () => {
    if (!currentUserEmail || busy) return;
    setBusy(true);
    setFeedback(null);
    try {
      await acceptSparkDelegation(currentUserId, currentUserEmail);
      await runAccessCheck();
      setFeedback({ text: 'Admin access unlocked!', isError: false });
    } catch (err) {
      setFeedback({ text: friendlyFirestoreMessage(err), isError: true });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="h-full overflow-y-auto bg-[var(--chatbox-bg)] px-4 py-4 flex justify-center">
      <div className="w-full max-w-[760px] space-y-4 pb-8">
        {/* Simple Header */}
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2.5 min-w-0">
            <ChatboxLogoWithUpdateBadge
              appUpdateActive={appUpdate.active}
              onUpdateClick={onAppUpdateClick}
              size={34}
            />
            <div className="min-w-0">
              <h1 className="font-display text-lg font-bold text-[var(--chatbox-ink)]">
                Admin Panel
              </h1>
              <p className="text-xs text-[#8f89a3] truncate">
                {currentUserEmail || 'Admin session'}
              </p>
            </div>
          </div>

          <button
            type="button"
            disabled={busy || !isOnline}
            onClick={handleReauth}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl border border-[var(--chatbox-border)] bg-[var(--chatbox-surface)] hover:bg-[#efeaf7] text-xs font-semibold text-[#5b4a8f] cursor-pointer shrink-0"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            Verify Google
          </button>
        </div>

        {!isOnline && (
          <NoticeBanner isError text="Offline · Admin controls require internet connection." />
        )}
        {feedback && <NoticeBanner isError={feedback.isError} text={feedback.text} />}
        {statusError && <NoticeBanner isError text={statusError} />}

        {/* Access Gate */}
        {status === 'CHECKING' && (
          <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-8 text-center text-sm text-[#8f89a3]">
            Checking admin permission…
          </div>
        )}

        {status === 'DENIED' && (
          <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-6 text-center space-y-2.5">
            <Shield className="w-8 h-8 text-[#9b5145] mx-auto" />
            <h2 className="font-display text-base font-bold text-[var(--chatbox-ink)]">
              Admin Only
            </h2>
            <p className="text-xs text-[#6b657d]">
              Only the verified admin Gmail account can access this panel.
            </p>
          </div>
        )}

        {status === 'UNCLAIMED_OWNER' && (
          <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-6 text-center space-y-3">
            <ShieldCheck className="w-8 h-8 text-[#5b4a8f] mx-auto" />
            <h2 className="font-display text-base font-bold text-[var(--chatbox-ink)]">
              Verify Google to unlock Admin Dashboard
            </h2>
            <button
              type="button"
              onClick={handleReauth}
              disabled={busy}
              className="px-5 py-2.5 rounded-xl bg-[#6250a0] text-white text-xs font-semibold cursor-pointer"
            >
              Verify Google Now
            </button>
          </div>
        )}

        {status === 'INVITED_DELEGATE' && (
          <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-6 text-center space-y-3">
            <ShieldCheck className="w-8 h-8 text-[#5b4a8f] mx-auto" />
            <h2 className="font-display text-base font-bold text-[var(--chatbox-ink)]">
              Admin Invitation Ready
            </h2>
            <button
              type="button"
              disabled={busy || !isOnline}
              onClick={handleAcceptDelegation}
              className="px-5 py-2.5 rounded-xl bg-[#6250a0] text-white text-xs font-semibold cursor-pointer"
            >
              Accept Admin Access
            </button>
          </div>
        )}

        {(status === 'OWNER' || status === 'DELEGATE') && (
          <>
            {/* Simple 2-Tab Bar: Dashboard & QE Coder */}
            <div className="grid grid-cols-2 p-1 rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)]">
              <button
                type="button"
                onClick={() => setAdminTab('dashboard')}
                className={`py-2.5 rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors cursor-pointer ${
                  adminTab === 'dashboard'
                    ? 'bg-[#efeaf7] text-[#5b4a8f]'
                    : 'text-[#6b657d] hover:text-[var(--chatbox-ink)]'
                }`}
              >
                <LayoutDashboard className="w-4 h-4" />
                Dashboard
              </button>
              <button
                type="button"
                onClick={() => setAdminTab('qeCoder')}
                className={`py-2.5 rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors cursor-pointer ${
                  adminTab === 'qeCoder'
                    ? 'bg-[#efeaf7] text-[#5b4a8f]'
                    : 'text-[#6b657d] hover:text-[var(--chatbox-ink)]'
                }`}
              >
                <FileArchive className="w-4 h-4" />
                QE Coder
              </button>
            </div>

            {adminTab === 'dashboard' ? (
              <div className="space-y-4">
                {/* 1. Natural-Language App Control & Custom Menu Builder */}
                <form
                  onSubmit={handleInterpretAndPreview}
                  className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-4 space-y-3"
                >
                  <div className="flex items-center gap-2">
                    <Wand2 className="w-4 h-4 text-[#5b4a8f]" />
                    <h2 className="font-display text-sm font-bold text-[var(--chatbox-ink)]">
                      App Control & Live Builder
                    </h2>
                  </div>
                  <p className="text-xs text-[#6b657d] leading-relaxed">
                    অ্যাপে যা যোগ বা পরিবর্তন করতে চান বিস্তারিত লিখুন (যেমন: মেনু বার যোগ, থিম পরিবর্তন, বা নোটিশ) এবং Submit করুন:
                  </p>
                  <textarea
                    rows={3}
                    value={promptDraft}
                    disabled={busy || !isOnline}
                    onChange={(e) => setPromptDraft(e.target.value.slice(0, 1500))}
                    placeholder="যেমন: একটা নতুন মেনু বার যোগ করো নাম Notice, সেখানে লেখা থাকবে প্রতিদিন রাত ১০টায় লাইভ আড্ডা হবে..."
                    className="w-full p-3 rounded-xl bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] text-sm text-[var(--chatbox-ink)] placeholder:text-[#8f89a3] focus:outline-none focus:border-[#5b4a8f]"
                  />
                  <div className="flex justify-end">
                    <button
                      type="submit"
                      disabled={!promptDraft.trim() || busy || !isOnline}
                      className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-[#6250a0] hover:bg-[#463a76] disabled:opacity-50 text-white text-xs font-semibold cursor-pointer"
                    >
                      <Sparkles className="w-3.5 h-3.5" />
                      {busy ? 'Preparing…' : 'Preview & Submit'}
                    </button>
                  </div>

                  {pendingPlan && (
                    <div className="rounded-xl bg-[#f7f5fb] border border-[#5b4a8f]/35 p-3.5 space-y-2.5">
                      <p className="text-xs font-bold text-[#5b4a8f]">
                        Ready to update live app:
                      </p>
                      <p className="text-sm text-[var(--chatbox-ink)]">{pendingPlan.summary}</p>
                      {pendingPlan.addOrUpdateMenu && (
                        <div className="text-xs text-[#5a5468] bg-white rounded-lg p-2.5 border border-[#ece9f3]">
                          <strong>New Menu:</strong> {pendingPlan.addOrUpdateMenu.label} —{' '}
                          {pendingPlan.addOrUpdateMenu.title}
                        </div>
                      )}
                      <div className="flex justify-end gap-2">
                        <button
                          type="button"
                          onClick={() => setPendingPlan(null)}
                          className="px-3 py-1.5 rounded-lg border border-[var(--chatbox-border)] bg-white text-xs text-[#6b657d] cursor-pointer"
                        >
                          Cancel
                        </button>
                        <button
                          type="button"
                          disabled={busy || !isOnline}
                          onClick={() => void handleApplyPlan()}
                          className="px-4 py-1.5 rounded-lg bg-[#16a34a] hover:bg-[#15803d] text-white text-xs font-semibold cursor-pointer"
                        >
                          {busy ? 'Updating…' : 'Apply Update Now'}
                        </button>
                      </div>
                    </div>
                  )}
                </form>

                {/* 2. Quick Controls */}
                <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-4 space-y-4">
                  <h2 className="font-display text-sm font-bold text-[var(--chatbox-ink)]">
                    Quick App Controls
                  </h2>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div className="space-y-1.5">
                      <p className="text-[11px] font-semibold uppercase tracking-wider text-[#8f89a3]">
                        App Theme
                      </p>
                      <div className="grid grid-cols-2 gap-1.5">
                        {(['default', 'lavender'] as const).map((t) => (
                          <button
                            key={t}
                            type="button"
                            disabled={busy || !isOnline}
                            onClick={() =>
                              void handleQuickAction(() =>
                                applySparkProposal(currentUserId, { kind: 'setTheme', value: t })
                              )
                            }
                            className={`py-2 rounded-xl text-xs font-semibold capitalize border cursor-pointer ${
                              appearance.theme === t
                                ? 'bg-[#efeaf7] border-[#5b4a8f] text-[#5b4a8f]'
                                : 'bg-white border-[var(--chatbox-border)] text-[#6b657d]'
                            }`}
                          >
                            {t}
                          </button>
                        ))}
                      </div>
                    </div>

                    <div className="space-y-1.5">
                      <p className="text-[11px] font-semibold uppercase tracking-wider text-[#8f89a3]">
                        Mobile Menu Position
                      </p>
                      <div className="grid grid-cols-2 gap-1.5">
                        {(['bottom', 'top'] as const).map((m) => (
                          <button
                            key={m}
                            type="button"
                            disabled={busy || !isOnline}
                            onClick={() =>
                              void handleQuickAction(() =>
                                applySparkProposal(currentUserId, { kind: 'setMenu', value: m })
                              )
                            }
                            className={`py-2 rounded-xl text-xs font-semibold capitalize border cursor-pointer ${
                              appearance.menu === m
                                ? 'bg-[#efeaf7] border-[#5b4a8f] text-[#5b4a8f]'
                                : 'bg-white border-[var(--chatbox-border)] text-[#6b657d]'
                            }`}
                          >
                            {m}
                          </button>
                        ))}
                      </div>
                    </div>
                  </div>

                  {/* Welcome Banner */}
                  <div className="space-y-1.5 pt-1">
                    <p className="text-[11px] font-semibold uppercase tracking-wider text-[#8f89a3]">
                      Welcome Banner
                    </p>
                    <div className="flex gap-2">
                      <input
                        type="text"
                        value={welcomeDraft}
                        maxLength={MAX_WELCOME_LENGTH}
                        onChange={(e) => setWelcomeDraft(e.target.value)}
                        placeholder="Welcome message for users…"
                        className="flex-1 h-10 px-3.5 rounded-xl bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] text-sm focus:outline-none focus:border-[#5b4a8f]"
                      />
                      <button
                        type="button"
                        disabled={busy || !welcomeDraft.trim() || !isOnline}
                        onClick={() =>
                          void handleQuickAction(() =>
                            applySparkProposal(currentUserId, {
                              kind: 'setWelcome',
                              value: welcomeDraft,
                            })
                          )
                        }
                        className="px-4 h-10 rounded-xl bg-[#efeaf7] hover:bg-[#e6dff5] text-[#5b4a8f] text-xs font-semibold cursor-pointer"
                      >
                        Save
                      </button>
                    </div>
                  </div>

                  {/* Active Custom Menu Bars */}
                  {dynamicConfig.customMenus.length > 0 && (
                    <div className="space-y-2 pt-2 border-t border-[var(--chatbox-border)]">
                      <p className="text-[11px] font-semibold uppercase tracking-wider text-[#8f89a3]">
                        Custom Menu Bars ({dynamicConfig.customMenus.length}/5)
                      </p>
                      <div className="space-y-1.5">
                        {dynamicConfig.customMenus.map((menu) => (
                          <div
                            key={menu.id}
                            className="flex items-center justify-between gap-2 rounded-xl bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] px-3 py-2"
                          >
                            <div className="flex items-center gap-2 min-w-0">
                              <CustomMenuIcon name={menu.icon} className="w-4 h-4 text-[#5b4a8f]" />
                              <div className="min-w-0">
                                <p className="text-xs font-bold text-[var(--chatbox-ink)] truncate">
                                  {menu.label} · {menu.title}
                                </p>
                                <p className="text-[11px] text-[#6b657d] truncate">{menu.body}</p>
                              </div>
                            </div>
                            <button
                              type="button"
                              disabled={busy}
                              onClick={() =>
                                void handleQuickAction(
                                  () =>
                                    saveSparkDynamicConfig(currentUserId, {
                                      ...dynamicConfig,
                                      customMenus: dynamicConfig.customMenus.filter(
                                        (m) => m.id !== menu.id
                                      ),
                                    }),
                                  `Removed custom menu "${menu.label}".`
                                )
                              }
                              className="p-1.5 rounded-lg hover:bg-[#fff1ed] text-[#9b5145] cursor-pointer shrink-0"
                              aria-label="Remove custom menu"
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>

                {/* Compact Delegate Toggle for Owner */}
                {status === 'OWNER' && (
                  <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-4 space-y-3">
                    <button
                      type="button"
                      onClick={() => setShowDelegates((v) => !v)}
                      className="w-full flex items-center justify-between text-xs font-semibold text-[#5b4a8f] cursor-pointer"
                    >
                      <span className="inline-flex items-center gap-1.5">
                        <UserPlus className="w-4 h-4" />
                        Admin Gmail Access ({delegations.filter((d) => d.status === 'active').length} delegates)
                      </span>
                      <span>{showDelegates ? 'Hide' : 'Manage'}</span>
                    </button>

                    {showDelegates && (
                      <div className="space-y-3 pt-2 border-t border-[var(--chatbox-border)]">
                        <form onSubmit={handleInviteDelegate} className="flex gap-2">
                          <input
                            type="email"
                            value={targetEmail}
                            onChange={(e) => setTargetEmail(e.target.value)}
                            placeholder="partner@gmail.com"
                            className="flex-1 h-10 px-3 rounded-xl bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] text-sm focus:outline-none focus:border-[#5b4a8f]"
                          />
                          <button
                            type="submit"
                            disabled={!isValidSparkTarget(targetEmail) || busy || !isOnline}
                            className="px-4 h-10 rounded-xl bg-[#6250a0] disabled:opacity-50 text-white text-xs font-semibold cursor-pointer"
                          >
                            Invite
                          </button>
                        </form>
                        {delegations.map((del) => (
                          <div
                            key={del.email}
                            className="flex items-center justify-between gap-2 text-xs py-1"
                          >
                            <span className="truncate text-[var(--chatbox-ink)]">
                              {del.email} ({del.status})
                            </span>
                            {del.status !== 'revoked' ? (
                              <button
                                type="button"
                                disabled={busy}
                                onClick={() =>
                                  void handleQuickAction(
                                    () => revokeSparkDelegate(del.email),
                                    `Revoked ${del.email}`
                                  )
                                }
                                className="inline-flex items-center gap-1 text-[#9b5145] font-semibold cursor-pointer"
                              >
                                <XCircle className="w-3.5 h-3.5" />
                                Revoke
                              </button>
                            ) : (
                              <span className="inline-flex items-center gap-1 text-[#8f89a3]">
                                <CheckCircle2 className="w-3.5 h-3.5" />
                                Revoked
                              </span>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            ) : (
              /* QE Coder ZIP Vault & Live Release Publisher */
              <div className="space-y-4">
                <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-4 space-y-3">
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <FileArchive className="w-5 h-5 text-[#5b4a8f]" />
                      <div>
                        <h2 className="font-display text-sm font-bold text-[var(--chatbox-ink)]">
                          QE Coder · Latest Coding ZIP
                        </h2>
                        <p className="text-[11px] text-[#8f89a3]">
                          Only the newest release ZIP is kept; previous ZIP files are automatically deleted.
                        </p>
                      </div>
                    </div>
                    {appUpdate.active && (
                      <span className="px-2 py-0.5 rounded-full bg-[#dcfce7] text-[#15803d] text-[10px] font-bold uppercase">
                        Live v{appUpdate.version}
                      </span>
                    )}
                  </div>

                  {qeRelease ? (
                    <div className="rounded-xl bg-[#f7f5fb] border border-[#e2dcf2] p-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                      <div className="min-w-0 space-y-0.5">
                        <p className="font-mono text-xs font-bold text-[#2a2340] truncate">
                          {qeRelease.filename}
                        </p>
                        <p className="text-[11px] text-[#6b657d] tabular-nums">
                          v{qeRelease.version} · {formatBytes(qeRelease.sizeBytes)} ·{' '}
                          {qeRelease.fileCount} source files
                        </p>
                        {qeRelease.note && (
                          <p className="text-xs text-[#5a5468] truncate">{qeRelease.note}</p>
                        )}
                      </div>
                      <a
                        href={qeRelease.downloadUrl}
                        download={qeRelease.filename}
                        className="inline-flex items-center justify-center gap-1.5 px-4 py-2 rounded-xl bg-[#6250a0] hover:bg-[#463a76] text-white text-xs font-semibold shrink-0"
                      >
                        <Download className="w-3.5 h-3.5" />
                        Download ZIP
                      </a>
                    </div>
                  ) : (
                    <div className="rounded-xl bg-[#f7f5fb] border border-dashed border-[#d8d0ec] p-4 text-center text-xs text-[#8f89a3]">
                      No coding ZIP packaged yet. Release a version below to package the full codebase.
                    </div>
                  )}
                </div>

                <form
                  onSubmit={handleBuildQeRelease}
                  className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-4 space-y-3"
                >
                  <h3 className="font-display text-sm font-bold text-[var(--chatbox-ink)]">
                    Release New App Update & Package Coding ZIP
                  </h3>
                  <div className="space-y-2.5">
                    <input
                      type="text"
                      value={qeVersion}
                      maxLength={MAX_UPDATE_VERSION_LENGTH}
                      onChange={(e) => setQeVersion(e.target.value)}
                      placeholder="New version (e.g., 1.1.0)"
                      className="w-full h-10 px-3.5 rounded-xl bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] text-sm focus:outline-none focus:border-[#5b4a8f]"
                    />
                    <textarea
                      rows={2}
                      value={qeNote}
                      maxLength={MAX_UPDATE_NOTE_LENGTH}
                      onChange={(e) => setQeNote(e.target.value)}
                      placeholder="What changed in this update?"
                      className="w-full p-3 rounded-xl bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] text-sm focus:outline-none focus:border-[#5b4a8f]"
                    />
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <button
                      type="submit"
                      disabled={busy || !qeVersion.trim() || !isOnline}
                      className="px-4 py-2.5 rounded-xl bg-[#16a34a] hover:bg-[#15803d] disabled:opacity-50 text-white text-xs font-semibold cursor-pointer"
                    >
                      {busy ? 'Packaging ZIP…' : 'Build New ZIP & Show Green Update Button'}
                    </button>
                    {appUpdate.active && (
                      <button
                        type="button"
                        disabled={busy || !isOnline}
                        onClick={() =>
                          void handleQuickAction(() =>
                            applySparkProposal(currentUserId, {
                              kind: 'setAppUpdate',
                              active: false,
                              version: '',
                              note: '',
                              downloadUrl: '',
                            })
                          )
                        }
                        className="px-4 py-2.5 rounded-xl border border-[var(--chatbox-border)] bg-white hover:bg-[#f7f5fb] text-xs font-semibold text-[#5a5468] cursor-pointer"
                      >
                        Hide Update Button
                      </button>
                    )}
                  </div>
                </form>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
