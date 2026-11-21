import {createPeerProfileController} from './peer-profile-controller.js';
import {validPeerUid} from './chat-utils.js';

// Bounded direct document listeners for peers already in the conversation list.
// Firebase rules remain the authorization boundary; names are unverified and
// cache-only snapshots are never promoted to confirmed names.
export function createConversationProfiles({subscribe, onChange, limit = 30}) {
  if (typeof subscribe !== 'function' || typeof onChange !== 'function' || !Number.isInteger(limit) || limit < 1) throw new Error('Profile dependencies are required.');
  const controllers = new Map();
  let disposed = false;
  function sync(conversations, ownUid) {
    if (disposed) return;
    const ids = [];
    const seen = new Set();
    for (const conversation of conversations) {
      const participants = conversation?.participants;
      if (!Array.isArray(participants) || participants.length !== 2 || !participants.includes(ownUid)) continue;
      const peer = participants.find(uid => uid !== ownUid);
      if (validPeerUid(peer, ownUid) && !seen.has(peer)) { seen.add(peer); ids.push(peer); }
      if (ids.length >= limit) break;
    }
    const wanted = new Set(ids);
    for (const [uid, controller] of controllers) {
      if (!wanted.has(uid)) { controllers.delete(uid); controller.dispose(); onChange(uid, null); }
    }
    for (const uid of ids) {
      if (disposed) return;
      if (controllers.has(uid)) continue;
      const controller = createPeerProfileController({subscribe, onChange: profile => {
        if (!disposed && controllers.get(uid) === controller) onChange(uid, profile.status === 'ready' ? profile : null);
      }});
      controllers.set(uid, controller);
      try { controller.select(uid); } catch { controllers.delete(uid); controller.dispose(); onChange(uid, null); }
    }
  }
  function dispose() {
    if (disposed) return;
    disposed = true;
    for (const controller of controllers.values()) { try { controller.dispose(); } catch { /* Close the rest. */ } }
    controllers.clear();
  }
  return {sync, dispose};
}
