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
  MAX_DISPLAY_NAME_LENGTH,
  MAX_MESSAGE_BODY_LENGTH,
  VALID_ID_REGEX,
} from '../types/chatbox';

export function buildConversationId(uidA: string, uidB: string): string {
  return uidA < uidB ? `${uidA}_${uidB}` : `${uidB}_${uidA}`;
}

export async function ensureOwnProfile(uid: string, rawDisplayName?: string | null): Promise<void> {
  if (!VALID_ID_REGEX.test(uid)) {
    throw new Error('Invalid Chatbox user ID.');
  }
  const safeName = (rawDisplayName ?? '').trim().slice(0, MAX_DISPLAY_NAME_LENGTH) || 'Chatbox User';
  const path = `profiles/${uid}`;
  const ref = doc(db, 'profiles', uid);
  try {
    const existingSnap = await getDoc(ref);
    if (!existingSnap.exists()) {
      await setDoc(ref, { uid, displayName: safeName });
    } else {
      const existingData = existingSnap.data() as Partial<UserProfile>;
      if (existingData.displayName !== safeName && rawDisplayName?.trim()) {
        await setDoc(ref, { uid, displayName: safeName });
      }
    }
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path, true);
  }
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
  rawPeerUid: string
): Promise<string> {
  const peerUid = rawPeerUid.trim();
  if (!VALID_ID_REGEX.test(peerUid)) {
    throw new Error('Enter a valid Chatbox ID (letters, numbers, _ or -).');
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
    throw new Error('No Chatbox account was found for that Chatbox ID yet.');
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
