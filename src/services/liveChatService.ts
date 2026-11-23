import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  endBefore,
  getDoc,
  getDocFromServer,
  getDocs,
  limitToLast,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
  type QueryDocumentSnapshot,
  type Unsubscribe,
} from 'firebase/firestore';
import { db, handleFirestoreError, OperationType } from '../firebase';
import type { Conversation, LiveMessage, UserProfile } from '../types/chatbox';
import {
  MAX_ABOUT_LENGTH,
  MAX_DISPLAY_NAME_LENGTH,
  MAX_MESSAGE_BODY_LENGTH,
  MAX_PHONE_LENGTH,
  VALID_ID_REGEX,
  VALID_PHONE_KEY_REGEX,
  VALID_SHORT_ID_REGEX,
} from '../types/chatbox';

export const DEFAULT_ABOUT_TEXT = 'Hey there! I am using Chatbox.';

export function buildConversationId(uidA: string, uidB: string): string {
  return uidA < uidB ? `${uidA}_${uidB}` : `${uidB}_${uidA}`;
}

function deterministicSixDigitSeed(uid: string): string {
  let hash = 2166136261;
  for (let i = 0; i < uid.length; i += 1) {
    hash ^= uid.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  const num = 100000 + (Math.abs(hash) % 900000);
  return String(num);
}

export function normalizePhoneDigits(raw: string): string {
  return raw.replace(/\D/g, '').slice(0, 15);
}

function buildPhoneLookupCandidates(raw: string): string[] {
  const digits = normalizePhoneDigits(raw);
  if (!digits) return [];
  const candidates = [digits];
  if (digits.startsWith('0') && digits.length === 11) {
    candidates.push(`88${digits}`);
  } else if (digits.startsWith('880') && digits.length === 13) {
    candidates.push(digits.slice(2));
  }
  return Array.from(new Set(candidates)).filter((c) => VALID_PHONE_KEY_REGEX.test(c));
}

async function allocateUniqueShortId(uid: string, displayName: string): Promise<string> {
  const firstCandidate = deterministicSixDigitSeed(uid);
  const candidates = [firstCandidate];
  for (let i = 0; i < 6; i += 1) {
    candidates.push(String(100000 + Math.floor(Math.random() * 900000)));
  }

  for (const candidate of candidates) {
    try {
      const ref = doc(db, 'shortIds', candidate);
      const snap = await getDocFromServer(ref);
      if (!snap.exists()) {
        await setDoc(ref, { uid, displayName });
        return candidate;
      }
      if (snap.data().uid === uid) {
        if (snap.data().displayName !== displayName) {
          await updateDoc(ref, { displayName });
        }
        return candidate;
      }
    } catch {
      // Try next candidate if claimed or conflict occurs
    }
  }
  return firstCandidate;
}

export async function ensureOwnProfile(
  uid: string,
  rawDisplayName?: string | null
): Promise<UserProfile> {
  if (!VALID_ID_REGEX.test(uid)) {
    throw new Error('Invalid Chatbox user ID.');
  }
  const fallbackName = (rawDisplayName ?? '').trim().slice(0, MAX_DISPLAY_NAME_LENGTH) || 'Chatbox User';
  const path = `profiles/${uid}`;
  const ref = doc(db, 'profiles', uid);
  try {
    const existingSnap = await getDoc(ref);
    if (!existingSnap.exists()) {
      const shortId = await allocateUniqueShortId(uid, fallbackName);
      const profile: UserProfile = {
        uid,
        displayName: fallbackName,
        shortId,
        about: DEFAULT_ABOUT_TEXT,
      };
      await setDoc(ref, profile);
      return profile;
    }

    const existingData = existingSnap.data() as Partial<UserProfile>;
    const displayName =
      (existingData.displayName && existingData.displayName.trim()) || fallbackName;
    const about =
      typeof existingData.about === 'string' ? existingData.about : DEFAULT_ABOUT_TEXT;
    let shortId =
      typeof existingData.shortId === 'string' && VALID_SHORT_ID_REGEX.test(existingData.shortId)
        ? existingData.shortId
        : '';

    if (!shortId) {
      shortId = await allocateUniqueShortId(uid, displayName);
      await setDoc(ref, { uid, displayName, shortId, about });
    } else {
      // Ensure shortIds index stays synced
      try {
        const indexSnap = await getDoc(doc(db, 'shortIds', shortId));
        if (!indexSnap.exists()) {
          await setDoc(doc(db, 'shortIds', shortId), { uid, displayName });
        }
      } catch {
        // Ignore non-fatal index sync failure
      }
    }

    return { uid, displayName, shortId, about };
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path, true);
    throw error;
  }
}

