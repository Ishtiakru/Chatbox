// End-to-end signaling against an emulator with fake WebRTC devices, not phones.
import { readFileSync } from 'node:fs';
import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, setDoc, onSnapshot } from 'firebase/firestore';
import { createFirestoreCall, watchIncomingCalls } from '../src/firestore-call-transport.js';
import { createCallController } from '../src/call-controller.js';
let env;
before(async () => {
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Use an emulator.');
  env = await initializeTestEnvironment({projectId:'demo-chatbox',firestore:{rules:readFileSync(process.env.CHATBOX_TEST_RULES_FILE || 'firestore.calls.rules','utf8')}});
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx => setDoc(doc(ctx.firestore(),'conversations','chat'),{participants:['alice','bob'],createdBy:'alice',status:'active',createdAt:new Date(),updatedAt:new Date()}));
});
after(async () => { if (env) await env.cleanup(); });
const wait = () => { let resolve, reject; const promise = new Promise((a,b) => {resolve=a;reject=b;}); return {resolve,reject,promise}; };
const timeout = async (promise) => { let timer; try { return await Promise.race([promise, new Promise((_,reject) => { timer = setTimeout(() => reject(new Error('Timed out waiting for signaling')),5000); })]); } finally { clearTimeout(timer); } };
function device() {
  const tracks = [{stopped:false,stop(){this.stopped=true;}}];
  const peer = {candidates:[], addTrack(){},async createOffer(){return {type:'offer',sdp:'offer'};},async createAnswer(){return {type:'answer',sdp:'answer'};},async setLocalDescription(){this.onicecandidate?.({candidate:{toJSON:()=>({candidate:'candidate:test',sdpMid:'0',sdpMLineIndex:0})}});},async setRemoteDescription(d){this.remote=d;},async addIceCandidate(c){this.candidates.push(c);},close(){this.closed=true;}};
  let permission=0;
  return {tracks,peer,mediaDevices:{async getUserMedia(){permission++;return {getTracks:()=>tracks};}},peerFactory:()=>peer,get permission(){return permission;}};
}
test('two participants exchange offer, explicit acceptance, answer, ICE and hangup', async () => {
  const a=device(),b=device(), incoming=wait(),answer=wait(),remoteEnd=wait(), errors=[];
  let caller, receiver;
  try {
    caller = createFirestoreCall({db:env.authenticatedContext('alice').firestore(),conversationId:'chat',uid:'alice',peerUid:'bob',role:'caller',media:'audio',mediaDevices:a.mediaDevices,peerFactory:a.peerFactory,onState:state=>{if(state==='connecting') answer.resolve(); if(state==='closed') remoteEnd.resolve();},onError:e=>errors.push(e)});
    await timeout(caller.start());
    receiver = createFirestoreCall({db:env.authenticatedContext('bob').firestore(),conversationId:'chat',uid:'bob',peerUid:'alice',role:'receiver',callId:caller.id,media:'audio',mediaDevices:b.mediaDevices,peerFactory:b.peerFactory,onIncoming:()=>incoming.resolve(),onError:e=>errors.push(e)});
    receiver.listen();
    assert.throws(() => receiver.listen(), /Already listening/);
    await timeout(incoming.promise);
    assert.equal(b.permission,0);
    await timeout(receiver.accept());
    await timeout(answer.promise);
    assert.equal(a.peer.remote.type,'answer'); assert.equal(b.peer.remote.type,'offer');
    assert.equal(b.permission,1);
    // Give snapshot delivery a chance, bounded by the test timeout.
    const iceReady = new Promise((resolve,reject) => {
      const deadline = setTimeout(()=>{clearInterval(timer);reject(new Error('Missing ICE'));},4000);
      const timer=setInterval(()=>{if(a.peer.candidates.length && b.peer.candidates.length){clearInterval(timer);clearTimeout(deadline);resolve();}},10);
    });
    await timeout(iceReady);
    await timeout(receiver.hangUp()); await timeout(remoteEnd.promise);
    assert.equal(a.tracks[0].stopped,true); assert.equal(b.tracks[0].stopped,true); assert.deepEqual(errors,[]);
  } finally { caller?.close(); receiver?.close(); }
});
test('incoming-call discovery only presents fresh ringing calls addressed to the receiver', async () => {
  const now = Date.now();
  await env.withSecurityRulesDisabled(async ctx => {
    const db = ctx.firestore();
    const base = {callerId:'alice',receiverId:'bob',media:'audio',offer:{type:'offer',sdp:'test'},status:'ringing',updatedAt:new Date(now)};
    await Promise.all([
      setDoc(doc(db,'conversations','chat','calls','fresh'),{...base, createdAt:new Date(now)}),
      setDoc(doc(db,'conversations','chat','calls','stale'),{...base, createdAt:new Date(now - 6*60*1000)}),
      setDoc(doc(db,'conversations','chat','calls','ended'),{...base,status:'ended',createdAt:new Date(now)}),
      setDoc(doc(db,'conversations','chat','calls','other'),{...base,receiverId:'alice',createdAt:new Date(now)}),
    ]);
  });
  const result = wait();
  let stop;
  try {
    stop = watchIncomingCalls(env.authenticatedContext('bob').firestore(), 'chat', 'bob', calls => { if (calls.some(call => call.id === 'fresh')) result.resolve(calls); }, result.reject);
    const calls = await timeout(result.promise);
    assert.deepEqual(calls.map(call=>call.id), ['fresh']);
  } finally { stop?.(); }
});
test('call controller uses signaling with explicit receiver consent and closes local tracks', async () => {
  await env.withSecurityRulesDisabled(async ctx => setDoc(doc(ctx.firestore(),'conversations','controller-chat'),{participants:['alice','bob'],createdBy:'alice',status:'active',createdAt:new Date(),updatedAt:new Date()}));
  const a=device(), b=device(), incoming=wait(), offered=wait(), answer=wait(), ended=wait();
  const dbA=env.authenticatedContext('alice').firestore(), dbB=env.authenticatedContext('bob').firestore();
  function controller(db, dev, onChange) {
    return createCallController({onChange,
      watchIncoming:(ctx,next,fail)=>watchIncomingCalls(db,ctx.id,ctx.uid,next,fail),
      createSession:opts=>createFirestoreCall({...opts,db,conversationId:opts.id,mediaDevices:dev.mediaDevices,peerFactory:dev.peerFactory})});
  }
  const caller = controller(dbA,a,state=>{if(state.status==='connecting') answer.resolve(); if(state.status==='idle' && a.permission) ended.resolve();});
  const receiver = controller(dbB,b,state=>{if(state.incoming.length) incoming.resolve(state.incoming[0].id); if(state.status==='incoming') offered.resolve();});
  try {
    caller.setConversation({id:'controller-chat',uid:'alice',peerUid:'bob'});
    receiver.setConversation({id:'controller-chat',uid:'bob',peerUid:'alice'});
    await timeout(caller.start('audio'));
    const id = await timeout(incoming.promise);
    receiver.openIncoming(id); await timeout(offered.promise);
    assert.equal(b.permission,0);
    await timeout(receiver.accept()); await timeout(answer.promise);
    assert.equal(b.permission,1);
    receiver.hangUp(); await timeout(ended.promise);
    assert.equal(a.tracks[0].stopped,true);
    assert.equal(b.tracks[0].stopped,true);
  } finally { caller.dispose(); receiver.dispose(); }
});
test('hangup releases media and both listeners even if the simulated end write stalls', async () => {
  await env.withSecurityRulesDisabled(async ctx => setDoc(doc(ctx.firestore(),'conversations','stall-chat'),{participants:['alice','bob'],createdBy:'alice',status:'active',createdAt:new Date(),updatedAt:new Date()}));
  const db = env.authenticatedContext('alice').firestore(), dev=device(), endWrite=wait();
  let attached = 0, detached = 0, call, hanging, writeStarted = false;
  const subscribe = (...args) => {
    const stop = onSnapshot(...args);
    attached++;
    return () => { detached++; stop(); };
  };
  const update = async (_ref, data) => {
    assert.equal(data.status,'ended');
    writeStarted = true;
    await endWrite.promise;
  };
  try {
    call = createFirestoreCall({db,conversationId:'stall-chat',uid:'alice',peerUid:'bob',role:'caller',media:'audio',mediaDevices:dev.mediaDevices,peerFactory:dev.peerFactory,subscribe,update});
    await timeout(call.start());
    assert.equal(attached,2);
    hanging = call.hangUp();
    assert.equal(writeStarted,true);
    assert.equal(dev.tracks[0].stopped,true);
    assert.equal(dev.peer.closed,true);
    assert.equal(detached,2);
    endWrite.resolve();
    await timeout(hanging);
  } finally {
    endWrite.resolve();
    call?.close();
    if (hanging) await hanging.catch(()=>{});
  }
});
