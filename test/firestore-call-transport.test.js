import test from 'node:test';
import assert from 'node:assert/strict';
import { initializeApp, deleteApp } from 'firebase/app';
import { getFirestore } from 'firebase/firestore';
import { createFirestoreCall, watchIncomingCalls } from '../src/firestore-call-transport.js';

const app = initializeApp({projectId:'demo-chatbox',apiKey:'fake',appId:'fake'}, 'call-transport-unit');
const db = getFirestore(app);
test.after(async () => { await deleteApp(app); });
function fakeSubscribe() {
  const listeners=[];
  function subscribe(ref, ...args) {
    const options = typeof args[0] === 'object' ? args.shift() : undefined;
    const [next, fail] = args;
    const item={ref,options,next,fail,stopped:false};
    listeners.push(item);
    return () => {item.stopped=true;};
  }
  return {listeners,subscribe};
}
const callData = {callerId:'alice',receiverId:'bob',media:'audio',status:'ringing',offer:{type:'offer',sdp:'offer'},createdAt:{toMillis:()=>Date.now()}};
const snapshot = (fromCache, data=callData) => ({metadata:{fromCache,hasPendingWrites:false},exists:()=>!!data,data:()=>data});

test('incoming discovery hides cache-only offers and clears offers on cache fallback', () => {
  const fake=fakeSubscribe(), changes=[];
  const stop=watchIncomingCalls(db,'chat','bob',calls=>changes.push(calls),()=>{},fake.subscribe);
  const listener=fake.listeners[0];
  assert.equal(listener.options.includeMetadataChanges,true);
  const doc={id:'offer',metadata:{hasPendingWrites:false},data:()=>callData};
  listener.next({metadata:{fromCache:true},docs:[doc]});
  assert.deepEqual(changes.at(-1),[]);
  listener.next({metadata:{fromCache:false},docs:[doc]});
  assert.deepEqual(changes.at(-1).map(x=>x.id),['offer']);
  listener.next({metadata:{fromCache:true},docs:[doc]});
  assert.deepEqual(changes.at(-1),[]);
  stop(); assert.equal(listener.stopped,true);
});

test('receiver does not present cached offers, and cache fallback tears down a confirmed session', async () => {
  const fake=fakeSubscribe(), errors=[], states=[];
  let offers=0, mediaRequests=0;
  const call=createFirestoreCall({db,conversationId:'chat',uid:'bob',peerUid:'alice',role:'receiver',callId:'offer',media:'audio',
    mediaDevices:{getUserMedia:async()=>{mediaRequests++; throw new Error('Unexpected media');}},peerFactory:()=>{},subscribe:fake.subscribe,
    onIncoming:()=>{offers++;},onError:error=>errors.push(error),onState:state=>states.push(state)});
  call.listen();
  const listener=fake.listeners[0];
  assert.equal(listener.options.includeMetadataChanges,true);
  listener.next(snapshot(true));
  await Promise.resolve(); await Promise.resolve();
  assert.equal(offers,0);
  assert.equal(mediaRequests,0);
  listener.next(snapshot(false));
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(offers,1);
  assert.equal(mediaRequests,0);
  listener.next(snapshot(true));
  assert.equal(errors.length,1);
  assert.equal(states.at(-1),'closed');
  assert.equal(fake.listeners.every(item=>item.stopped),true);
  call.close();
});

test('server-confirmed missing call is closed without asking for media', () => {
  const fake=fakeSubscribe(), errors=[];
  const call=createFirestoreCall({db,conversationId:'chat',uid:'bob',peerUid:'alice',role:'receiver',callId:'offer',media:'audio',
    mediaDevices:{getUserMedia:async()=>{throw new Error('Unexpected media');}},peerFactory:()=>{},subscribe:fake.subscribe,onError:error=>errors.push(error)});
  call.listen();
  fake.listeners[0].next(snapshot(false,null));
  assert.equal(errors.length,1);
  assert.equal(fake.listeners.every(item=>item.stopped),true);
  call.close();
});

test('cache fallback after acceptance releases active local media', async () => {
  const fake=fakeSubscribe(), errors=[];
  const track={stopped:false,stop(){this.stopped=true;}};
  const peer={closed:false,addTrack(){},async setRemoteDescription(){},async createAnswer(){return {type:'answer',sdp:'answer'};},async setLocalDescription(){},close(){this.closed=true;}};
  const call=createFirestoreCall({db,conversationId:'chat',uid:'bob',peerUid:'alice',role:'receiver',callId:'offer',media:'audio',
    mediaDevices:{getUserMedia:async()=>({getTracks:()=>[track]})},peerFactory:()=>peer,subscribe:fake.subscribe,update:async()=>{},onError:error=>errors.push(error)});
  call.listen();
  fake.listeners[0].next(snapshot(false));
  await new Promise(resolve=>setImmediate(resolve));
  await call.accept();
  assert.equal(track.stopped,false);
  fake.listeners[0].next(snapshot(true));
  assert.equal(track.stopped,true);
  assert.equal(peer.closed,true);
  assert.equal(fake.listeners.every(item=>item.stopped),true);
  assert.equal(errors.length,1);
  call.close();
});