export async function loadOwnProfileDetails(
  uid: string,
  rawDisplayName?: string | null
): Promise<{ profile: UserProfile; phoneNumber: string }> {
  const profile = await ensureOwnProfile(uid, rawDisplayName);
  let phoneNumber = '';
  try {
    const privSnap = await getDoc(doc(db, 'users', uid, 'private', 'profile'));
    if (privSnap.exists()) {
      const data = privSnap.data() as { phoneNumber?: unknown };
      if (typeof data.phoneNumber === 'string') {
        phoneNumber = data.phoneNumber;
      }
    }
  } catch {
    // Private profile may not exist yet
  }
  return { profile, phoneNumber };
}

export async function saveOwnWhatsAppProfile(params: {
  uid: string;
  shortId: string;
  displayName: string;
  about: string;
  phoneNumber: string;
  previousPhoneNumber: string;
}): Promise<{ profile: UserProfile; phoneNumber: string }> {
  const cleanName = params.displayName.trim().slice(0, MAX_DISPLAY_NAME_LENGTH);
  if (!cleanName) {
    throw new Error('Display name cannot be empty.');
  }
  const cleanAbout =
    params.about.trim().slice(0, MAX_ABOUT_LENGTH) || DEFAULT_ABOUT_TEXT;
  const cleanPhone = params.phoneNumber.trim().slice(0, MAX_PHONE_LENGTH);
  const newDigits = normalizePhoneDigits(cleanPhone);
  const oldDigits = normalizePhoneDigits(params.previousPhoneNumber);

  if (cleanPhone && !VALID_PHONE_KEY_REGEX.test(newDigits)) {
    throw new Error('Enter a valid phone number (7 to 15 digits, e.g. +8801XXXXXXXXX).');
  }

  let shortId = params.shortId;
  if (!VALID_SHORT_ID_REGEX.test(shortId)) {
    shortId = await allocateUniqueShortId(params.uid, cleanName);
  }

  // Update public profile
  const profilePath = `profiles/${params.uid}`;
  try {
    await setDoc(doc(db, 'profiles', params.uid), {
      uid: params.uid,
      displayName: cleanName,
      shortId,
      about: cleanAbout,
    });
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, profilePath, true);
  }

  // Sync shortId display name
  try {
    const shortRef = doc(db, 'shortIds', shortId);
    const shortSnap = await getDoc(shortRef);
    if (!shortSnap.exists()) {
      await setDoc(shortRef, { uid: params.uid, displayName: cleanName });
    } else if (shortSnap.data().uid === params.uid) {
      await updateDoc(shortRef, { displayName: cleanName });
    }
  } catch {
    // Non-fatal
  }

  // Update phoneDirectory exact lookup index
  if (oldDigits && oldDigits !== newDigits && VALID_PHONE_KEY_REGEX.test(oldDigits)) {
    try {
      const oldPhoneSnap = await getDoc(doc(db, 'phoneDirectory', oldDigits));
      if (oldPhoneSnap.exists() && oldPhoneSnap.data().uid === params.uid) {
        await deleteDoc(doc(db, 'phoneDirectory', oldDigits));
      }
    } catch {
      // Ignore cleanup errors
    }
  }

  if (newDigits && VALID_PHONE_KEY_REGEX.test(newDigits)) {
    const phonePath = `phoneDirectory/${newDigits}`;
    try {
      const phoneRef = doc(db, 'phoneDirectory', newDigits);
      const existingPhoneSnap = await getDoc(phoneRef);
      if (existingPhoneSnap.exists() && existingPhoneSnap.data().uid !== params.uid) {
        throw new Error('This phone number is already linked to another Chatbox account.');
      }
      if (existingPhoneSnap.exists()) {
        await updateDoc(phoneRef, { displayName: cleanName });
      } else {
        await setDoc(phoneRef, { uid: params.uid, displayName: cleanName });
      }
    } catch (error) {
      if (error instanceof Error && error.message.includes('already linked')) {
        throw error;
      }
      handleFirestoreError(error, OperationType.WRITE, phonePath, true);
    }
  }

  // Update owner-private phone storage
  const privPath = `users/${params.uid}/private/profile`;
  try {
    await setDoc(doc(db, 'users', params.uid, 'private', 'profile'), {
      phoneNumber: cleanPhone,
      updatedAt: serverTimestamp(),
    });
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, privPath, true);
  }

  return {
    profile: {
      uid: params.uid,
      displayName: cleanName,
      shortId,
      about: cleanAbout,
    },
    phoneNumber: cleanPhone,
  };
}

