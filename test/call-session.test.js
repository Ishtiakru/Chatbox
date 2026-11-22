import test from 'node:test';
import assert from 'node:assert/strict';
import { CallSession } from '../src/call-session.js';

function setup(role, send = async () => {}) {
  let permissionRequests = 0;
  const track = { stopped: false, stop() { this.stopped = true; } };
  const peer = {
    addTrack() {}, async createOffer() { return { type: 'offer', sdp: 'offer' }; },
    async createAnswer() { return { type: 'answer', sdp: 'answer' }; },
    async setLocalDescription() { this.onicecandidate?.({ candidate: { toJSON: () => ({candidate:'ice',sdpMid:'0',sdpMLineIndex:0}) } }); },
    async setRemoteDescription() {}, async addIceCandidate() {}, close() {},
  };
  const session = new CallSession({ role, media: 'audio', mediaDevices: { async getUserMedia() { permissionRequests++; return {getTracks: () => [track]}; } }, peerFactory: () => peer, send });
  return {session, track, getPermissionRequests: () => permissionRequests};
}

test('sends offer before early ICE candidates even when transport is asynchronous', async () => {
  const sent = [];
  const {session} = setup('caller', async signal => { await Promise.resolve(); sent.push(signal.type); });
  await session.start();
  assert.deepEqual(sent, ['offer', 'candidate']);
  session.end();
});
test('incoming call never opens microphone before explicit acceptance', async () => {
  const sent = [];
  const {session, track, getPermissionRequests} = setup('receiver', async signal => sent.push(signal.type));
  await session.receive({type:'offer',description:{type:'offer',sdp:'offer'}});
  await session.receive({type:'candidate',candidate:{candidate:'ice',sdpMid:'0',sdpMLineIndex:0}});
  assert.equal(getPermissionRequests(), 0);
  await session.accept();
  assert.equal(getPermissionRequests(), 1);
  assert.deepEqual(sent, ['answer', 'candidate']);
  session.end(); assert.equal(track.stopped, true);
});
test('rejects replayed offers and late signals; ending stops tracks', async () => {
  const sent = [];
  const {session, track} = setup('receiver', async signal => sent.push(signal.type));
  const offer = {type:'offer',description:{type:'offer',sdp:'offer'}};
  await session.receive(offer);
  await assert.rejects(session.receive(offer), /Unexpected offer/);
  await session.accept();
  await session.hangUp();
  assert.equal(track.stopped, true);
  assert.equal(sent.at(-1), 'end');
  await assert.rejects(session.receive({type:'end'}), /ended/);
});
test('send failures release microphone', async () => {
  const {session, track} = setup('caller', async () => { throw new Error('network down'); });
  await assert.rejects(session.start(), /network down/);
  assert.equal(track.stopped, true);
  assert.equal(session.closed, true);
});
