import test from 'node:test';
import assert from 'node:assert/strict';
import { PeerCall, validateCandidate, validateDescription } from '../src/call-core.js';

function fixture() {
  const track = { stopped: false, stop() { this.stopped = true; } };
  const stream = { getTracks: () => [track] };
  const peer = { candidates: [], closed: false, addTrack() {}, async createOffer(){return {type:'offer',sdp:'offer'};}, async createAnswer(){return {type:'answer',sdp:'answer'};}, async setLocalDescription(d){this.localDescription=d;}, async setRemoteDescription(d){this.remoteDescription=d;}, async addIceCandidate(c){this.candidates.push(c);}, close(){this.closed=true;} };
  const signals = [];
  const call = new PeerCall({ media:'audio', iceServers:[], mediaDevices:{getUserMedia: async () => stream}, peerFactory: () => peer, onSignal: async signal => { signals.push(signal); } });
  return {call,peer,track,signals};
}
test('WebRTC caller sends offer, buffers ICE, accepts answer, and releases media', async () => {
  const {call,peer,track,signals}=fixture();
  await call.createOffer();
  assert.equal(signals[0].type,'offer');
  await call.receiveCandidate({candidate:'candidate:1',sdpMid:'0',sdpMLineIndex:0});
  assert.equal(peer.candidates.length,0);
  await call.receiveAnswer({type:'answer',sdp:'answer'});
  assert.equal(peer.candidates.length,1);
  call.end(); assert.equal(track.stopped,true); assert.equal(peer.closed,true); assert.equal(call.state,'closed');
});
test('receiver explicitly accepts an offer before answering', async () => {
  const {call,signals,track}=fixture();
  await call.receiveCandidate({candidate:'candidate:1',sdpMid:null,sdpMLineIndex:null});
  await call.acceptOffer({type:'offer',sdp:'offer'});
  assert.equal(signals[0].type,'answer');
  assert.equal(call.state,'connecting');
  call.end(); assert.equal(track.stopped,true);
});
test('invalid signaling is rejected', async () => {
  assert.throws(() => validateDescription({type:'answer',sdp:'x'},'offer'));
  assert.throws(() => validateDescription({type:'offer',sdp:'x'.repeat(65537)},'offer'));
  assert.throws(() => validateCandidate({candidate:'x',sdpMLineIndex:-1}));
  assert.throws(() => validateCandidate({candidate:''}));
  const {call}=fixture();
  await assert.rejects(call.receiveAnswer({type:'answer',sdp:'x'}));
});
test('ending a call while waiting for permission stops newly granted tracks', async () => {
  let grant;
  const track = {stopped:false,stop(){this.stopped=true;}};
  const stream = {getTracks:()=>[track]};
  const call = new PeerCall({media:'video',mediaDevices:{getUserMedia:()=>new Promise(resolve=>{grant=resolve;})},peerFactory:()=>{throw new Error('should not create peer');},onSignal:()=>{}});
  const start = call.createOffer(); call.end(); grant(stream);
  await assert.rejects(start, /Call ended/); assert.equal(track.stopped,true);
});
test('cleanup releases every track before notifying an observer even when teardown throws', async () => {
  const stopped = [];
  const tracks = [
    {stop() { stopped.push('first'); throw new Error('broken track'); }},
    {stop() { stopped.push('second'); }},
  ];
  const peer = {addTrack() {}, async createOffer() {return {type:'offer',sdp:'offer'};}, async setLocalDescription() {}, close() {throw new Error('broken peer');}};
  const call = new PeerCall({media:'video',mediaDevices:{getUserMedia:async()=>({getTracks:()=>tracks})},peerFactory:()=>peer,onSignal:async()=>{},onState:state=>{if(state==='closed') assert.deepEqual(stopped,['first','second']);}});
  await call.createOffer();
  assert.throws(() => call.end(), /broken peer/);
  assert.equal(call.state,'closed');
  assert.deepEqual(stopped,['first','second']);
  assert.doesNotThrow(() => call.end());
});
test('a failing closed-state observer cannot prevent media cleanup', async () => {
  const {call,track,peer}=fixture();
  call.onState = state => { if (state === 'closed') throw new Error('UI failed'); };
  await call.createOffer();
  assert.throws(() => call.end(), /UI failed/);
  assert.equal(track.stopped,true);
  assert.equal(peer.closed,true);
});
test('a fast connection event is not overwritten by a late connecting state', async () => {
  const {call,peer}=fixture();
  await call.createOffer();
  peer.setRemoteDescription = async () => { peer.connectionState='connected'; peer.onconnectionstatechange(); };
  await call.receiveAnswer({type:'answer',sdp:'answer'});
  assert.equal(call.state,'connected');
  call.end();
});
test('receiver retains connected state if the event arrives while applying its answer', async () => {
  const {call,peer}=fixture();
  peer.setLocalDescription = async () => { peer.connectionState='connected'; peer.onconnectionstatechange(); };
  await call.acceptOffer({type:'offer',sdp:'offer'});
  assert.equal(call.state,'connected');
  call.end();
});
test('late permission grant releases all tracks even if one stop fails', async () => {
  let grant;
  const stopped = [];
  const tracks = [
    {stop() {stopped.push('first'); throw new Error('broken track');}},
    {stop() {stopped.push('second');}},
  ];
  const call = new PeerCall({media:'video',mediaDevices:{getUserMedia:()=>new Promise(resolve=>{grant=resolve;})},peerFactory:()=>{throw new Error('should not create peer');},onSignal:()=>{}});
  const start = call.createOffer();
  call.end();
  grant({getTracks:()=>tracks});
  await assert.rejects(start, /Call ended/);
  assert.deepEqual(stopped,['first','second']);
});
test('failure observer throwing does not prevent closing the peer and media', async () => {
  const {call,peer,track}=fixture();
  await call.createOffer();
  call.onError = () => {throw new Error('observer error');};
  assert.throws(() => call.fail(new Error('connection failure')), /observer error/);
  assert.equal(call.state,'closed');
  assert.equal(peer.closed,true);
  assert.equal(track.stopped,true);
});

