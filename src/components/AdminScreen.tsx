import React, { useEffect, useState } from 'react';
import {
  CheckCircle2,
  RefreshCw,
  Shield,
  ShieldCheck,
  Sparkles,
  UserPlus,
  XCircle,
} from 'lucide-react';
import { isValidSparkTarget, parseSparkCommand } from '../data/sparkAdminCommands';
import {
  acceptSparkDelegation,
  applySparkProposal,
  checkSparkAdminStatus,
  claimSparkOwnership,
  inviteSparkDelegate,
  observeSparkDelegations,
  reauthenticateWithGooglePopup,
  revokeSparkDelegate,
} from '../services/sparkAdminService';
import { friendlyFirestoreMessage } from '../firebase';
import type {
  AdminAuthStatus,
  SparkAppearance,
  SparkAppUpdate,
  SparkCommandProposal,
  SparkDelegation,
} from '../types/chatbox';
import {
  MAX_UPDATE_NOTE_LENGTH,
  MAX_UPDATE_URL_LENGTH,
  MAX_UPDATE_VERSION_LENGTH,
} from '../types/chatbox';
import { ChatboxLogoWithUpdateBadge, NoticeBanner } from './Common';

export function AdminScreen({
  currentUserId,
  currentUserEmail,
  isOnline,
  appearance,
  welcomeBanner,
  appUpdate,
  onAppUpdateClick,
}: {
  currentUserId: string;
  currentUserEmail: string | null;
  isOnline: boolean;
  appearance: SparkAppearance;
  welcomeBanner: string;
  appUpdate: SparkAppUpdate;
  onAppUpdateClick: () => void;
}) {
  const [status, setStatus] = useState<AdminAuthStatus>('CHECKING');
  const [statusError, setStatusError] = useState('');
  const [busy, setBusy] = useState(false);
  const [commandDraft, setCommandDraft] = useState('');
  const [proposal, setProposal] = useState<SparkCommandProposal | null>(null);

  const [updateVersion, setUpdateVersion] = useState(appUpdate.version || '');
  const [updateNote, setUpdateNote] = useState(appUpdate.note || '');
  const [updateUrl, setUpdateUrl] = useState(appUpdate.downloadUrl || '');

  const [targetEmail, setTargetEmail] = useState('');
  const [targetConfirmed, setTargetConfirmed] = useState(false);
  const [delegations, setDelegations] = useState<SparkDelegation[]>([]);
  const [feedback, setFeedback] = useState<{ text: string; isError: boolean } | null>(null);

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
        text: 'Google account verified! Recent authentication window refreshed for 5 minutes.',
        isError: false,
      });
    } catch (err) {
      setFeedback({ text: friendlyFirestoreMessage(err), isError: true });
    } finally {
      setBusy(false);
    }
  };

  const handlePreviewCommand = () => {
    setFeedback(null);
    const parsed = parseSparkCommand(commandDraft);
    setProposal(parsed);
    if (parsed.kind === 'unsupported') {
      setFeedback({
        text: 'Unrecognized command. Try "theme: lavender", "theme: default", "menu: top", "menu: bottom", "welcome: Hello!" or "update: 1.0.1 | Release note".',
        isError: true,
      });
    }
  };

  const handleApplyProposal = async () => {
    if (!proposal || proposal.kind === 'unsupported' || busy) return;
    setBusy(true);
    setFeedback(null);
    try {
      const msg = await applySparkProposal(currentUserId, proposal);
      setProposal(null);
      setCommandDraft('');
      setFeedback({ text: msg, isError: false });
    } catch (err) {
      setFeedback({ text: friendlyFirestoreMessage(err), isError: true });
    } finally {
      setBusy(false);
    }
  };

  const handlePreviewPublishUpdate = () => {
    const version = updateVersion.trim().slice(0, MAX_UPDATE_VERSION_LENGTH);
    const note = updateNote.trim().slice(0, MAX_UPDATE_NOTE_LENGTH);
    const downloadUrl = updateUrl.trim().slice(0, MAX_UPDATE_URL_LENGTH);
    if (!version) {
      setFeedback({ text: 'Enter a target version (e.g., 1.1.0).', isError: true });
      return;
    }
    if (downloadUrl && !downloadUrl.startsWith('https://')) {
      setFeedback({ text: 'Download URL must start with https://', isError: true });
      return;
    }
    setFeedback(null);
    setProposal({
      kind: 'setAppUpdate',
      active: true,
      version,
      note,
      downloadUrl,
    });
  };

  const handleInviteDelegate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!targetConfirmed || !isValidSparkTarget(targetEmail) || busy) return;
    setBusy(true);
    setFeedback(null);
    try {
      await inviteSparkDelegate(currentUserId, targetEmail);
      setTargetEmail('');
      setTargetConfirmed(false);
      setFeedback({ text: 'Delegation invitation sent.', isError: false });
    } catch (err) {
      setFeedback({ text: friendlyFirestoreMessage(err), isError: true });
    } finally {
      setBusy(false);
    }
  };

  const handleRevokeDelegate = async (email: string) => {
    if (busy) return;
    setBusy(true);
    setFeedback(null);
    try {
      await revokeSparkDelegate(email);
      setFeedback({ text: `Revoked delegation for ${email}.`, isError: false });
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
      setFeedback({ text: 'Delegation accepted! Spark Admin tools unlocked.', isError: false });
    } catch (err) {
      setFeedback({ text: friendlyFirestoreMessage(err), isError: true });
    } finally {
      setBusy(false);
    }
  };

  const describeProposal = (p: SparkCommandProposal): string => {
    switch (p.kind) {
      case 'setTheme':
        return `Set home appearance theme to "${p.value}"`;
      case 'setMenu':
        return `Set mobile navigation bar position to "${p.value}"`;
      case 'setWelcome':
        return `Set public welcome banner to "${p.value}"`;
      case 'setAppUpdate':
        return p.active
          ? `Publish green update badge for v${p.version}${p.note ? ` — "${p.note}"` : ''}`
          : 'Clear active app update badge notice';
      default:
        return '';
    }
  };

  return (
    <div className="h-full overflow-y-auto bg-[var(--chatbox-bg)] px-4 py-5 flex justify-center">
      <div className="w-full max-w-[820px] space-y-4 pb-8">
        {/* Header */}
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            <ChatboxLogoWithUpdateBadge
              appUpdateActive={appUpdate.active}
              onUpdateClick={onAppUpdateClick}
              size={36}
            />
            <div className="min-w-0">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-[#8f89a3]">
                SPARK ADMIN STUDIO
              </p>
              <h1 className="font-display text-xl font-bold text-[var(--chatbox-ink)]">
                Admin Controls
              </h1>
              <p className="text-xs text-[#8f89a3] truncate">
                {currentUserEmail || 'Not signed in with email'}
              </p>
            </div>
          </div>

          <button
            type="button"
            disabled={busy || !isOnline}
            onClick={handleReauth}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl border border-[var(--chatbox-border)] bg-[var(--chatbox-surface)] hover:bg-[#efeaf7] text-xs font-semibold text-[#5b4a8f] transition-colors cursor-pointer shrink-0"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            Verify Google Again
          </button>
        </div>

        {!isOnline && (
          <NoticeBanner
            isError
            text="Offline · Admin Studio actions require an active internet connection."
          />
        )}
        {feedback && <NoticeBanner isError={feedback.isError} text={feedback.text} />}
        {statusError && <NoticeBanner isError text={statusError} />}

        {/* Status Gate */}
        {status === 'CHECKING' && (
          <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-8 text-center text-sm text-[#8f89a3]">
            Checking Spark Admin permissions…
          </div>
        )}

        {status === 'DENIED' && (
          <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-6 text-center space-y-3">
            <div className="w-12 h-12 rounded-2xl bg-[#fff1ed] text-[#9b5145] mx-auto flex items-center justify-center">
              <Shield className="w-6 h-6" />
            </div>
            <h2 className="font-display text-lg font-bold text-[var(--chatbox-ink)]">
              Admin access locked
            </h2>
            <p className="text-sm text-[#6b657d] max-w-md mx-auto">
              Spark Admin Studio is restricted by Firestore security rules to the owner or invited active Gmail delegates.
            </p>
            <button
              type="button"
              onClick={runAccessCheck}
              className="px-4 py-2 rounded-xl bg-[#efeaf7] text-[#5b4a8f] text-xs font-semibold cursor-pointer"
            >
              Re-check Access
            </button>
          </div>
        )}

        {status === 'UNCLAIMED_OWNER' && (
          <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-6 text-center space-y-3">
            <ShieldCheck className="w-8 h-8 text-[#5b4a8f] mx-auto" />
            <h2 className="font-display text-lg font-bold text-[var(--chatbox-ink)]">
              Fresh authentication needed to claim ownership
            </h2>
            <p className="text-sm text-[#6b657d]">
              Click “Verify Google Again” to refresh your 5-minute Google auth window and register Spark Admin ownership.
            </p>
          </div>
        )}

        {status === 'INVITED_DELEGATE' && (
          <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-6 text-center space-y-3">
            <ShieldCheck className="w-8 h-8 text-[#5b4a8f] mx-auto" />
            <h2 className="font-display text-lg font-bold text-[var(--chatbox-ink)]">
              You have a Spark Admin invitation
            </h2>
            <p className="text-sm text-[#6b657d]">
              Accept this invitation with your verified Gmail account ({currentUserEmail}) to manage Chatbox public settings.
            </p>
            <button
              type="button"
              disabled={busy || !isOnline}
              onClick={handleAcceptDelegation}
              className="px-5 py-2.5 rounded-xl bg-[#6250a0] hover:bg-[#463a76] text-white text-sm font-semibold cursor-pointer"
            >
              Accept Delegation
            </button>
          </div>
        )}

        {(status === 'OWNER' || status === 'DELEGATE') && (
          <>
            {/* Live Snapshot */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-3.5">
                <p className="text-[11px] uppercase tracking-wider text-[#8f89a3] font-semibold">
                  Role
                </p>
                <p className="font-display text-sm font-bold text-[#5b4a8f] mt-1">
                  {status === 'OWNER' ? 'Spark Owner' : 'Active Delegate'}
                </p>
              </div>
              <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-3.5">
                <p className="text-[11px] uppercase tracking-wider text-[#8f89a3] font-semibold">
                  Theme / Menu
                </p>
                <p className="font-display text-sm font-bold text-[var(--chatbox-ink)] mt-1">
                  {appearance.theme} · {appearance.menu}
                </p>
              </div>
              <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-3.5">
                <p className="text-[11px] uppercase tracking-wider text-[#8f89a3] font-semibold">
                  Welcome Banner
                </p>
                <p className="text-xs text-[var(--chatbox-ink)] mt-1 truncate">
                  {welcomeBanner || '(Default — none set)'}
                </p>
              </div>
            </div>

            {/* Command Console */}
            <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-4 space-y-3">
              <div className="flex items-center gap-2">
                <Sparkles className="w-4 h-4 text-[#5b4a8f]" />
                <h2 className="font-display text-sm font-bold text-[var(--chatbox-ink)]">
                  Deterministic Command Console (Bangla & English)
                </h2>
              </div>
              <p className="text-xs text-[#6b657d]">
                Examples: <code className="bg-[#f7f5fb] px-1.5 py-0.5 rounded">theme: lavender</code>,{' '}
                <code className="bg-[#f7f5fb] px-1.5 py-0.5 rounded">theme: default</code>,{' '}
                <code className="bg-[#f7f5fb] px-1.5 py-0.5 rounded">menu: top</code>,{' '}
                <code className="bg-[#f7f5fb] px-1.5 py-0.5 rounded">welcome: Stay close ✨</code>
              </p>
              <div className="flex flex-col sm:flex-row gap-2">
                <input
                  type="text"
                  value={commandDraft}
                  onChange={(e) => setCommandDraft(e.target.value)}
                  placeholder="Type a Spark command to preview…"
                  className="flex-1 h-10 px-3.5 rounded-xl bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] text-sm focus:outline-none focus:border-[#5b4a8f]"
                />
                <button
                  type="button"
                  onClick={handlePreviewCommand}
                  disabled={!commandDraft.trim() || busy}
                  className="px-4 h-10 rounded-xl bg-[#efeaf7] hover:bg-[#e6dff5] text-[#5b4a8f] text-xs font-semibold cursor-pointer"
                >
                  Preview
                </button>
              </div>

              {proposal && proposal.kind !== 'unsupported' && (
                <div className="rounded-xl bg-[#f7f5fb] border border-[#5b4a8f]/30 p-3 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
                  <div>
                    <p className="text-[11px] font-bold uppercase text-[#5b4a8f]">
                      Proposal Ready
                    </p>
                    <p className="text-sm text-[var(--chatbox-ink)] font-medium">
                      {describeProposal(proposal)}
                    </p>
                  </div>
                  <div className="flex gap-2 shrink-0">
                    <button
                      type="button"
                      onClick={() => setProposal(null)}
                      className="px-3 py-1.5 rounded-lg border border-[var(--chatbox-border)] bg-white text-xs text-[#6b657d] cursor-pointer"
                    >
                      Cancel
                    </button>
                    <button
                      type="button"
                      disabled={busy || !isOnline}
                      onClick={handleApplyProposal}
                      className="px-3.5 py-1.5 rounded-lg bg-[#6250a0] hover:bg-[#463a76] text-white text-xs font-semibold cursor-pointer"
                    >
                      {busy ? 'Applying…' : 'Confirm & Apply'}
                    </button>
                  </div>
                </div>
              )}
            </div>

            {/* App Update Badge Studio */}
            <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-4 space-y-3">
              <h2 className="font-display text-sm font-bold text-[var(--chatbox-ink)]">
                App Update Badge Notice
              </h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                <input
                  type="text"
                  value={updateVersion}
                  onChange={(e) => setUpdateVersion(e.target.value.slice(0, MAX_UPDATE_VERSION_LENGTH))}
                  placeholder="Target version (e.g., 1.2.0)"
                  className="h-10 px-3.5 rounded-xl bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] text-sm focus:outline-none focus:border-[#5b4a8f]"
                />
                <input
                  type="url"
                  value={updateUrl}
                  onChange={(e) => setUpdateUrl(e.target.value.slice(0, MAX_UPDATE_URL_LENGTH))}
                  placeholder="Optional HTTPS release link"
                  className="h-10 px-3.5 rounded-xl bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] text-sm focus:outline-none focus:border-[#5b4a8f]"
                />
              </div>
              <input
                type="text"
                value={updateNote}
                onChange={(e) => setUpdateNote(e.target.value.slice(0, MAX_UPDATE_NOTE_LENGTH))}
                placeholder="Short summary of what is new…"
                className="w-full h-10 px-3.5 rounded-xl bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] text-sm focus:outline-none focus:border-[#5b4a8f]"
              />
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  disabled={busy}
                  onClick={handlePreviewPublishUpdate}
                  className="px-4 py-2 rounded-xl bg-[#16a34a] hover:bg-[#15803d] text-white text-xs font-semibold cursor-pointer"
                >
                  Preview Update Badge
                </button>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() =>
                    setProposal({
                      kind: 'setAppUpdate',
                      active: false,
                      version: '',
                      note: '',
                      downloadUrl: '',
                    })
                  }
                  className="px-4 py-2 rounded-xl border border-[var(--chatbox-border)] bg-white hover:bg-[#f7f5fb] text-xs font-semibold text-[#5a5468] cursor-pointer"
                >
                  Preview Clear Badge
                </button>
              </div>
            </div>

            {/* Owner Delegation Console */}
            {status === 'OWNER' && (
              <div className="rounded-2xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-4 space-y-3">
                <div className="flex items-center gap-2">
                  <UserPlus className="w-4 h-4 text-[#5b4a8f]" />
                  <h2 className="font-display text-sm font-bold text-[var(--chatbox-ink)]">
                    Gmail Admin Delegations
                  </h2>
                </div>
                <form onSubmit={handleInviteDelegate} className="space-y-2.5">
                  <input
                    type="email"
                    value={targetEmail}
                    onChange={(e) => setTargetEmail(e.target.value)}
                    placeholder="colleague@gmail.com"
                    className="w-full h-10 px-3.5 rounded-xl bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] text-sm focus:outline-none focus:border-[#5b4a8f]"
                  />
                  <label className="flex items-center gap-2 text-xs text-[#6b657d] cursor-pointer select-none">
                    <input
                      type="checkbox"
                      checked={targetConfirmed}
                      onChange={(e) => setTargetConfirmed(e.target.checked)}
                      className="accent-[#6250a0] w-4 h-4"
                    />
                    Confirm granting Spark Admin settings delegation to this Gmail
                  </label>
                  <button
                    type="submit"
                    disabled={!targetConfirmed || !isValidSparkTarget(targetEmail) || busy || !isOnline}
                    className="px-4 py-2 rounded-xl bg-[#6250a0] hover:bg-[#463a76] disabled:opacity-50 text-white text-xs font-semibold cursor-pointer"
                  >
                    Invite Gmail Delegate
                  </button>
                </form>

                {delegations.length > 0 && (
                  <div className="divide-y divide-[var(--chatbox-border)] pt-2">
                    {delegations.map((del) => (
                      <div key={del.email} className="py-2.5 flex items-center justify-between gap-2">
                        <div className="min-w-0">
                          <p className="text-sm font-medium text-[var(--chatbox-ink)] truncate">
                            {del.email}
                          </p>
                          <p className="text-[11px] text-[#8f89a3] uppercase font-semibold">
                            Status: {del.status}
                          </p>
                        </div>
                        {del.status !== 'revoked' ? (
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() => handleRevokeDelegate(del.email)}
                            className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-[#fff1ed] text-[#9b5145] text-xs font-semibold cursor-pointer"
                          >
                            <XCircle className="w-3.5 h-3.5" />
                            Revoke
                          </button>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-xs text-[#8f89a3]">
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
          </>
        )}
      </div>
    </div>
  );
}
