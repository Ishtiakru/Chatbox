// Experimental call orchestration. The transport MUST authenticate and authorize
// both participants, bind all messages to a unique call ID, and expire sessions.
// The default-off browser lab uses a candidate transport; it is not production-ready.
import { MAX_CANDIDATES, PeerCall, validateDescription } from './call-core.js';

export class CallSession {
  constructor({ role, send, ...dependencies }) {
    if (!['caller', 'receiver'].includes(role) || typeof send !== 'function') throw new Error('Invalid call session.');
    this.role = role;
    this.send = send;
    this.closed = false;
    this.descriptionSent = false;
    this.queuedCandidates = [];
    this.offered = null;
    this.starting = false;
    this.outgoing = Promise.resolve();
    this.incoming = Promise.resolve();
    this.call = new PeerCall({ ...dependencies, onSignal: signal => this.sendSignal(signal), onError: error => {
      try { dependencies.onError?.(error); } finally { this.end(); }
    } });
  }
  // ICE can arrive before setLocalDescription has completed. Never send it
  // before the offer/answer, even if the network transport is asynchronous.
  sendSignal(signal) {
    if (this.closed) return Promise.resolve();
    if (signal.type === 'candidate' && !this.descriptionSent) {
      if (this.queuedCandidates.length >= MAX_CANDIDATES) return Promise.reject(new Error('Too many local ICE candidates.'));
      this.queuedCandidates.push(signal);
      return Promise.resolve();
    }
    if (signal.type === 'offer' || signal.type === 'answer') {
      this.descriptionSent = true;
      const signals = [signal, ...this.queuedCandidates.splice(0)];
      return Promise.all(signals.map(item => this.enqueueSend(item))).then(() => {});
    }
    return this.enqueueSend(signal);
  }
  enqueueSend(signal) {
    const next = this.outgoing.then(() => { if (!this.closed) return this.send(signal); });
    this.outgoing = next;
    return next;
  }
  async start() {
    if (this.role !== 'caller' || this.starting || this.closed) throw new Error('Cannot start this call.');
    this.starting = true;
    try { await this.call.createOffer(); } catch (error) { this.end(); throw error; }
  }
  receive(signal) {
    // Serialize asynchronous remote descriptions and candidates to prevent races.
    const next = this.incoming.then(() => this.handleSignal(signal));
    this.incoming = next.catch(() => {});
    return next;
  }
  async handleSignal(signal) {
    if (this.closed) throw new Error('Call has ended.');
    if (!signal || typeof signal !== 'object') throw new Error('Invalid call signal.');
    if (signal.type === 'offer') {
      if (this.role !== 'receiver' || this.offered || this.starting) throw new Error('Unexpected offer.');
      this.offered = validateDescription(signal.description, 'offer');
    } else if (signal.type === 'answer') {
      if (this.role !== 'caller') throw new Error('Unexpected answer.');
      await this.call.receiveAnswer(signal.description);
    } else if (signal.type === 'candidate') {
      await this.call.receiveCandidate(signal.candidate);
    } else if (signal.type === 'end') {
      this.end();
    } else throw new Error('Invalid call signal.');
  }
  async accept() {
    if (this.role !== 'receiver' || !this.offered || this.starting || this.closed) throw new Error('No incoming offer to accept.');
    this.starting = true;
    try { await this.call.acceptOffer(this.offered); } catch (error) { this.end(); throw error; }
  }
  async hangUp() {
    if (this.closed) return;
    // Stop local media immediately; end notification is best-effort and should
    // not wait for an earlier failed or stalled signaling write.
    this.end();
    await this.send({ type: 'end' });
  }
  end() {
    if (this.closed) return;
    this.closed = true;
    this.queuedCandidates = [];
    this.call.end();
  }
}
