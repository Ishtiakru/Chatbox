import {otherParticipant} from './chat-utils.js';

// Search only the conversations already present in this account's listener.
// It must not query Firestore, persist the query, or turn names into identity.
export function visiblePeerName(uid, profile) {
  return profile?.uid === uid && profile.status === 'ready' && typeof profile.name === 'string' && profile.name
    ? profile.name : uid || 'Chatbox user';
}

export function matchesLiveConversation(conversation, ownUid, search, selected, selectedProfile, profiles) {
  const query = typeof search === 'string' ? search.trim().toLocaleLowerCase() : '';
  if (!query) return true;
  const peer = otherParticipant(conversation?.participants, ownUid);
  if (typeof peer !== 'string' || !peer) return false;
  const listed = profiles instanceof Map ? profiles.get(peer) : null;
  const profile = conversation?.id === selected && selectedProfile?.uid === peer && selectedProfile.status === 'ready'
    ? selectedProfile : listed;
  const name = visiblePeerName(peer, profile);
  return peer.toLocaleLowerCase().includes(query) || name.toLocaleLowerCase().includes(query);
}