test('synchronous cancellation during preparing does not request media', async () => {
  let requested = false;
  let call;
  call = new PeerCall({media:'audio', mediaDevices:{getUserMedia:async()=>{requested=true;}}, peerFactory:()=>{}, onSignal:()=>{}, onState:state=>{if(state==='preparing') call.end();}});
  await assert.rejects(call.createOffer(), /Call ended/);
  assert.equal(requested, false);
  assert.equal(call.state, 'closed');
});

test('cancellation from local stream callback stops tracks before offer creation', async () => {
  const {call,peer,track,signals} = fixture();
  let offered = false;
  peer.createOffer = async () => { offered=true; return {type:'offer',sdp:'offer'}; };
  call.onLocalStream = () => call.end();
  await assert.rejects(call.createOffer(), /Call ended/);
  assert.equal(offered, false);
  assert.equal(track.stopped, true);
  assert.equal(peer.closed, true);
  assert.deepEqual(signals, []);
});

test('cancellation while creating an offer prevents later signaling', async () => {
  const {call,peer,track,signals} = fixture();
  let finish, entered;
  const started = new Promise(resolve => {entered=resolve;});
  peer.createOffer = () => {entered(); return new Promise(resolve => {finish=resolve;});};
  let localDescriptionSet = false;
  peer.setLocalDescription = async () => { localDescriptionSet=true; };
  const task = call.createOffer();
  await started;
  call.end();
  finish({type:'offer',sdp:'offer'});
  await assert.rejects(task, /Call ended/);
  assert.equal(localDescriptionSet, false);
  assert.equal(track.stopped, true);
  assert.deepEqual(signals, []);
});

test('cancellation while applying an incoming offer prevents answer creation', async () => {
  const {call,peer,track,signals} = fixture();
  let finish, entered;
  const started = new Promise(resolve => {entered=resolve;});
  peer.setRemoteDescription = () => {entered(); return new Promise(resolve => {finish=resolve;});};
  let answered = false;
  peer.createAnswer = async () => {answered=true; return {type:'answer',sdp:'answer'};};
  const task = call.acceptOffer({type:'offer',sdp:'offer'});
  await started;
  call.end();
  finish();
  await assert.rejects(task, /Call ended/);
  assert.equal(answered, false);
  assert.equal(track.stopped, true);
  assert.deepEqual(signals, []);
});

test('cancellation on calling state does not transmit the offer', async () => {
  const {call,track,signals} = fixture();
  call.onState = state => { if (state === 'calling') call.end(); };
  await assert.rejects(call.createOffer(), /Call ended/);
  assert.equal(track.stopped, true);
  assert.deepEqual(signals, []);
});

test('peer reports disconnected and later connected states without stopping media early', async () => {
  const {call,peer,track}=fixture();
  await call.createOffer();
  await call.receiveAnswer({type:'answer',sdp:'answer'});
  peer.connectionState='connected'; peer.onconnectionstatechange();
  peer.connectionState='disconnected'; peer.onconnectionstatechange();
  assert.equal(call.state,'disconnected'); assert.equal(track.stopped,false);
  peer.connectionState='connected'; peer.onconnectionstatechange();
  assert.equal(call.state,'connected');
  call.end(); assert.equal(track.stopped,true);
});
