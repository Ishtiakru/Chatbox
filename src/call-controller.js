// Experimental controller for a single selected direct conversation. The browser
// lab is default-off. The injected transport must enforce authorization;
// this controller is not a replacement for Firestore rules, abuse controls,
// background notifications, or a reliable TURN service.
import { readLocalMedia, setLocalMediaEnabled } from './call-media.js';

export const CALL_SETUP_TIMEOUT_MS = 60_000;
export const CALL_DISCONNECT_TIMEOUT_MS = 20_000;

// Explain common device errors without exposing raw browser or backend details.
function callErrorMessage(error) {
  switch (error?.name) {
    case 'NotAllowedError':
    case 'PermissionDeniedError':
      return 'Camera or microphone permission was denied. Check your device and app permissions, then try again.';
    case 'NotFoundError':
    case 'DevicesNotFoundError':
      return 'A required camera or microphone was not found. Check your device and try again.';
    case 'NotReadableError':
    case 'TrackStartError':
      return 'The camera or microphone is unavailable. Close other apps using it and try again.';
    default:
      return 'Call could not be completed.';
  }
}

export function createCallController({ watchIncoming, createSession, onChange = () => {}, setTimer = setTimeout, clearTimer = clearTimeout }) {
  if (typeof watchIncoming !== 'function' || typeof createSession !== 'function' || typeof onChange !== 'function' || typeof setTimer !== 'function' || typeof clearTimer !== 'function') throw new Error('Call dependencies are required.');
  let context = null, stopWatching = null, active = null, generation = 0, disposed = false, discoveryFailed = false;
  let timer = null, timerGeneration = 0, timerKind = null, stopTrackWatch = null;
  let state = {status:'idle', incoming:[], callId:null, media:null, error:'', localStream:null, remoteStream:null, localMedia:readLocalMedia(null)};
  const snapshot = () => ({...state, localMedia:{audio:{...state.localMedia.audio},video:{...state.localMedia.video}}, incoming:state.incoming.map(call => ({...call}))});
  const update = patch => { state = {...state, ...patch}; onChange(snapshot()); };
  const clearActive = () => ({status:'idle',callId:null,media:null,localStream:null,remoteStream:null,localMedia:readLocalMedia(null)});
  function clearTrackWatch() { const stop = stopTrackWatch; stopTrackWatch = null; stop?.(); }
  function clearDeadline() {
    timerGeneration++;
    if (timer !== null) clearTimer(timer);
    timer = null; timerKind = null;
  }
  function deadline(kind, delay, current, session) {
    clearDeadline();
    const token = timerGeneration;
    timerKind = kind;
    timer = setTimer(() => {
      if (disposed || token !== timerGeneration || current !== generation || active !== session) return;
      endActive();
      update({...clearActive(), error:kind === 'disconnect' ? 'The connection was lost. Call ended.' : 'The call did not connect in time. Call ended.'});
    }, delay);
  }
  function endActive() {
    clearDeadline(); clearTrackWatch();
    const old = active; active = null; generation++;
    if (!old) return;
    // hangUp stops local media synchronously before attempting the network write.
    // Do not wait for an offline signaling write when changing accounts/threads.
    try { Promise.resolve(old.hangUp()).catch(() => {}); } catch { try { old.close(); } catch {} }
  }
  function setConversation(value) {
    if (disposed) throw new Error('Controller is disposed.');
    if (value && (!value.id || !value.uid || !value.peerUid || value.uid === value.peerUid)) throw new Error('Invalid conversation.');
    // An unsubscribe failure must not leave the previous call's media running.
    try { stopWatching?.(); } finally { stopWatching = null; endActive(); context = null; }
    context = value ? {...value} : null; discoveryFailed = false;
    update({...clearActive(), incoming:[],error:''});
    if (!context) return;
    const selected = context;
    stopWatching = watchIncoming(selected, calls => {
      if (disposed || context !== selected) return;
      // Never trust display data to identify the peer: it must match the
      // selected conversation, and acceptance still needs server-side checks.
      const valid = calls.filter(call => call.callerId === selected.peerUid && call.receiverId === selected.uid && ['audio','video'].includes(call.media) && typeof call.id === 'string' && call.id);
      update({incoming:valid});
    }, () => {
      if (disposed || context !== selected) return;
      // A terminal discovery-listener error can indicate revoked access.
      // Stop active media as well; do not assume another listener will fail.
      discoveryFailed = true;
      endActive();
      update({...clearActive(), status:'unavailable', incoming:[], error:'Unable to check incoming calls. Reopen this conversation to retry.'});
    });
  }
  function begin(role, media, callId) {
    if (disposed || !context || active || discoveryFailed || !['audio','video'].includes(media)) throw new Error('Cannot start this call.');
    const current = ++generation, selected = context;
    const callbacks = {
      onState: value => {
        if (disposed || current !== generation || selected !== context) return;
        // The transport can report an error immediately after its closed
        // notification. Keep the generation until another operation starts so
        // that error is not accidentally hidden.
        if (value === 'closed') { clearDeadline(); clearTrackWatch(); active = null; update(clearActive()); }
        else {
          if (value === 'connected') clearDeadline();
          else if (value === 'disconnected' && active && timerKind !== 'disconnect') deadline('disconnect', CALL_DISCONNECT_TIMEOUT_MS, current, active);
          update({status:value});
        }
      },
      onIncoming: () => { if (!disposed && active && current === generation && context === selected) update({status:'incoming'}); },
      onLocalStream: stream => {
        if (disposed || !active || current !== generation || context !== selected) return;
        clearTrackWatch();
        const tracks = typeof stream?.getTracks === 'function' ? stream.getTracks() : [];
        const changed = () => {
          if (!disposed && active && current === generation && context === selected && state.localStream === stream) update({localMedia:readLocalMedia(stream)});
        };
        for (const track of tracks) { try { track.addEventListener?.('ended', changed); } catch {} }
        stopTrackWatch = () => { for (const track of tracks) { try { track.removeEventListener?.('ended', changed); } catch {} } };
        update({localStream:stream, localMedia:readLocalMedia(stream)});
      },
      onRemoteStream: stream => { if (!disposed && active && current === generation && context === selected) update({remoteStream:stream}); },
      onError: error => {
        if (disposed || current !== generation || context !== selected) return;
        clearDeadline(); clearTrackWatch(); active = null; generation++;
        update({...clearActive(),error:callErrorMessage(error)});
      },
    };
    const session = createSession({...selected, role, media, callId, ...callbacks});
    active = session;
    // Bound unanswered calls and stalled permission or connection setup. A late
    // media grant is stopped by PeerCall after hangUp. Connected calls have no
    // fixed duration; a later disconnect has its own shorter grace period.
    deadline('setup', CALL_SETUP_TIMEOUT_MS, current, session);
    update({status:role === 'caller' ? 'preparing' : 'loading', callId:session.id, media, error:'',localStream:null,remoteStream:null,localMedia:readLocalMedia(null)});
    return {session, current};
  }
  async function start(media) {
    const {session, current} = begin('caller', media);
    try { await session.start(); }
    catch (error) {
      if (!disposed && current === generation) {
        endActive(); update({...clearActive(),error:callErrorMessage(error)});
      }
    }
  }
  function openIncoming(id) {
    const offer = state.incoming.find(call => call.id === id);
    if (!offer) throw new Error('Incoming call is no longer available.');
    const {session} = begin('receiver', offer.media, id);
    try { session.listen(); }
    catch { endActive(); update({...clearActive(),error:'Call could not be completed.'}); }
  }
  async function accept() {
    if (!active || state.status !== 'incoming') throw new Error('No call is awaiting acceptance.');
    const session = active, current = generation;
    // Set busy before awaiting: double taps must not open media twice.
    update({status:'preparing'});
    try { await session.accept(); }
    catch (error) {
      if (!disposed && current === generation) {
        endActive(); update({...clearActive(),error:callErrorMessage(error)});
      }
    }
  }
  function setLocalEnabled(kind, enabled) {
    if (disposed || !active || !state.localStream) return;
    const stream = state.localStream, current = generation, session = active;
    let failed = false;
    try { setLocalMediaEnabled(stream,kind,enabled); } catch { failed = true; }
    if (disposed || current !== generation || active !== session || state.localStream !== stream) return;
    update({localMedia:readLocalMedia(stream), error:failed ? 'Could not change the media track. Check your device.' : ''});
  }
  function hangUp() {
    if (!disposed) {
      endActive();
      update(discoveryFailed
        ? {...clearActive(),status:'unavailable',incoming:[],error:'Unable to check incoming calls. Reopen this conversation to retry.'}
        : {...clearActive(),error:''});
    }
  }
  function dispose() {
    if (disposed) return;
    disposed = true;
    try { stopWatching?.(); } finally { stopWatching = null; context = null; endActive(); }
  }
  return {setConversation,start,openIncoming,accept,setLocalEnabled,hangUp,dispose,getState:snapshot};
}
