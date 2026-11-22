import { ChatInputError, validPeerUid } from './chat-utils.js';

// Never use a cached profile to conclude that a recipient does or does not
// exist. A server read and a session check precede the separate request write.
// These checks do not authorize the write and cannot cancel an in-flight one.
export async function createLiveRequest({uid, peerUid, guard, readPeer, writeRequest}) {
  if (typeof uid !== 'string' || !uid || uid.length > 128 || uid.includes('/') || typeof peerUid !== 'string') throw new ChatInputError('invalid-peer');
  const peer = peerUid.trim();
  if (!validPeerUid(peer, uid)) throw new ChatInputError('invalid-peer');
  if (typeof guard?.run !== 'function' || typeof readPeer !== 'function' || typeof writeRequest !== 'function') throw new Error('Request dependencies are required.');
  const exists = await guard.run(() => readPeer(peer));
  return guard.run(() => {
    if (!exists) throw new ChatInputError('profile-not-found');
    return writeRequest(uid, peer);
  });
}
