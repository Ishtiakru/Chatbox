import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowLeft,
  ArrowUp,
  Ban,
  Check,
  ChevronDown,
  ChevronUp,
  Clock,
  Copy,
  Image as ImageIcon,
  Info,
  Mic,
  Phone,
  Plus,
  Search,
  ShieldCheck,
  Smile,
  Sparkles,
  UserPlus,
  Video,
  X,
} from 'lucide-react';
import type { QueryDocumentSnapshot, Unsubscribe } from 'firebase/firestore';
import { friendlyFirestoreMessage } from '../firebase';
import {
  uploadChatImage,
  uploadChatVoiceNote,
} from '../services/chatMediaService';
import {
  blockPeerUser,
  ensureOwnProfile,
  fetchOlderMessagesPage,
  observeBlockedPeerIds,
  observeConversationMessages,
  observePeerProfile,
  observeUserConversations,
  sendLiveMessage,
  startOrOpenConversation,
  unblockPeerUser,
  updateConversationStatus,
} from '../services/liveChatService';
import {
  askSairaCompanion,
  generateOneMinuteAutoReply,
} from '../services/sairaAiService';
import {
  getStoredQuickReaction,
  saveStoredQuickReaction,
} from '../services/storagePrefs';
import type {
  Conversation,
  LiveMessage,
  SairaCompanionMessage,
  UserProfile,
} from '../types/chatbox';
import { MAX_MESSAGE_BODY_LENGTH } from '../types/chatbox';
import {
  ChatboxLogoWithUpdateBadge,
  HighlightText,
  MessengerAvatar,
  NoticeBanner,
  SearchBar,
} from './Common';
import {
  PendingVoicePreviewBar,
  VoiceNoteBubblePlayer,
  VoiceRecordingBar,
} from './LiveVoiceComponents';

const QUICK_REACTION_OPTIONS = ['💜', '👍', '❤️', '😂', '😮', '🔥', '🎉', '🙏'];
const PAGE_SIZE = 100;
const SEARCH_MESSAGE_LIMIT = 100;
const INACTIVITY_DELAY_MS = 60_000;
const MAX_INACTIVITY_AGE_MS = 10 * 60_000;

