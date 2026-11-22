// Experimental only: the default-off browser lab uses this transport. Firebase
// authentication and participant-only rules are essential; uid values supplied
// here are never an authorization boundary. Not production-ready.
import { collection, doc, setDoc, addDoc, updateDoc, serverTimestamp, onSnapshot, query, orderBy, limit } from 'firebase/firestore';
import { CallSession } from './call-session.js';
import { MAX_CANDIDATES } from './call-core.js';
import { createIncomingCallFilter } from './incoming-call-filter.js';

// Bounded listing is only a prototype. Account for device clock skew, spam,
// pagination and notification delivery before relying on it for production.
export function watchIncomingCalls(db, conversationId, uid, onCalls, onError, subscribe = onSnapshot) {
  const filter = createIncomingCallFilter({ uid, onCalls });
  const q = query(collection(db, 'conversations', conversationId, 'calls'), orderBy('createdAt', 'desc'), limit(20));
  // Cache-only results may contain a call the other person has already ended.
  // Metadata changes are necessary to remove displayed calls on cache fallback.
  const unsubscribe = subscribe(q, {includeMetadataChanges:true}, snapshot => filter.update(snapshot.metadata.fromCache ? [] : snapshot.docs), error => {
    filter.stop();
    onError?.(error);
  });
  return () => { filter.stop(); unsubscribe(); };
}

