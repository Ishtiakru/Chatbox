import {collection,doc,serverTimestamp,writeBatch} from 'firebase/firestore';
import {ChatInputError,validPeerUid} from './chat-utils.js';

// The caller must use only the matching undeployed rules candidate. Atomic
// commit can remain pending offline; its outcome must not be assumed on timeout.
export async function createThrottledRequest(db,uid,peerUid) {
  if (!db || typeof uid !== 'string' || !uid || uid.length>128 || uid.includes('/') || !validPeerUid(peerUid,uid)) throw new ChatInputError('invalid-peer');
  const conversation=doc(collection(db,'conversations'));
  const throttle=doc(db,'requestThrottle',uid);
  const batch=writeBatch(db);
  batch.set(conversation,{participants:[uid,peerUid],createdBy:uid,status:'pending',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
  batch.set(throttle,{conversationId:conversation.id,lastRequestedAt:serverTimestamp()});
  await batch.commit();
  return conversation;
}