export function observePeerProfile(
  uid: string,
  onProfile: (profile: UserProfile | null) => void
): Unsubscribe {
  if (!VALID_ID_REGEX.test(uid)) {
    onProfile(null);
    return () => {};
  }
  const path = `profiles/${uid}`;
  return onSnapshot(
    doc(db, 'profiles', uid),
    (snap) => {
      if (snap.exists()) {
        const data = snap.data() as Partial<UserProfile>;
        onProfile({
          uid: data.uid ?? uid,
          displayName: data.displayName ?? 'Chatbox User',
          shortId: typeof data.shortId === 'string' ? data.shortId : undefined,
          about: typeof data.about === 'string' ? data.about : undefined,
        });
      } else {
        onProfile(null);
      }
    },
    (error) => {
      handleFirestoreError(error, OperationType.GET, path, false);
      onProfile(null);
    }
  );
}

export function observeUserConversations(
  uid: string,
  onConversations: (conversations: Conversation[]) => void,
  onError: (error: unknown) => void
): Unsubscribe {
  const path = 'conversations';
  const q = query(collection(db, path), where('participants', 'array-contains', uid));
  return onSnapshot(
    q,
    (snapshot) => {
      const list: Conversation[] = snapshot.docs.map((d) => {
        const data = d.data() as Omit<Conversation, 'id'>;
        return {
          id: d.id,
          participants: Array.isArray(data.participants) ? data.participants : [],
          createdBy: data.createdBy ?? '',
          status: data.status ?? 'pending',
          createdAt: data.createdAt ?? null,
          updatedAt: data.updatedAt ?? null,
        };
      });
      list.sort((a, b) => {
        const msA = a.updatedAt?.toMillis?.() ?? a.createdAt?.toMillis?.() ?? 0;
        const msB = b.updatedAt?.toMillis?.() ?? b.createdAt?.toMillis?.() ?? 0;
        return msB - msA;
      });
      onConversations(list);
    },
    (error) => {
      handleFirestoreError(error, OperationType.LIST, path, false);
      onError(error);
    }
  );
}

export function observeBlockedPeerIds(
  uid: string,
  onBlocked: (ids: Set<string>) => void
): Unsubscribe {
  const path = `userBlocks/${uid}/blocked`;
  return onSnapshot(
    collection(db, 'userBlocks', uid, 'blocked'),
    (snapshot) => {
      onBlocked(new Set(snapshot.docs.map((d) => d.id)));
    },
    (error) => {
      handleFirestoreError(error, OperationType.LIST, path, false);
    }
  );
}

