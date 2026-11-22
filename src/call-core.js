// Experimental WebRTC transport. The browser-only UI is default-off and is
// not production-ready; authenticated signaling and receiver consent remain essential.
const MAX_SDP = 65536;
export const MAX_CANDIDATES = 128;

function stopTracks(stream) {
  for (const track of stream?.getTracks() ?? []) {
    try { track.stop(); } catch { /* Continue releasing other tracks. */ }
  }
}

export function validateDescription(value, type) {
  if (!value || value.type !== type || typeof value.sdp !== 'string' || !value.sdp || value.sdp.length > MAX_SDP) throw new Error('Invalid session description.');
  return { type, sdp: value.sdp };
}
export function validateCandidate(value) {
  if (!value || typeof value.candidate !== 'string' || !value.candidate || value.candidate.length > 4096) throw new Error('Invalid ICE candidate.');
  if (value.sdpMid != null && (typeof value.sdpMid !== 'string' || value.sdpMid.length > 256)) throw new Error('Invalid candidate media ID.');
  if (value.sdpMLineIndex != null && (!Number.isInteger(value.sdpMLineIndex) || value.sdpMLineIndex < 0 || value.sdpMLineIndex > 65535)) throw new Error('Invalid candidate media index.');
  return { candidate: value.candidate, sdpMid: value.sdpMid ?? null, sdpMLineIndex: value.sdpMLineIndex ?? null };
}

export class PeerCall {
  constructor({ media, iceServers = [], mediaDevices, peerFactory, onSignal, onLocalStream = () => {}, onRemoteStream = () => {}, onState = () => {}, onError = () => {} }) {
    if (media !== 'audio' && media !== 'video') throw new Error('Choose audio or video.');
    if (!Array.isArray(iceServers) || !mediaDevices?.getUserMedia || typeof peerFactory !== 'function' || typeof onSignal !== 'function') throw new Error('Call dependencies are required.');
    Object.assign(this, { media, iceServers, mediaDevices, peerFactory, onSignal, onLocalStream, onRemoteStream, onState, onError });
    this.state = 'new'; this.peer = null; this.localStream = null; this.pending = []; this.remoteDescriptionReady = false; this.candidatesSeen = 0;
  }
  setState(value) { this.state = value; this.onState(value); }
  ensureOpen() { if (this.state === 'closed') throw new Error('Call ended.'); }
  async open() {
    if (this.state !== 'new') throw new Error('Call already started.');
    try {
      this.setState('preparing');
      // An observer may synchronously cancel while handling the state change.
      // Never ask for media permission after the user has ended the call.
      this.ensureOpen();
      const stream = await this.mediaDevices.getUserMedia({ audio: true, video: this.media === 'video' });
      if (this.state === 'closed') { stopTracks(stream); throw new Error('Call ended.'); }
      this.localStream = stream;
      const peer = this.peerFactory({ iceServers: this.iceServers }); this.peer = peer;
      for (const track of stream.getTracks()) peer.addTrack(track, stream);
      peer.ontrack = event => { if (this.state !== 'closed' && event.streams?.[0]) this.onRemoteStream(event.streams[0]); };
      peer.onicecandidate = event => {
        if (!event.candidate || this.state === 'closed') return;
        try {
          const candidate = validateCandidate(event.candidate.toJSON());
          Promise.resolve(this.onSignal({ type: 'candidate', candidate })).catch(error => this.fail(error));
        } catch (error) { this.fail(error); }
      };
      peer.onconnectionstatechange = () => {
        if (this.state === 'closed') return;
        if (peer.connectionState === 'connected') this.setState('connected');
        else if (peer.connectionState === 'disconnected' && ['connecting','connected','disconnected'].includes(this.state)) this.setState('disconnected');
        else if (peer.connectionState === 'failed') this.fail(new Error('Peer connection failed.'));
        else if (peer.connectionState === 'closed') this.end();
      };
      this.onLocalStream(stream);
      this.ensureOpen();
      return peer;
    } catch (error) { this.end(); throw error; }
  }
  async createOffer() {
    try {
      const peer = await this.open();
      this.ensureOpen();
      const description = validateDescription(await peer.createOffer(), 'offer');
      this.ensureOpen();
      await peer.setLocalDescription(description);
      this.ensureOpen();
      this.setState('calling');
      this.ensureOpen();
      await this.onSignal({ type: 'offer', description });
    } catch (error) { this.end(); throw error; }
  }
  async acceptOffer(offer) {
    const description = validateDescription(offer, 'offer');
    try {
      const peer = await this.open();
      this.ensureOpen();
      await peer.setRemoteDescription(description);
      this.ensureOpen();
      this.remoteDescriptionReady = true; await this.flushCandidates();
      this.ensureOpen();
      const answer = validateDescription(await peer.createAnswer(), 'answer');
      this.ensureOpen();
      await peer.setLocalDescription(answer);
      this.ensureOpen();
      // A very fast peer may already have fired the connected event while
      // descriptions were being applied. Do not regress that state.
      if (this.state !== 'connected') this.setState('connecting');
      this.ensureOpen();
      await this.onSignal({ type: 'answer', description: answer });
    } catch (error) { this.end(); throw error; }
  }
  async receiveAnswer(answer) {
    if (this.state !== 'calling' || !this.peer) throw new Error('No outgoing call is awaiting an answer.');
    try {
      await this.peer.setRemoteDescription(validateDescription(answer, 'answer'));
      this.ensureOpen();
      this.remoteDescriptionReady = true; await this.flushCandidates();
      this.ensureOpen();
      if (this.state !== 'connected') this.setState('connecting');
    } catch (error) { this.end(); throw error; }
  }
  async receiveCandidate(value) {
    if (this.state === 'closed') throw new Error('Call has ended.');
    const candidate = validateCandidate(value);
    if (++this.candidatesSeen > MAX_CANDIDATES) throw new Error('Too many ICE candidates.');
    if (!this.peer || !this.remoteDescriptionReady) { this.pending.push(candidate); return; }
    await this.peer.addIceCandidate(candidate);
  }
  async flushCandidates() {
    while (this.pending.length && this.peer && this.state !== 'closed') await this.peer.addIceCandidate(this.pending.shift());
  }
  fail(error) { if (this.state !== 'closed') { try { this.onError(error); } finally { this.end(); } } }
  end() {
    if (this.state === 'closed') return;
    // Release media before notifying UI observers. A throwing observer must not
    // leave the camera or microphone active, and teardown must be idempotent.
    this.state = 'closed';
    const peer = this.peer, stream = this.localStream;
    this.peer = null; this.localStream = null; this.pending = [];
    try {
      if (peer) {
        peer.ontrack = null; peer.onicecandidate = null; peer.onconnectionstatechange = null;
        peer.close();
      }
    } finally {
      try {
        // A broken track should not prevent the other tracks being stopped.
        stopTracks(stream);
      } finally {
        this.onState('closed');
      }
    }
  }
}
