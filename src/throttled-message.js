import { collection, doc, serverTimestamp, writeBatch } from 'firebase/firestore';
import { normalizeMessage } from './chat-utils.js';

// Only for the matching, separately reviewed experimental rules candidate.
// Firestore commits may remain pending offline; do not treat this as delivered
// until the promise resolves, and never blindly retry an unknown outcome.
export async function sendThrottledMessage(db, conversationId, uid, body) {
  if (!db || typeof uid !== 'string' || !uid || uid.length > 128 || uid.includes('/') || typeof conversationId !== 'string' || !conversationId || conversationId.length > 128 || conversationId.includes('/')) throw new Error('Invalid message target.');
  const text=normalizeMessage(body);
  const ref=doc(collection(db,'conversations',conversationId,'messages'));
  const throttle=doc(db,'messageThrottle',uid);
  const batch=writeBatch(db);
  batch.set(ref,{senderId:uid,body:text,sentAt:serverTimestamp()});
  batch.set(throttle,{conversationId,messageId:ref.id,lastSentAt:serverTimestamp()});
  await batch.commit();
  return ref;
}