export function observeConversationMessages(
  conversationId: string,
  messageLimit: number,
  onMessages: (messages: LiveMessage[], firstDoc: QueryDocumentSnapshot | null, hasMore: boolean) => void,
  onError: (error: unknown) => void
): Unsubscribe {
  const path = `conversations/${conversationId}/messages`;
  const q = query(
    collection(db, 'conversations', conversationId, 'messages'),
    orderBy('sentAt', 'asc'),
    limitToLast(messageLimit)
  );
  return onSnapshot(
    q,
    { includeMetadataChanges: true },
    (snapshot) => {
      const docs = snapshot.docs;
      const items: LiveMessage[] = docs.map((d) => {
        const data = d.data();
        return {
          id: d.id,
          senderId: String(data.senderId ?? ''),
          body: String(data.body ?? ''),
          imageUrl: typeof data.imageUrl === 'string' ? data.imageUrl : null,
          audioUrl: typeof data.audioUrl === 'string' ? data.audioUrl : null,
          audioDurationMs: typeof data.audioDurationMs === 'number' ? data.audioDurationMs : null,
          sentAt: data.sentAt ?? null,
          pendingWrites: d.metadata.hasPendingWrites,
        };
      });
      onMessages(items, docs[0] ?? null, docs.length >= messageLimit);
    },
    (error) => {
      handleFirestoreError(error, OperationType.LIST, path, false);
      onError(error);
    }
  );
}

export async function fetchOlderMessagesPage(
  conversationId: string,
  beforeDoc: QueryDocumentSnapshot,
  pageSize = 50
): Promise<{ messages: LiveMessage[]; firstDoc: QueryDocumentSnapshot | null; hasMore: boolean }> {
  const path = `conversations/${conversationId}/messages`;
  try {
    const q = query(
      collection(db, 'conversations', conversationId, 'messages'),
      orderBy('sentAt', 'asc'),
      endBefore(beforeDoc),
      limitToLast(pageSize)
    );
    const snap = await getDocs(q);
    const items: LiveMessage[] = snap.docs.map((d) => {
      const data = d.data();
      return {
        id: d.id,
        senderId: String(data.senderId ?? ''),
        body: String(data.body ?? ''),
        imageUrl: typeof data.imageUrl === 'string' ? data.imageUrl : null,
        audioUrl: typeof data.audioUrl === 'string' ? data.audioUrl : null,
        audioDurationMs: typeof data.audioDurationMs === 'number' ? data.audioDurationMs : null,
        sentAt: data.sentAt ?? null,
        pendingWrites: d.metadata.hasPendingWrites,
      };
    });
    return {
      messages: items,
      firstDoc: snap.docs[0] ?? null,
      hasMore: snap.docs.length >= pageSize,
    };
  } catch (error) {
    handleFirestoreError(error, OperationType.LIST, path, true);
    throw error;
  }
}