function formatMsgTime(msg: LiveMessage, isOnline: boolean): string {
  if (msg.pendingWrites && !isOnline) return 'Queued · offline';
  if (msg.pendingWrites || !msg.sentAt) return 'Sending…';
  return msg.sentAt.toDate().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatGroupDate(msg: LiveMessage): string {
  if (!msg.sentAt) return '';
  const d = msg.sentAt.toDate();
  const ageDays = Math.floor((Date.now() - d.getTime()) / (24 * 60 * 60 * 1000));
  if (ageDays < 7) {
    return d.toLocaleDateString([], { weekday: 'short', hour: '2-digit', minute: '2-digit' });
  }
  return d.toLocaleDateString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export function LiveChatsScreen({
  currentUserId,
  displayName,
  isOnline,
  appUpdateActive,
  onAppUpdateClick,
  oneMinAutoReplyEnabled,
  onOpenCalls,
}: {
  currentUserId: string;
  displayName: string | null;
  isOnline: boolean;
  appUpdateActive: boolean;
  onAppUpdateClick: () => void;
  oneMinAutoReplyEnabled: boolean;
  onOpenCalls: () => void;
}) {
  const [profileReady, setProfileReady] = useState(false);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [loadingConversations, setLoadingConversations] = useState(true);
  const [convError, setConvError] = useState<string | null>(null);
  const [blockedIds, setBlockedIds] = useState<Set<string>>(new Set());
  const [peerProfiles, setPeerProfiles] = useState<Record<string, UserProfile>>({});
  const [messagesByConversation, setMessagesByConversation] = useState<
    Record<string, LiveMessage[]>
  >({});

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [mobilePane, setMobilePane] = useState<'list' | 'thread'>('list');
  const [showDetails, setShowDetails] = useState(false);

  const [showNewConversation, setShowNewConversation] = useState(false);
  const [peerDraft, setPeerDraft] = useState('');
  const [copiedId, setCopiedId] = useState(false);
  const [listSearch, setListSearch] = useState('');

  // Active thread messages + pagination
  const [messages, setMessages] = useState<LiveMessage[]>([]);
  const [olderMessages, setOlderMessages] = useState<LiveMessage[]>([]);
  const [oldestCursor, setOldestCursor] = useState<QueryDocumentSnapshot | null>(null);
  const [hasMoreOlder, setHasMoreOlder] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [threadError, setThreadError] = useState<string | null>(null);
  const [busyAction, setBusyAction] = useState(false);

  // Thread search
  const [threadSearchOpen, setThreadSearchOpen] = useState(false);
  const [threadSearchQuery, setThreadSearchQuery] = useState('');
  const [activeMatchIdx, setActiveMatchIdx] = useState(0);

  // Composer + Attachments
  const [messageDraft, setMessageDraft] = useState('');
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const [quickReaction, setQuickReaction] = useState(() => getStoredQuickReaction());
  const [pendingImageFile, setPendingImageFile] = useState<File | null>(null);
  const [pendingImagePreview, setPendingImagePreview] = useState<string | null>(null);
  const [uploadProgress, setUploadProgress] = useState<number | null>(null);
  const [lightboxImage, setLightboxImage] = useState<{ url: string; caption: string } | null>(null);

  // Voice recording & playback
  const [isRecording, setIsRecording] = useState(false);
  const [recordingMs, setRecordingMs] = useState(0);
  const [pendingVoice, setPendingVoice] = useState<{
    blob: Blob;
    url: string;
    durationMs: number;
  } | null>(null);
  const [playingAudioId, setPlayingAudioId] = useState<string | null>(null);
  const [audioIsPlaying, setAudioIsPlaying] = useState(false);
  const [audioProgress, setAudioProgress] = useState(0);

  // Saira 1-minute companion
  const [companionMessages, setCompanionMessages] = useState<SairaCompanionMessage[]>([]);
  const [companionDraft, setCompanionDraft] = useState('');
  const [companionBusy, setCompanionBusy] = useState(false);
  const [companionDismissedForMsgId, setCompanionDismissedForMsgId] = useState<string | null>(null);
  const triggeredOneMinMsgIdsRef = useRef<Set<string>>(new Set());

  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const recordChunksRef = useRef<BlobPart[]>([]);
  const recordTimerRef = useRef<number | null>(null);
  const recordStartRef = useRef<number>(0);
  const audioElRef = useRef<HTMLAudioElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const searchInputRef = useRef<HTMLInputElement | null>(null);
  const threadFeedRef = useRef<HTMLDivElement | null>(null);

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

  // Ensure user's own lookup profile exists
  useEffect(() => {
    let active = true;
    ensureOwnProfile(currentUserId, displayName)
      .then(() => {
        if (active) setProfileReady(true);
      })
      .catch((err) => {
        if (active) setConvError(friendlyFirestoreMessage(err));
      });
    return () => {
      active = false;
    };
  }, [currentUserId, displayName]);

  // Subscribe to conversations & blocked IDs
  useEffect(() => {
    setLoadingConversations(true);
    const unsubConvs = observeUserConversations(
      currentUserId,
      (list) => {
        setConversations(list);
        setLoadingConversations(false);
        setConvError(null);
      },
      (err) => {
        setLoadingConversations(false);
        setConvError(friendlyFirestoreMessage(err));
      }
    );
    const unsubBlocks = observeBlockedPeerIds(currentUserId, setBlockedIds);
    return () => {
      unsubConvs();
      unsubBlocks();
    };
  }, [currentUserId]);

  // Observe peer profiles for all conversations
  const peerUidsKey = useMemo(() => {
    const uids = conversations
      .map((c) => c.participants.find((p) => p !== currentUserId) ?? '')
      .filter(Boolean);
    return Array.from(new Set(uids)).sort().join(',');
  }, [conversations, currentUserId]);

  useEffect(() => {
    if (!peerUidsKey) return;
    const uids = peerUidsKey.split(',').filter(Boolean);
    const unsubs: Unsubscribe[] = uids.map((uid) =>
      observePeerProfile(uid, (profile) => {
        if (profile) {
          setPeerProfiles((prev) => ({ ...prev, [uid]: profile }));
        }
      })
    );
    return () => {
      unsubs.forEach((u) => u());
    };
  }, [peerUidsKey]);

  // Observe recent messages across all conversations so global search can filter by message content
  const convIdsKey = useMemo(
    () =>
      conversations
        .map((c) => c.id)
        .sort()
        .join(','),
    [conversations]
  );

  useEffect(() => {
    const convIds = convIdsKey ? convIdsKey.split(',').filter(Boolean) : [];
    const activeSet = new Set(convIds);
    setMessagesByConversation((prev) => {
      let changed = false;
      const next: Record<string, LiveMessage[]> = {};
      for (const [id, list] of Object.entries(prev)) {
        if (activeSet.has(id)) {
          next[id] = list;
        } else {
          changed = true;
        }
      }
      return changed ? next : prev;
    });

    if (convIds.length === 0) return;

    const unsubs: Unsubscribe[] = convIds.map((convId) =>
      observeConversationMessages(
        convId,
        SEARCH_MESSAGE_LIMIT,
        (items) => {
          setMessagesByConversation((prev) => ({
            ...prev,
            [convId]: items,
          }));
        },
        () => {
          // Read errors are surfaced through handleFirestoreError inside observeConversationMessages
        }
      )
    );

    return () => {
      unsubs.forEach((u) => u());
    };
  }, [convIdsKey]);

  const selectedConversation = useMemo(
    () => conversations.find((c) => c.id === selectedId) ?? null,
    [conversations, selectedId]
  );

  // Subscribe to active thread messages
  useEffect(() => {
    setOlderMessages([]);
    setOldestCursor(null);
    setHasMoreOlder(false);
    setMessages([]);
    setThreadError(null);
    setCompanionMessages([]);
    setCompanionDismissedForMsgId(null);

    if (!selectedId) return;

    const unsub = observeConversationMessages(
      selectedId,
      PAGE_SIZE,
      (items, firstDoc, hasMore) => {
        setMessages(items);
        setOldestCursor(firstDoc);
        setHasMoreOlder(hasMore);
      },
      (err) => setThreadError(friendlyFirestoreMessage(err))
    );
    return () => unsub();
  }, [selectedId]);

  const combinedMessages = useMemo(() => {
    const map = new Map<string, LiveMessage>();
    for (const m of olderMessages) map.set(m.id, m);
    for (const m of messages) map.set(m.id, m);
    return Array.from(map.values());
  }, [olderMessages, messages]);

  const combinedMessagesRef = useRef<LiveMessage[]>([]);
  combinedMessagesRef.current = combinedMessages;

  useEffect(() => {
    const q = listSearch.trim().toLowerCase();
    if (q && !threadSearchQuery.trim() && threadFeedRef.current) {
      const matched = [...combinedMessagesRef.current]
        .reverse()
        .find((m) => m.body.toLowerCase().includes(q));
      if (matched) {
        const nodes = threadFeedRef.current.querySelectorAll<HTMLElement>('[data-live-msg-id]');
        const el = Array.from(nodes).find((node) => node.dataset.liveMsgId === matched.id);
        if (el) {
          el.scrollIntoView({ behavior: 'smooth', block: 'center' });
          return;
        }
      }
    }
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length, selectedId, listSearch, threadSearchQuery]);

  const selectedPeerUid = selectedConversation
    ? selectedConversation.participants.find((p) => p !== currentUserId) ?? ''
    : '';
  const selectedPeerName =
    (selectedPeerUid && peerProfiles[selectedPeerUid]?.displayName) ||
    (selectedPeerUid ? `User ${selectedPeerUid.slice(0, 6)}` : 'Friend');
  const isPeerBlocked = Boolean(selectedPeerUid && blockedIds.has(selectedPeerUid));

  // Saira 1-minute inactivity check-in / absence auto-reply
  useEffect(() => {
    if (
      !selectedConversation ||
      selectedConversation.status !== 'active' ||
      isPeerBlocked ||
      !isOnline ||
      !oneMinAutoReplyEnabled
    ) {
      return;
    }

    const latest = combinedMessages[combinedMessages.length - 1];
    if (!latest || latest.pendingWrites || !latest.sentAt) return;
    if (latest.body.startsWith('[Saira Assistant]')) return;
    if (triggeredOneMinMsgIdsRef.current.has(latest.id)) return;

    const ageMs = Date.now() - latest.sentAt.toMillis();
    if (ageMs > MAX_INACTIVITY_AGE_MS) return;

    const remainingMs = Math.max(0, INACTIVITY_DELAY_MS - ageMs);
    const convId = selectedConversation.id;
    const isWaitingOnPeer = latest.senderId === currentUserId;

    const timer = window.setTimeout(async () => {
      if (triggeredOneMinMsgIdsRef.current.has(latest.id)) return;
      triggeredOneMinMsgIdsRef.current.add(latest.id);
      try {
        const reply = await generateOneMinuteAutoReply({
          senderName: isWaitingOnPeer ? displayName || 'Friend' : selectedPeerName,
          peerName: isWaitingOnPeer ? selectedPeerName : displayName || 'Friend',
          lastMessageText: latest.body || (latest.imageUrl ? 'Photo' : 'Voice note'),
          isSenderWaitingOnPeer: isWaitingOnPeer,
        });
        if (!reply) return;
        if (isWaitingOnPeer) {
          setCompanionMessages([
            {
              id: `${Date.now()}`,
              text: reply,
              isFromUser: false,
              timestampMs: Date.now(),
            },
          ]);
        } else {
          await sendLiveMessage(convId, currentUserId, reply);
        }
      } catch {
        // Silent background assist failure
      }
    }, remainingMs);

    return () => window.clearTimeout(timer);
  }, [
    selectedConversation,
    combinedMessages,
    isPeerBlocked,
    isOnline,
    oneMinAutoReplyEnabled,
    currentUserId,
    displayName,
    selectedPeerName,
  ]);

  const handleCopyId = async () => {
    try {
      await navigator.clipboard.writeText(currentUserId);
      setCopiedId(true);
      window.setTimeout(() => setCopiedId(false), 2000);
    } catch {
      // Ignore clipboard failure
    }
  };

  const handleStartConversation = async (e: React.FormEvent) => {
    e.preventDefault();
    if (busyAction || !peerDraft.trim()) return;
    setBusyAction(true);
    setConvError(null);
    try {
      const convId = await startOrOpenConversation(currentUserId, peerDraft);
      setPeerDraft('');
      setShowNewConversation(false);
      setSelectedId(convId);
      setMobilePane('thread');
    } catch (err) {
      setConvError(err instanceof Error ? err.message : friendlyFirestoreMessage(err));
    } finally {
      setBusyAction(false);
    }
  };

  const handleLoadOlder = async () => {
    if (!selectedId || !oldestCursor || loadingOlder) return;
    setLoadingOlder(true);
    try {
      const page = await fetchOlderMessagesPage(selectedId, oldestCursor, 50);
      setOlderMessages((prev) => [...page.messages, ...prev]);
      setOldestCursor(page.firstDoc);
      setHasMoreOlder(page.hasMore);
    } catch (err) {
      setThreadError(friendlyFirestoreMessage(err));
    } finally {
      setLoadingOlder(false);
    }
  };

  const handleSelectImage = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      setThreadError('Please choose an image file.');
      return;
    }
    if (pendingImagePreview) URL.revokeObjectURL(pendingImagePreview);
    setPendingImageFile(file);
    setPendingImagePreview(URL.createObjectURL(file));
    setThreadError(null);
  };

  const clearPendingImage = () => {
    if (pendingImagePreview) URL.revokeObjectURL(pendingImagePreview);
    setPendingImageFile(null);
    setPendingImagePreview(null);
  };

  const startVoiceRecording = async () => {
    if (isRecording || !navigator.mediaDevices?.getUserMedia) {
      setThreadError('Microphone recording is not supported in this browser.');
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = MediaRecorder.isTypeSupported('audio/webm')
        ? 'audio/webm'
        : MediaRecorder.isTypeSupported('audio/mp4')
          ? 'audio/mp4'
          : '';
      const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
      recordChunksRef.current = [];
      recorder.ondataavailable = (ev) => {
        if (ev.data.size > 0) recordChunksRef.current.push(ev.data);
      };
      recorder.onstop = () => {
        stream.getTracks().forEach((t) => t.stop());
      };
      mediaRecorderRef.current = recorder;
      recordStartRef.current = Date.now();
      setRecordingMs(0);
      setIsRecording(true);
      recorder.start();

      recordTimerRef.current = window.setInterval(() => {
        const elapsed = Date.now() - recordStartRef.current;
        setRecordingMs(elapsed);
        if (elapsed >= 60_000) {
          void stopVoiceRecording(false);
        }
      }, 200);
    } catch {
      setThreadError('Microphone permission denied or unavailable.');
    }
  };

  const cancelVoiceRecording = () => {
    if (recordTimerRef.current) window.clearInterval(recordTimerRef.current);
    recordTimerRef.current = null;
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop();
    }
    setIsRecording(false);
    setRecordingMs(0);
  };

  const stopVoiceRecording = async (sendImmediately: boolean) => {
    if (recordTimerRef.current) window.clearInterval(recordTimerRef.current);
    recordTimerRef.current = null;
    const recorder = mediaRecorderRef.current;
    if (!recorder) {
      setIsRecording(false);
      return;
    }
    const durationMs = Math.max(500, Date.now() - recordStartRef.current);
    const blob = await new Promise<Blob>((resolve) => {
      const prevOnStop = recorder.onstop;
      recorder.onstop = (ev) => {
        if (prevOnStop) prevOnStop.call(recorder, ev);
        resolve(new Blob(recordChunksRef.current, { type: recorder.mimeType || 'audio/webm' }));
      };
      if (recorder.state !== 'inactive') recorder.stop();
      else resolve(new Blob(recordChunksRef.current, { type: recorder.mimeType || 'audio/webm' }));
    });
    setIsRecording(false);
    setRecordingMs(0);

    const url = URL.createObjectURL(blob);
    if (pendingVoice?.url) URL.revokeObjectURL(pendingVoice.url);

    if (sendImmediately && selectedConversation) {
      await sendWithMedia('', null, { blob, url, durationMs });
    } else {
      setPendingVoice({ blob, url, durationMs });
    }
  };

  const clearPendingVoice = () => {
    if (pendingVoice?.url) URL.revokeObjectURL(pendingVoice.url);
    setPendingVoice(null);
  };

  const togglePlayAudio = (id: string, url: string) => {
    if (playingAudioId === id && audioElRef.current) {
      if (audioElRef.current.paused) {
        void audioElRef.current.play();
        setAudioIsPlaying(true);
      } else {
        audioElRef.current.pause();
        setAudioIsPlaying(false);
      }
      return;
    }

    if (audioElRef.current) {
      audioElRef.current.pause();
      audioElRef.current = null;
    }

    const audio = new Audio(url);
    audioElRef.current = audio;
    setPlayingAudioId(id);
    setAudioIsPlaying(true);
    setAudioProgress(0);

    audio.ontimeupdate = () => {
      if (audio.duration > 0) {
        setAudioProgress(audio.currentTime / audio.duration);
      }
    };
    audio.onended = () => {
      setAudioIsPlaying(false);
      setAudioProgress(0);
      setPlayingAudioId(null);
    };
    audio.onerror = () => {
      setAudioIsPlaying(false);
      setPlayingAudioId(null);
    };
    void audio.play();
  };

  const sendWithMedia = async (
    textBody: string,
    imageFile: File | null,
    voiceData: { blob: Blob; url: string; durationMs: number } | null
  ) => {
    if (!selectedConversation || busyAction) return;
    setBusyAction(true);
    setThreadError(null);
    try {
      let imageUrl: string | null = null;
      let audioUrl: string | null = null;
      let audioDurationMs: number | null = null;

      if (imageFile) {
        setUploadProgress(0.05);
        imageUrl = await uploadChatImage(
          selectedConversation.id,
          currentUserId,
          imageFile,
          (p) => setUploadProgress(p)
        );
      }
      if (voiceData) {
        setUploadProgress(0.05);
        audioUrl = await uploadChatVoiceNote(
          selectedConversation.id,
          currentUserId,
          voiceData.blob,
          (p) => setUploadProgress(p)
        );
        audioDurationMs = voiceData.durationMs;
      }

      await sendLiveMessage(selectedConversation.id, currentUserId, textBody, {
        imageUrl,
        audioUrl,
        audioDurationMs,
      });

      setMessageDraft('');
      clearPendingImage();
      clearPendingVoice();
    } catch (err) {
      setThreadError(err instanceof Error ? err.message : friendlyFirestoreMessage(err));
    } finally {
      setUploadProgress(null);
      setBusyAction(false);
    }
  };

  const handleSendComposer = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!selectedConversation || busyAction) return;
    const text = messageDraft.trim();
    if (!text && !pendingImageFile && !pendingVoice) return;
    await sendWithMedia(text, pendingImageFile, pendingVoice);
  };

  const handleQuickReaction = async () => {
    if (!selectedConversation || busyAction || !isOnline) return;
    await sendWithMedia(quickReaction, null, null);
  };

  const handleSendCompanion = async (e: React.FormEvent) => {
    e.preventDefault();
    const q = companionDraft.trim();
    if (!q || companionBusy) return;
    setCompanionDraft('');
    setCompanionBusy(true);
    setCompanionMessages((prev) => [
      ...prev,
      { id: `${Date.now()}_u`, text: q, isFromUser: true, timestampMs: Date.now() },
    ]);
    try {
      const reply = await askSairaCompanion({
        userName: displayName || 'Friend',
        peerName: selectedPeerName,
        userQuestion: q,
      });
      setCompanionMessages((prev) => [
        ...prev,
        { id: `${Date.now()}_a`, text: reply, isFromUser: false, timestampMs: Date.now() },
      ]);
    } catch {
      setCompanionMessages((prev) => [
        ...prev,
        {
          id: `${Date.now()}_err`,
          text: 'Could not reach Saira right now. Please try again in a moment.',
          isFromUser: false,
          timestampMs: Date.now(),
        },
      ]);
    } finally {
      setCompanionBusy(false);
    }
  };

  // Filtered conversations & requests (matches contact name, Chatbox ID, or message content)
  const filteredConvs = useMemo(() => {
    const q = listSearch.trim().toLowerCase();
    if (!q) return conversations;
    return conversations.filter((conv) => {
      const peerUid = conv.participants.find((p) => p !== currentUserId) ?? '';
      const name = peerProfiles[peerUid]?.displayName ?? peerUid;
      if (name.toLowerCase().includes(q) || peerUid.toLowerCase().includes(q)) {
        return true;
      }
      const msgs =
        conv.id === selectedId
          ? combinedMessages
          : (messagesByConversation[conv.id] ?? []);
      return msgs.some((m) => m.body.toLowerCase().includes(q));
    });
  }, [
    conversations,
    listSearch,
    currentUserId,
    peerProfiles,
    selectedId,
    combinedMessages,
    messagesByConversation,
  ]);

  const incomingRequests = filteredConvs.filter(
    (c) => c.status === 'pending' && c.createdBy !== currentUserId
  );
  const regularChats = filteredConvs.filter(
    (c) => !(c.status === 'pending' && c.createdBy !== currentUserId)
  );

  // Thread search match indices
  const threadMatchIds = useMemo(() => {
    const q = threadSearchQuery.trim().toLowerCase();
    if (!q) return [];
    return combinedMessages
      .filter((m) => m.body.toLowerCase().includes(q))
      .map((m) => m.id);
  }, [combinedMessages, threadSearchQuery]);

  const effectiveThreadHighlight = threadSearchQuery.trim() ? threadSearchQuery : listSearch;

  const latestMsgId = combinedMessages[combinedMessages.length - 1]?.id ?? null;
  const showCompanion =
    companionMessages.length > 0 && companionDismissedForMsgId !== latestMsgId;

  const composerEnabled =
    Boolean(selectedConversation) &&
    selectedConversation?.status === 'active' &&
    !isPeerBlocked &&
    !busyAction;

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
                  REAL-TIME FIRESTORE
                </p>
                <h2 className="font-display text-lg font-bold text-[var(--chatbox-ink)]">
                  Live Chats
                </h2>
              </div>
            </div>
            <button
              type="button"
              onClick={() => setShowNewConversation((v) => !v)}
              title="Start new conversation"
              className="w-9 h-9 rounded-xl bg-[#efeaf7] hover:bg-[#e6dff5] text-[#5b4a8f] flex items-center justify-center cursor-pointer"
            >
              <Plus className="w-4 h-4" />
            </button>
          </div>

          {/* Copyable Chatbox ID pill */}
          <div className="rounded-xl bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] px-3 py-2 flex items-center justify-between gap-2">
            <div className="min-w-0">
              <p className="text-[10px] font-semibold uppercase tracking-wider text-[#8f89a3]">
                Your Chatbox ID {profileReady ? '· Ready' : '· Syncing…'}
              </p>
              <p className="text-xs font-mono text-[var(--chatbox-ink)] truncate">
                {currentUserId}
              </p>
            </div>
            <button
              type="button"
              onClick={handleCopyId}
              className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-white border border-[var(--chatbox-border)] text-xs font-medium text-[#5b4a8f] hover:bg-[#efeaf7] shrink-0 cursor-pointer"
            >
              {copiedId ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
              {copiedId ? 'Copied' : 'Copy'}
            </button>
          </div>

          {showNewConversation && (
            <form
              onSubmit={handleStartConversation}
              className="rounded-xl bg-[var(--chatbox-field-bg)] border border-[#5b4a8f]/30 p-3 space-y-2"
            >
              <p className="text-xs font-semibold text-[var(--chatbox-ink)]">
                Start conversation by Chatbox ID
              </p>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={peerDraft}
                  onChange={(e) => setPeerDraft(e.target.value)}
                  placeholder="Paste friend's Chatbox ID…"
                  className="flex-1 h-9 px-3 rounded-lg bg-white border border-[var(--chatbox-border)] text-xs focus:outline-none focus:border-[#5b4a8f]"
                />
                <button
                  type="submit"
                  disabled={!peerDraft.trim() || busyAction || !isOnline}
                  className="px-3 h-9 rounded-lg bg-[#6250a0] hover:bg-[#463a76] disabled:opacity-50 text-white text-xs font-semibold flex items-center gap-1 cursor-pointer"
                >
                  <UserPlus className="w-3.5 h-3.5" />
                  Send
                </button>
              </div>
            </form>
          )}

          <div className="space-y-1.5">
            <SearchBar
              value={listSearch}
              onChange={setListSearch}
              placeholder="Search conversations by name or message…"
              ariaLabel="Global search across Live Chats by contact name or message content"
              inputRef={searchInputRef}
            />
            {listSearch.trim() && !loadingConversations && (
              <p className="text-[11px] font-medium text-[#5b4a8f] px-1">
                {filteredConvs.length === 0
                  ? 'No matching conversations'
                  : `${filteredConvs.length} matching ${
                      filteredConvs.length === 1 ? 'conversation' : 'conversations'
                    }`}
              </p>
            )}
          </div>

          {!isOnline && (
            <NoticeBanner
              isError
              text="Offline · Text messages sent now queue locally until you reconnect. Media & requests need internet."
            />
          )}
          {convError && <NoticeBanner isError text={convError} />}
        </div>

        <div className="flex-1 overflow-y-auto p-2 space-y-3">
          {loadingConversations ? (
            <p className="p-4 text-center text-xs text-[#8f89a3]">Loading conversations…</p>
          ) : filteredConvs.length === 0 ? (
            listSearch.trim() ? (
              <div className="p-6 text-center space-y-2.5">
                <p className="text-sm font-medium text-[var(--chatbox-ink)]">
                  No conversations match &ldquo;{listSearch.trim()}&rdquo;
                </p>
                <p className="text-xs text-[#8f89a3]">
                  Try another contact name, Chatbox ID, or message keyword.
                </p>
                <button
                  type="button"
                  onClick={() => setListSearch('')}
                  className="inline-flex items-center px-3 py-1.5 rounded-xl bg-[#efeaf7] hover:bg-[#e6dff5] text-[#5b4a8f] text-xs font-semibold cursor-pointer"
                >
                  Clear search
                </button>
              </div>
            ) : (
              <div className="p-6 text-center space-y-2">
                <p className="text-sm font-medium text-[var(--chatbox-ink)]">No live chats yet</p>
                <p className="text-xs text-[#8f89a3]">
                  Share your Chatbox ID or click + to send a conversation request.
                </p>
              </div>
            )
          ) : (
            <>
              {incomingRequests.length > 0 && (
                <div className="space-y-1">
                  <p className="text-[10px] font-bold uppercase tracking-wider text-[#5b4a8f] px-2 pt-1">
                    MESSAGE REQUESTS ({incomingRequests.length})
                  </p>
                  {incomingRequests.map((conv) => {
                    const peerUid = conv.participants.find((p) => p !== currentUserId) ?? '';
                    const name = peerProfiles[peerUid]?.displayName || `User ${peerUid.slice(0, 6)}`;
                    const selected = conv.id === selectedId;
                    const msgs =
                      conv.id === selectedId
                        ? combinedMessages
                        : (messagesByConversation[conv.id] ?? []);
                    const q = listSearch.trim().toLowerCase();
                    const matchedMsg = q
                      ? [...msgs].reverse().find((m) => m.body.toLowerCase().includes(q))
                      : undefined;
                    return (
                      <button
                        key={conv.id}
                        type="button"
                        onClick={() => {
                          setSelectedId(conv.id);
                          setMobilePane('thread');
                        }}
                        className={`w-full flex items-center gap-3 p-3 rounded-2xl text-left transition-colors cursor-pointer ${
                          selected ? 'bg-[var(--chatbox-selected-bg)]' : 'hover:bg-[var(--chatbox-field-bg)]'
                        }`}
                      >
                        <MessengerAvatar label={name} size={40} />
                        <div className="flex-1 min-w-0">
                          <p className="font-display text-sm font-semibold text-[var(--chatbox-ink)] truncate">
                            <HighlightText text={name} query={listSearch} />
                          </p>
                          <p className="text-xs text-[#5b4a8f] truncate">
                            {matchedMsg ? (
                              <>
                                {matchedMsg.senderId === currentUserId && <span>You: </span>}
                                <HighlightText text={matchedMsg.body} query={listSearch} />
                              </>
                            ) : (
                              'Wants to message you · Tap to review'
                            )}
                          </p>
                        </div>
                      </button>
                    );
                  })}
                </div>
              )}

              {regularChats.length > 0 && (
                <div className="space-y-1">
                  <p className="text-[10px] font-bold uppercase tracking-wider text-[#8f89a3] px-2 pt-1">
                    CHATS ({regularChats.length})
                  </p>
                  {regularChats.map((conv) => {
                    const peerUid = conv.participants.find((p) => p !== currentUserId) ?? '';
                    const name = peerProfiles[peerUid]?.displayName || `User ${peerUid.slice(0, 6)}`;
                    const blocked = blockedIds.has(peerUid);
                    const selected = conv.id === selectedId;
                    const msgs =
                      conv.id === selectedId
                        ? combinedMessages
                        : (messagesByConversation[conv.id] ?? []);
                    const q = listSearch.trim().toLowerCase();
                    const matchedMsg = q
                      ? [...msgs].reverse().find((m) => m.body.toLowerCase().includes(q))
                      : undefined;
                    const latestMsg = msgs[msgs.length - 1];
                    const statusText = blocked
                      ? 'Blocked'
                      : conv.status === 'pending'
                        ? 'Request sent · pending'
                        : conv.status === 'closed'
                          ? 'Conversation closed'
                          : 'Active conversation';
                    return (
                      <button
                        key={conv.id}
                        type="button"
                        onClick={() => {
                          setSelectedId(conv.id);
                          setMobilePane('thread');
                        }}
                        className={`w-full flex items-center gap-3 p-3 rounded-2xl text-left transition-colors cursor-pointer ${
                          selected ? 'bg-[var(--chatbox-selected-bg)]' : 'hover:bg-[var(--chatbox-field-bg)]'
                        }`}
                      >
                        <MessengerAvatar label={name} size={40} />
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center justify-between gap-1.5">
                            <p className="font-display text-sm font-semibold text-[var(--chatbox-ink)] truncate">
                              <HighlightText text={name} query={listSearch} />
                            </p>
                            {latestMsg?.pendingWrites && (
                              <Clock className="w-3 h-3 text-[#5b4a8f] shrink-0" />
                            )}
                          </div>
                          <p className="text-xs text-[#6b657d] truncate">
                            {matchedMsg ? (
                              <>
                                {matchedMsg.senderId === currentUserId && <span>You: </span>}
                                <HighlightText text={matchedMsg.body} query={listSearch} />
                              </>
                            ) : latestMsg ? (
                              <>
                                {latestMsg.senderId === currentUserId && <span>You: </span>}
                                {latestMsg.body ||
                                  (latestMsg.imageUrl
                                    ? 'Photo'
                                    : latestMsg.audioUrl
                                      ? 'Voice note'
                                      : statusText)}
                              </>
                            ) : (
                              statusText
                            )}
                          </p>
                        </div>
                      </button>
                    );
                  })}
                </div>
              )}
            </>
          )}
        </div>
      </aside>

      {/* Center Thread Pane */}
      <main
        className={`${
          mobilePane === 'thread' ? 'flex' : 'hidden'
        } md:flex flex-1 flex-col min-w-0 bg-[var(--chatbox-bg)]`}
      >
        {!selectedConversation ? (
          <div className="flex-1 flex flex-col items-center justify-center p-6 text-center space-y-3">
            <div className="w-14 h-14 rounded-2xl bg-[#efeaf7] text-[#5b4a8f] flex items-center justify-center">
              <ShieldCheck className="w-7 h-7" />
            </div>
            <h3 className="font-display text-lg font-bold text-[var(--chatbox-ink)]">
              Select a live conversation
            </h3>
            <p className="text-sm text-[#6b657d] max-w-sm">
              Choose a conversation on the left or tap + to invite a friend using their Chatbox ID.
            </p>
          </div>
        ) : (
          <>
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
                <MessengerAvatar label={selectedPeerName} size={38} />
                <div className="min-w-0">
                  <h3 className="font-display text-sm font-bold text-[var(--chatbox-ink)] truncate">
                    {selectedPeerName}
                  </h3>
                  <p className="text-[11px] text-[#8f89a3] truncate">
                    {isPeerBlocked
                      ? 'Blocked'
                      : selectedConversation.status === 'pending'
                        ? 'Pending consent'
                        : selectedConversation.status === 'closed'
                          ? 'Closed'
                          : isOnline
                            ? 'Active · Firestore real-time'
                            : 'Offline · text queue'}
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-1">
                <button
                  type="button"
                  onClick={onOpenCalls}
                  title="Voice call"
                  className="w-9 h-9 rounded-xl hover:bg-[#f7f5fb] text-[#5b4a8f] flex items-center justify-center cursor-pointer"
                >
                  <Phone className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  onClick={onOpenCalls}
                  title="Video call"
                  className="w-9 h-9 rounded-xl hover:bg-[#f7f5fb] text-[#5b4a8f] flex items-center justify-center cursor-pointer"
                >
                  <Video className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  onClick={() => setThreadSearchOpen((v) => !v)}
                  title="Search in conversation"
                  className={`w-9 h-9 rounded-xl flex items-center justify-center cursor-pointer ${
                    threadSearchOpen ? 'bg-[#efeaf7] text-[#5b4a8f]' : 'hover:bg-[#f7f5fb] text-[#5b4a8f]'
                  }`}
                >
                  <Search className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  onClick={() => setShowDetails((v) => !v)}
                  title="Conversation details"
                  className={`w-9 h-9 rounded-xl flex items-center justify-center cursor-pointer ${
                    showDetails ? 'bg-[#efeaf7] text-[#5b4a8f]' : 'hover:bg-[#f7f5fb] text-[#5b4a8f]'
                  }`}
                >
                  <Info className="w-4 h-4" />
                </button>
              </div>
            </header>

            {/* Thread Search Bar */}
            {threadSearchOpen && (
              <div className="px-4 py-2 border-b border-[var(--chatbox-border)] bg-[var(--chatbox-field-bg)] flex items-center gap-2">
                <input
                  type="text"
                  value={threadSearchQuery}
                  onChange={(e) => {
                    setThreadSearchQuery(e.target.value);
                    setActiveMatchIdx(0);
                  }}
                  placeholder="Search messages in this chat…"
                  className="flex-1 h-9 px-3 rounded-lg bg-white border border-[var(--chatbox-border)] text-xs focus:outline-none focus:border-[#5b4a8f]"
                />
                {threadMatchIds.length > 0 && (
                  <span className="text-xs text-[#5b4a8f] font-medium tabular-nums">
                    {activeMatchIdx + 1}/{threadMatchIds.length}
                  </span>
                )}
                <button
                  type="button"
                  disabled={threadMatchIds.length === 0}
                  onClick={() =>
                    setActiveMatchIdx((i) => (i - 1 + threadMatchIds.length) % threadMatchIds.length)
                  }
                  className="p-1.5 rounded-lg hover:bg-white disabled:opacity-40 cursor-pointer"
                >
                  <ChevronUp className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  disabled={threadMatchIds.length === 0}
                  onClick={() =>
                    setActiveMatchIdx((i) => (i + 1) % threadMatchIds.length)
                  }
                  className="p-1.5 rounded-lg hover:bg-white disabled:opacity-40 cursor-pointer"
                >
                  <ChevronDown className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setThreadSearchOpen(false);
                    setThreadSearchQuery('');
                  }}
                  className="p-1.5 rounded-lg hover:bg-white cursor-pointer"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            )}

            {/* Message Feed */}
            <div ref={threadFeedRef} className="flex-1 overflow-y-auto p-4 space-y-2">
              {hasMoreOlder && (
                <div className="text-center pb-2">
                  <button
                    type="button"
                    disabled={loadingOlder}
                    onClick={handleLoadOlder}
                    className="px-4 py-1.5 rounded-xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] text-xs font-medium text-[#5b4a8f] hover:bg-[#efeaf7] cursor-pointer"
                  >
                    {loadingOlder ? 'Loading…' : 'Load earlier messages'}
                  </button>
                </div>
              )}

              {threadError && <NoticeBanner isError text={threadError} />}

              {combinedMessages.map((msg, idx) => {
                const prev = combinedMessages[idx - 1];
                const next = combinedMessages[idx + 1];
                const isOwn = msg.senderId === currentUserId;
                const groupedWithPrev = Boolean(prev && prev.senderId === msg.senderId);
                const groupedWithNext = Boolean(next && next.senderId === msg.senderId);
                const showDateHeader =
                  !prev ||
                  (prev.sentAt &&
                    msg.sentAt &&
                    msg.sentAt.toMillis() - prev.sentAt.toMillis() > 30 * 60_000);
                const isPureEmoji =
                  !msg.imageUrl &&
                  !msg.audioUrl &&
                  QUICK_REACTION_OPTIONS.includes(msg.body.trim());
                const highlightedMatchId = threadMatchIds[activeMatchIdx];
                const isHighlighted = msg.id === highlightedMatchId;

                return (
                  <React.Fragment key={msg.id}>
                    {showDateHeader && msg.sentAt && (
                      <div className="text-center py-2">
                        <span className="text-[11px] font-medium text-[#8f89a3]">
                          {formatGroupDate(msg)}
                        </span>
                      </div>
                    )}
                    <div
                      data-live-msg-id={msg.id}
                      className={`flex items-end gap-2 ${
                        isOwn ? 'justify-end' : 'justify-start'
                      } ${groupedWithPrev ? 'mt-0.5' : 'mt-2'}`}
                    >
                      {!isOwn && (
                        <div className="w-7 shrink-0">
                          {!groupedWithNext && <MessengerAvatar label={selectedPeerName} size={26} />}
                        </div>
                      )}

                      <div
                        className={`flex flex-col ${
                          isOwn ? 'items-end' : 'items-start'
                        } max-w-[78%] sm:max-w-[340px]`}
                      >
                        {isPureEmoji ? (
                          <span className="text-4xl px-2 py-1">{msg.body.trim()}</span>
                        ) : (
                          <div
                            className={`rounded-2xl overflow-hidden ${
                              isOwn
                                ? 'bg-[var(--chatbox-live-outgoing)] text-white rounded-br-xs'
                                : 'bg-[var(--chatbox-live-incoming)] text-[#1f2937] rounded-bl-xs'
                            } ${isHighlighted ? 'ring-2 ring-[#5b4a8f]' : ''}`}
                          >
                            {msg.imageUrl && (
                              <button
                                type="button"
                                onClick={() =>
                                  setLightboxImage({ url: msg.imageUrl!, caption: msg.body })
                                }
                                className="block p-1 cursor-pointer"
                              >
                                <img
                                  src={msg.imageUrl}
                                  alt="Attached image"
                                  className="max-h-64 w-auto rounded-xl object-cover"
                                />
                              </button>
                            )}
                            {msg.audioUrl && (
                              <VoiceNoteBubblePlayer
                                messageId={msg.id}
                                audioUrl={msg.audioUrl}
                                durationMs={msg.audioDurationMs}
                                isOwn={isOwn}
                                playingId={playingAudioId}
                                isPlaying={audioIsPlaying}
                                progressFraction={audioProgress}
                                onTogglePlay={togglePlayAudio}
                              />
                            )}
                            {msg.body && (
                              <div className="px-3.5 py-2 text-sm whitespace-pre-wrap break-words">
                                <HighlightText
                                  text={msg.body}
                                  query={effectiveThreadHighlight}
                                  highlightClassName={
                                    isOwn
                                      ? 'bg-white/30 text-white font-semibold rounded-xs px-0.5'
                                      : 'bg-[#5b4a8f]/20 text-[#5b4a8f] font-semibold rounded-xs px-0.5'
                                  }
                                />
                              </div>
                            )}
                          </div>
                        )}

                        {(!groupedWithNext || msg.pendingWrites) && (
                          <div className="flex items-center gap-1 mt-0.5 px-1 text-[10px] text-[#8f89a3]">
                            {msg.pendingWrites && <Clock className="w-3 h-3 text-[#5b4a8f]" />}
                            <span>{formatMsgTime(msg, isOnline)}</span>
                          </div>
                        )}
                      </div>
                    </div>
                  </React.Fragment>
                );
              })}

              {/* Saira Thread Companion Card */}
              {showCompanion && (
                <div className="mt-3 rounded-2xl bg-[#efeaf7]/80 border border-[#5b4a8f]/30 p-3 space-y-2.5">
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-1.5 text-xs font-semibold text-[#5b4a8f]">
                      <Sparkles className="w-4 h-4" />
                      <span>Saira Assistant · {selectedPeerName} hasn’t replied yet</span>
                    </div>
                    <button
                      type="button"
                      onClick={() => setCompanionDismissedForMsgId(latestMsgId)}
                      className="p-1 rounded-lg hover:bg-white/60 text-[#6b657d] cursor-pointer"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                  <div className="space-y-1.5">
                    {companionMessages.slice(-4).map((cm) => (
                      <div
                        key={cm.id}
                        className={`flex ${cm.isFromUser ? 'justify-end' : 'justify-start'}`}
                      >
                        <div
                          className={`max-w-[85%] px-3 py-2 rounded-xl text-xs ${
                            cm.isFromUser
                              ? 'bg-[#6250a0] text-white'
                              : 'bg-white text-[#2a2340] border border-[var(--chatbox-border)]'
                          }`}
                        >
                          {cm.text}
                        </div>
                      </div>
                    ))}
                  </div>
                  <form onSubmit={handleSendCompanion} className="flex items-center gap-2">
                    <input
                      type="text"
                      value={companionDraft}
                      disabled={companionBusy}
                      onChange={(e) => setCompanionDraft(e.target.value)}
                      placeholder="Ask Saira while waiting…"
                      className="flex-1 h-9 px-3 rounded-full bg-white border border-[var(--chatbox-border)] text-xs focus:outline-none focus:border-[#5b4a8f]"
                    />
                    <button
                      type="submit"
                      disabled={companionBusy || !companionDraft.trim()}
                      className="w-9 h-9 rounded-full bg-[#6250a0] disabled:opacity-50 text-white flex items-center justify-center cursor-pointer"
                    >
                      <ArrowUp className="w-3.5 h-3.5" />
                    </button>
                  </form>
                </div>
              )}

              <div ref={messagesEndRef} />
            </div>

            {/* Lifecycle / Pending Consent Bar */}
            {selectedConversation.status !== 'active' && (
              <div className="px-4 py-3 border-t border-[var(--chatbox-border)] bg-[var(--chatbox-surface)] space-y-2">
                {selectedConversation.status === 'pending' &&
                selectedConversation.createdBy === currentUserId ? (
                  <>
                    <NoticeBanner text="Waiting for the other person to accept. Messaging is unavailable until then." />
                    <button
                      type="button"
                      disabled={busyAction}
                      onClick={() => updateConversationStatus(selectedConversation.id, 'closed')}
                      className="w-full py-2 rounded-xl border border-[var(--chatbox-border)] text-xs font-semibold text-[#5a5468] hover:bg-[#f7f5fb] cursor-pointer"
                    >
                      Cancel request
                    </button>
                  </>
                ) : selectedConversation.status === 'pending' ? (
                  <>
                    <NoticeBanner text="This person requested a conversation. Accept only if you trust them." />
                    <div className="grid grid-cols-2 gap-2">
                      <button
                        type="button"
                        disabled={busyAction || !isOnline || isPeerBlocked}
                        onClick={() => updateConversationStatus(selectedConversation.id, 'active')}
                        className="py-2.5 rounded-xl bg-[#6250a0] hover:bg-[#463a76] disabled:opacity-50 text-white text-xs font-semibold cursor-pointer"
                      >
                        Accept request
                      </button>
                      <button
                        type="button"
                        disabled={busyAction}
                        onClick={() => updateConversationStatus(selectedConversation.id, 'closed')}
                        className="py-2.5 rounded-xl border border-[var(--chatbox-border)] text-xs font-semibold text-[#5a5468] hover:bg-[#f7f5fb] cursor-pointer"
                      >
                        Decline
                      </button>
                    </div>
                  </>
                ) : (
                  <NoticeBanner text="This conversation is closed. Previous messages remain visible." />
                )}
              </div>
            )}

            {/* Active Composer */}
            {selectedConversation.status === 'active' && !isPeerBlocked && (
              <div className="p-3 border-t border-[var(--chatbox-border)] bg-[var(--chatbox-surface)] space-y-2">
                {pendingImagePreview && (
                  <div className="relative inline-block">
                    <img
                      src={pendingImagePreview}
                      alt="Pending upload"
                      className="h-20 w-auto rounded-xl border border-[var(--chatbox-border)] object-cover"
                    />
                    <button
                      type="button"
                      onClick={clearPendingImage}
                      className="absolute -top-2 -right-2 w-6 h-6 rounded-full bg-[#2a2340] text-white flex items-center justify-center cursor-pointer"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                )}

                {isRecording && (
                  <VoiceRecordingBar
                    elapsedMs={recordingMs}
                    onCancel={cancelVoiceRecording}
                    onStopAndPreview={() => void stopVoiceRecording(false)}
                  />
                )}

                {pendingVoice && !isRecording && (
                  <PendingVoicePreviewBar
                    durationMs={pendingVoice.durationMs}
                    isPlaying={audioIsPlaying && playingAudioId === 'pending_preview'}
                    onTogglePreview={() => togglePlayAudio('pending_preview', pendingVoice.url)}
                    onRemove={clearPendingVoice}
                  />
                )}

                {uploadProgress !== null && (
                  <div className="w-full h-1.5 rounded-full bg-[#ece9f3] overflow-hidden">
                    <div
                      style={{ width: `${Math.round(uploadProgress * 100)}%` }}
                      className="h-full bg-[#6250a0] transition-all"
                    />
                  </div>
                )}

                {showEmojiPicker && (
                  <div className="flex flex-wrap gap-1.5 p-2 rounded-xl bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)]">
                    {QUICK_REACTION_OPTIONS.map((emoji) => (
                      <button
                        key={emoji}
                        type="button"
                        onClick={() => {
                          setMessageDraft((prev) => `${prev}${emoji}`);
                          setShowEmojiPicker(false);
                        }}
                        className="w-9 h-9 rounded-lg hover:bg-white text-lg flex items-center justify-center cursor-pointer"
                      >
                        {emoji}
                      </button>
                    ))}
                  </div>
                )}

                <form onSubmit={handleSendComposer} className="flex items-center gap-1.5">
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    onChange={handleSelectImage}
                    className="hidden"
                  />
                  <button
                    type="button"
                    disabled={!composerEnabled || !isOnline || isRecording}
                    onClick={() => fileInputRef.current?.click()}
                    title="Attach image"
                    className="w-9 h-9 rounded-full hover:bg-[#f7f5fb] disabled:opacity-40 text-[#5b4a8f] flex items-center justify-center shrink-0 cursor-pointer"
                  >
                    <ImageIcon className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    disabled={!composerEnabled || !isOnline}
                    onClick={() =>
                      isRecording ? void stopVoiceRecording(false) : void startVoiceRecording()
                    }
                    title="Record voice note"
                    className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0 cursor-pointer disabled:opacity-40 ${
                      isRecording
                        ? 'bg-[#fff1ed] text-[#9b5145]'
                        : 'hover:bg-[#f7f5fb] text-[#5b4a8f]'
                    }`}
                  >
                    <Mic className="w-4 h-4" />
                  </button>

                  <input
                    type="text"
                    value={messageDraft}
                    disabled={!composerEnabled || isRecording}
                    onChange={(e) =>
                      setMessageDraft(e.target.value.slice(0, MAX_MESSAGE_BODY_LENGTH))
                    }
                    placeholder={isOnline ? 'Message…' : 'Offline · text queues for sending'}
                    className="flex-1 h-10 px-4 rounded-full bg-[var(--chatbox-field-bg)] border border-[var(--chatbox-border)] text-sm text-[var(--chatbox-ink)] placeholder:text-[#8f89a3] focus:outline-none focus:border-[#5b4a8f]"
                  />

                  <button
                    type="button"
                    disabled={!composerEnabled || isRecording}
                    onClick={() => setShowEmojiPicker((v) => !v)}
                    title="Emoji"
                    className="w-9 h-9 rounded-full hover:bg-[#f7f5fb] disabled:opacity-40 text-[#5b4a8f] flex items-center justify-center shrink-0 cursor-pointer"
                  >
                    <Smile className="w-4 h-4" />
                  </button>

                  {messageDraft.trim() || pendingImageFile || pendingVoice || !isOnline ? (
                    <button
                      type="submit"
                      disabled={
                        !composerEnabled ||
                        (!messageDraft.trim() && !pendingImageFile && !pendingVoice)
                      }
                      aria-label="Send"
                      className="w-10 h-10 rounded-full bg-[#6250a0] hover:bg-[#463a76] disabled:bg-[#ece9f3] disabled:text-[#8f89a3] text-white flex items-center justify-center shrink-0 transition-colors cursor-pointer"
                    >
                      <ArrowUp className="w-4 h-4" />
                    </button>
                  ) : (
                    <button
                      type="button"
                      disabled={!composerEnabled}
                      onClick={() => void handleQuickReaction()}
                      title="Send quick reaction"
                      className="w-10 h-10 rounded-full hover:bg-[#efeaf7] text-xl flex items-center justify-center shrink-0 cursor-pointer"
                    >
                      {quickReaction}
                    </button>
                  )}
                </form>
              </div>
            )}
          </>
        )}
      </main>

      {/* Right Conversation Details Pane */}
      {showDetails && selectedConversation && (
        <aside className="hidden lg:flex w-[290px] shrink-0 flex-col border-l border-[var(--chatbox-border)] bg-[var(--chatbox-surface)] p-4 space-y-4 overflow-y-auto">
          <div className="flex flex-col items-center text-center pt-2 space-y-2">
            <MessengerAvatar label={selectedPeerName} size={60} />
            <h4 className="font-display text-base font-bold text-[var(--chatbox-ink)]">
              {selectedPeerName}
            </h4>
            <p className="text-[11px] font-mono text-[#8f89a3] break-all px-2">
              {selectedPeerUid}
            </p>
          </div>

          <div className="border-t border-[var(--chatbox-border)] pt-3 space-y-2">
            <p className="text-[11px] font-bold uppercase tracking-wider text-[#8f89a3]">
              Quick Reaction
            </p>
            <div className="flex flex-wrap gap-1.5">
              {QUICK_REACTION_OPTIONS.map((emoji) => (
                <button
                  key={emoji}
                  type="button"
                  onClick={() => {
                    setQuickReaction(emoji);
                    saveStoredQuickReaction(emoji);
                  }}
                  className={`w-9 h-9 rounded-xl text-lg flex items-center justify-center border cursor-pointer ${
                    quickReaction === emoji
                      ? 'bg-[#efeaf7] border-[#5b4a8f]'
                      : 'border-[var(--chatbox-border)] hover:bg-[#f7f5fb]'
                  }`}
                >
                  {emoji}
                </button>
              ))}
            </div>
          </div>

          <div className="border-t border-[var(--chatbox-border)] pt-3 space-y-2">
            <p className="text-[11px] font-bold uppercase tracking-wider text-[#8f89a3]">
              Safety & Controls
            </p>
            {selectedConversation.status === 'active' && (
              <button
                type="button"
                disabled={busyAction}
                onClick={() => void updateConversationStatus(selectedConversation.id, 'closed')}
                className="w-full py-2 rounded-xl border border-[var(--chatbox-border)] hover:bg-[#f7f5fb] text-xs font-semibold text-[#5a5468] cursor-pointer"
              >
                Close conversation
              </button>
            )}

            {isPeerBlocked ? (
              <button
                type="button"
                disabled={busyAction || !isOnline}
                onClick={() => void unblockPeerUser(currentUserId, selectedPeerUid)}
                className="w-full py-2 rounded-xl bg-[#efeaf7] text-[#5b4a8f] text-xs font-semibold cursor-pointer"
              >
                Unblock {selectedPeerName}
              </button>
            ) : (
              <button
                type="button"
                disabled={busyAction || !isOnline}
                onClick={() =>
                  void blockPeerUser(currentUserId, selectedPeerUid, selectedConversation)
                }
                className="w-full py-2 rounded-xl bg-[#fff1ed] hover:bg-[#fde3dc] text-[#9b5145] text-xs font-semibold inline-flex items-center justify-center gap-1.5 cursor-pointer"
              >
                <Ban className="w-3.5 h-3.5" />
                Block user
              </button>
            )}
          </div>

          <NoticeBanner text="Display names are unverified and messages are stored in Cloud Firestore (not end-to-end encrypted)." />
        </aside>
      )}

      {/* Fullscreen Image Lightbox */}
      {lightboxImage && (
        <div
          className="fixed inset-0 z-50 bg-black/95 flex items-center justify-center p-4"
          onClick={() => setLightboxImage(null)}
        >
          <button
            type="button"
            onClick={() => setLightboxImage(null)}
            aria-label="Close image viewer"
            className="absolute top-4 right-4 w-10 h-10 rounded-full bg-black/60 text-white flex items-center justify-center cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
          <img
            src={lightboxImage.url}
            alt="Full screen attachment"
            className="max-w-full max-h-[85vh] object-contain rounded-xl"
            onClick={(e) => e.stopPropagation()}
          />
          {lightboxImage.caption && (
            <div className="absolute bottom-6 max-w-lg px-4 py-2 rounded-xl bg-black/70 text-white text-sm">
              {lightboxImage.caption}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
