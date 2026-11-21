// Experimental only: the default-off browser lab uses this transport. Firebase
// authentication and participant-only rules are essential; uid values supplied
// here are never an authorization boundary. Not production-ready.
import { collection, doc, setDoc, addDoc, updateDoc, serverTimestamp, onSnapshot, query, orderBy, limit } from 'firebase/firestore';
import { CallSession } from './call-session.js';
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
  let stopDoc = () => {}, stopCandidates = () => {}, closed = false, listening = false, answerReceived = false, offerReceived = false, sawServerDoc = false;
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
    stopDoc = subscribe(ref, {includeMetadataChanges:true}, snapshot => {
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
    }, fail);
    stopCandidates = subscribe(collection(ref, otherCandidates), snapshot => {
      if (closed) return;
      for (const change of snapshot.docChanges()) {
        if (change.type !== 'added' || change.doc.metadata.hasPendingWrites) continue;
        const data = change.doc.data();
        handleSignal({type:'candidate',candidate:{candidate:data.candidate,sdpMid:data.sdpMid,sdpMLineIndex:data.sdpMLineIndex}});
      }
    }, fail);
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