export async function startOrOpenConversation(
  currentUid: string,
  rawIdentifier: string
): Promise<string> {
  const trimmed = rawIdentifier.trim();
  if (!trimmed) {
    throw new Error('Enter a 6-digit Chatbox ID or phone number.');
  }

  let peerUid = '';

  // 1. Check 6-digit Chatbox ID code
  if (VALID_SHORT_ID_REGEX.test(trimmed)) {
    const shortSnap = await getDocFromServer(doc(db, 'shortIds', trimmed));
    if (!shortSnap.exists()) {
      throw new Error(`No user found with 6-digit ID "${trimmed}".`);
    }
    peerUid = String(shortSnap.data().uid ?? '');
  } else {
    // 2. Check Phone number lookup (e.g. +8801..., 017...)
    const looksLikePhone = /^[\d+\-\s()]{7,24}$/.test(trimmed);
    const phoneCandidates = looksLikePhone ? buildPhoneLookupCandidates(trimmed) : [];
    if (phoneCandidates.length > 0) {
      for (const candidate of phoneCandidates) {
        const phoneSnap = await getDocFromServer(doc(db, 'phoneDirectory', candidate));
        if (phoneSnap.exists()) {
          peerUid = String(phoneSnap.data().uid ?? '');
          break;
        }
      }
      if (!peerUid) {
        throw new Error(`No Chatbox account is linked to phone number "${trimmed}". Ask them to add their number in Profile or share their 6-digit ID.`);
      }
    } else if (VALID_ID_REGEX.test(trimmed)) {
      // 3. Fallback direct UID lookup
      peerUid = trimmed;
    } else {
      throw new Error('Enter a valid 6-digit ID code (e.g. 482910) or phone number.');
    }
  }

  if (peerUid === currentUid) {
    throw new Error('You cannot start a conversation with yourself.');
  }

  const peerProfilePath = `profiles/${peerUid}`;
  let peerExists = false;
  try {
    const peerSnap = await getDocFromServer(doc(db, 'profiles', peerUid));
    peerExists = peerSnap.exists();
  } catch (error) {
    handleFirestoreError(error, OperationType.GET, peerProfilePath, true);
  }
  if (!peerExists) {
    throw new Error('No Chatbox account was found for that ID yet.');
  }

  const conversationId = buildConversationId(currentUid, peerUid);
  const convPath = `conversations/${conversationId}`;
  try {
    const convRef = doc(db, 'conversations', conversationId);
    const existing = await getDoc(convRef);
    if (existing.exists()) {
      return conversationId;
    }
    const participants = [currentUid, peerUid].sort();
    await setDoc(convRef, {
      participants,
      createdBy: currentUid,
      status: 'pending',
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    return conversationId;
  } catch (error) {
    handleFirestoreError(error, OperationType.CREATE, convPath, true);
    throw error;
  }
}

export async function updateConversationStatus(
  conversationId: string,
  status: 'active' | 'closed'
): Promise<void> {
  const path = `conversations/${conversationId}`;
  try {
    await updateDoc(doc(db, 'conversations', conversationId), {
      status,
      updatedAt: serverTimestamp(),
    });
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path, true);
  }
}

export async function sendLiveMessage(
  conversationId: string,
  senderId: string,
  rawBody: string,
  media?: {
    imageUrl?: string | null;
    audioUrl?: string | null;
    audioDurationMs?: number | null;
  }
): Promise<void> {
  const body = rawBody.trim().slice(0, MAX_MESSAGE_BODY_LENGTH);
  const imageUrl = media?.imageUrl?.trim() || null;
  const audioUrl = media?.audioUrl?.trim() || null;
  const audioDurationMs =
    audioUrl && typeof media?.audioDurationMs === 'number'
      ? Math.max(0, Math.min(300000, Math.round(media.audioDurationMs)))
      : null;

  if (!body && !imageUrl && !audioUrl) {
    throw new Error('Cannot send an empty message.');
  }

  const payload: Record<string, unknown> = {
    senderId,
    body,
    sentAt: serverTimestamp(),
  };
  if (imageUrl) payload.imageUrl = imageUrl;
  if (audioUrl) {
    payload.audioUrl = audioUrl;
    if (audioDurationMs !== null) {
      payload.audioDurationMs = audioDurationMs;
    }
  }

  const path = `conversations/${conversationId}/messages`;
  try {
    await addDoc(collection(db, 'conversations', conversationId, 'messages'), payload);
  } catch (error) {
    handleFirestoreError(error, OperationType.CREATE, path, true);
  }
}

export async function blockPeerUser(
  currentUid: string,
  peerUid: string,
  conversation?: Conversation | null
): Promise<void> {
  const path = `userBlocks/${currentUid}/blocked/${peerUid}`;
  try {
    const batch = writeBatch(db);
    batch.set(doc(db, 'userBlocks', currentUid, 'blocked', peerUid), {
      createdAt: serverTimestamp(),
    });
    if (conversation && (conversation.status === 'pending' || conversation.status === 'active')) {
      batch.update(doc(db, 'conversations', conversation.id), {
        status: 'closed',
        updatedAt: serverTimestamp(),
      });
    }
    await batch.commit();
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path, true);
  }
}

export async function unblockPeerUser(currentUid: string, peerUid: string): Promise<void> {
  const path = `userBlocks/${currentUid}/blocked/${peerUid}`;
  try {
    await deleteDoc(doc(db, 'userBlocks', currentUid, 'blocked', peerUid));
  } catch (error) {
    handleFirestoreError(error, OperationType.DELETE, path, true);
  }
}
