// Experimental, not enabled in the production UI. Firestore rules, not these
// client checks, authorize blocks and protect chat and signaling operations.
import { collection, deleteDoc, doc, onSnapshot, serverTimestamp, writeBatch } from 'firebase/firestore';
import { validPeerUid, ChatInputError } from './chat-utils.js';

function validate(uid, peerUid) {
  if (typeof uid !== 'string' || !uid || uid.includes('/') || !validPeerUid(peerUid, uid)) throw new ChatInputError('invalid-peer');
}

// Snapshot metadata is useful for rendering but is not proof that a particular
// write completed. Callers must await the write promise before claiming success.
export function watchOwnBlocks(db, uid, next, fail) {
  if (typeof uid !== 'string' || !uid || uid.includes('/') || typeof next !== 'function' || typeof fail !== 'function') throw new Error('Invalid block subscription.');
  return onSnapshot(collection(db, 'userBlocks', uid, 'blocked'), {includeMetadataChanges:true}, snapshot => {
    next({ids:snapshot.docs.map(item=>item.id), fromCache:snapshot.metadata.fromCache, pendingWrites:snapshot.metadata.hasPendingWrites});
  }, fail);
}

// The block and selected conversation's closure commit atomically. If the chat
// has concurrently changed or access is lost, neither change is acknowledged.
// Other duplicate conversations are not automatically closed. In-flight writes,
// offline peers, and pre-existing P2P calls on other devices still need care.
export function blockAndClose(db, uid, peerUid, conversation) {
  validate(uid, peerUid);
  if (!conversation || typeof conversation.id !== 'string' || !conversation.id || conversation.id.includes('/') ||
      !Array.isArray(conversation.participants) || conversation.participants.length !== 2 ||
      !conversation.participants.includes(uid) || !conversation.participants.includes(peerUid) ||
      !['pending','active','closed'].includes(conversation.status) || conversation.pendingWrites) throw new ChatInputError('invalid-request');
  const batch = writeBatch(db);
  batch.set(doc(db, 'userBlocks', uid, 'blocked', peerUid), {createdAt:serverTimestamp()});
  if (conversation.status !== 'closed') batch.update(doc(db, 'conversations', conversation.id), {status:'closed',updatedAt:serverTimestamp()});
  return batch.commit();
}

// Unblocking does not reopen the selected closed conversation. Other existing
// active conversations can become usable again; callers must warn explicitly.
export function unblock(db, uid, peerUid) {
  validate(uid, peerUid);
  return deleteDoc(doc(db, 'userBlocks', uid, 'blocked', peerUid));
}