export function createFirestoreCall({ db, conversationId, uid, peerUid, role, callId, media, mediaDevices, peerFactory, iceServers = [], onIncoming = () => {}, onState = () => {}, onError = () => {}, onLocalStream = () => {}, onRemoteStream = () => {}, subscribe = onSnapshot, update = updateDoc }) {
  if (!db || !conversationId || !uid || !peerUid || uid === peerUid || !['caller', 'receiver'].includes(role) || (role === 'receiver' && !callId)) throw new Error('Invalid call configuration.');
  const calls = collection(db, 'conversations', conversationId, 'calls');
  const ref = callId ? doc(calls, callId) : doc(calls);
  const ownCandidates = role === 'caller' ? 'callerCandidates' : 'receiverCandidates';
  const otherCandidates = role === 'caller' ? 'receiverCandidates' : 'callerCandidates';
  let stopDoc = () => {}, stopCandidates = () => {}, closed = false, listening = false, answerReceived = false, offerReceived = false, sawServerDoc = false, sawServerCandidates = false;
  const seenCandidates = new Set();
  const detach = () => { if (!closed) { closed = true; try { stopDoc(); } finally { stopCandidates(); } } };
  const fail = error => { if (closed) return; try { session.end(); } finally { detach(); } onError(error); };
  const send = async signal => {
    if (signal.type === 'offer') {
      if (role !== 'caller') throw new Error('Unexpected offer.');
      await setDoc(ref, {callerId:uid,receiverId:peerUid,media,offer:signal.description,status:'ringing',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
    } else if (signal.type === 'answer') {
      if (role !== 'receiver') throw new Error('Unexpected answer.');
      await update(ref, {status:'accepted',answer:signal.description,updatedAt:serverTimestamp()});
    } else if (signal.type === 'candidate') {
      await addDoc(collection(ref, ownCandidates), {...signal.candidate,createdAt:serverTimestamp()});
    } else if (signal.type === 'end') {
      await update(ref, {status:'ended',updatedAt:serverTimestamp()});
    } else throw new Error('Invalid signal.');
  };
  const session = new CallSession({role,media,mediaDevices,peerFactory,iceServers,send,onLocalStream,onRemoteStream,onState,onError:fail});
  const handleSignal = signal => { session.receive(signal).catch(fail); };
  const listen = () => {
    if (listening) throw new Error('Already listening for this call.');
    listening = true;
    let docListener;
    try { docListener = subscribe(ref, {includeMetadataChanges:true}, snapshot => {
      if (closed) return;
      // Never prompt for a cached offer or process a cached answer. Once a
      // server snapshot was seen, cache fallback ends the local call rather
      // than continuing media against known-stale signaling. This is not a
      // reliable connectivity monitor: loss can precede the metadata event.
      if (snapshot.metadata.fromCache) {
        if (sawServerDoc) fail(new Error('Call signaling is no longer server-confirmed.'));
        return;
      }
      if (snapshot.metadata.hasPendingWrites) return;
      if (!snapshot.exists()) return fail(new Error('Call is unavailable.'));
      sawServerDoc = true;
      const value = snapshot.data();
      const caller = role === 'caller' ? uid : peerUid;
      const receiver = role === 'caller' ? peerUid : uid;
      if (value.callerId !== caller || value.receiverId !== receiver || value.media !== media) return fail(new Error('Call identity mismatch.'));
      if (value.status === 'ended' || (role === 'receiver' && value.status === 'accepted' && !session.starting)) { try { session.end(); } finally { detach(); } return; }
      if (role === 'receiver' && value.status === 'ringing' && !offerReceived) {
        offerReceived = true;
        session.receive({type:'offer',description:value.offer}).then(() => { if (!closed) onIncoming(); }).catch(fail);
      }
      if (role === 'caller' && value.status === 'accepted' && !answerReceived) {
        answerReceived = true; handleSignal({type:'answer',description:value.answer});
      }
    }, fail); } catch (error) { fail(error); throw error; }
    // A synchronous listener callback can close the call before subscribe
    // returns. Do not leak the returned listener or attach another one.
    if (closed) { docListener(); return; }
    stopDoc = docListener;
    let candidateListener;
    // Bound the read as well as the PeerCall candidate queue. The extra item
    // lets us fail closed on overflow rather than silently losing candidates.
    const candidates = query(collection(ref, otherCandidates), limit(MAX_CANDIDATES + 1));
    try { candidateListener = subscribe(candidates, {includeMetadataChanges:true}, snapshot => {
      if (closed) return;
      // A metadata-only transition can confirm cached documents without any
      // added changes. Read the full bounded snapshot and deduplicate by ID.
      if (snapshot.metadata.fromCache) {
        if (sawServerCandidates) fail(new Error('Call candidates are no longer server-confirmed.'));
        return;
      }
      sawServerCandidates = true;
      if (snapshot.docs.length > MAX_CANDIDATES) return fail(new Error('Too many remote ICE candidates.'));
      for (const candidate of snapshot.docs) {
        if (candidate.metadata.hasPendingWrites || seenCandidates.has(candidate.id)) continue;
        if (seenCandidates.size >= MAX_CANDIDATES) return fail(new Error('Too many remote ICE candidates.'));
        seenCandidates.add(candidate.id);
        const data = candidate.data();
        handleSignal({type:'candidate',candidate:{candidate:data.candidate,sdpMid:data.sdpMid,sdpMLineIndex:data.sdpMLineIndex}});
      }
    }, fail); } catch (error) { fail(error); throw error; }
    if (closed) { candidateListener(); return; }
    stopCandidates = candidateListener;
  };
  return {
    id: ref.id,
    async start() {
      if (role !== 'caller' || closed) throw new Error('Cannot start this call.');
      try { await session.start(); if (!closed) listen(); } catch (error) { fail(error); throw error; }
    },
    listen() { if (role !== 'receiver' || closed || offerReceived) throw new Error('Cannot listen for this call.'); listen(); },
    async accept() { if (closed) throw new Error('Call has ended.'); return session.accept(); },
    async hangUp() {
      if (closed) return;
      // Firestore writes can remain pending indefinitely while offline. Release
      // listeners immediately, not after the best-effort end write completes.
      let pending;
      try { pending = session.hangUp(); } finally { detach(); }
      await pending;
    },
    close() { try { session.end(); } finally { detach(); } },
  };
}
