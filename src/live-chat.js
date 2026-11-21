import { getFirestore, doc, setDoc, getDocFromServer, getDocsFromServer, collection, addDoc, updateDoc, serverTimestamp, query, where, onSnapshot, orderBy, limitToLast, endBefore } from 'firebase/firestore';
import { app } from './firebase.js';
import { ChatInputError, normalizeMessage } from './chat-utils.js';
import { createLiveRequest } from './live-request.js';
import { createMessageFeed } from './message-feed.js';
import { EXPERIMENTAL_MESSAGE_COOLDOWN, EXPERIMENTAL_REQUEST_COOLDOWN } from './feature-flags.js';
import { sendThrottledMessage } from './throttled-message.js';
import { createThrottledRequest } from './throttled-request.js';

const db = getFirestore(app);

export async function registerProfile(user) {
  const displayName = (user.displayName || 'Chatbox user').trim().slice(0, 80) || 'Chatbox user';
  await setDoc(doc(db, 'profiles', user.uid), { uid: user.uid, displayName });
}

// Direct lookup only for the selected participant; no profile directory query.
export function watchPeerProfile(uid, next, fail) {
  return onSnapshot(doc(db, 'profiles', uid), {includeMetadataChanges:true}, next, fail);
}

export function watchConversations(uid, next, fail) {
  return onSnapshot(query(collection(db, 'conversations'), where('participants', 'array-contains', uid)), {includeMetadataChanges:true}, snapshot => {
    const items = snapshot.docs.map(item => ({ ...item.data(), id:item.id, pendingWrites:item.metadata.hasPendingWrites }));
    items.sort((a, b) => (b.createdAt?.toMillis?.() || 0) - (a.createdAt?.toMillis?.() || 0));
    next(items);
  }, fail);
}

export function watchMessages(id, next, fail, fatal = fail) {
  const messages = collection(db, 'conversations', id, 'messages');
  return createMessageFeed({
    // Metadata notifications distinguish accepted writes from optimistic ones
    // even when the document's visible fields do not change on acknowledgement.
    listenRecent: (onNext, onFail) => onSnapshot(query(messages, orderBy('sentAt'), limitToLast(100)), {includeMetadataChanges:true}, onNext, onFail),
    fetchOlder: cursor => getDocsFromServer(query(messages, orderBy('sentAt'), endBefore(cursor), limitToLast(100))),
    onChange: next,
    onError: fail,
    onFatalError: fatal,
  });
}

export async function createConversation(uid, peerUid, guard) {
  return createLiveRequest({
    uid, peerUid, guard,
    readPeer: async peer => (await getDocFromServer(doc(db, 'profiles', peer))).exists(),
    writeRequest: (sender, peer) => EXPERIMENTAL_REQUEST_COOLDOWN
      ? createThrottledRequest(db, sender, peer)
      : addDoc(collection(db, 'conversations'), { participants: [sender, peer], createdBy: sender, status:'pending', createdAt: serverTimestamp(), updatedAt: serverTimestamp() }),
  });
}

export async function respondToConversation(id, status) {
  if (!['active', 'closed'].includes(status)) throw new ChatInputError('invalid-request');
  return updateDoc(doc(db, 'conversations', id), { status, updatedAt: serverTimestamp() });
}

export async function sendMessage(conversationId, uid, body) {
  if (EXPERIMENTAL_MESSAGE_COOLDOWN) return sendThrottledMessage(db, conversationId, uid, body);
  return addDoc(collection(db, 'conversations', conversationId, 'messages'), { senderId: uid, body: normalizeMessage(body), sentAt: serverTimestamp() });
}
