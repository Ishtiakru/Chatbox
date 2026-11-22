import test from 'node:test';
import assert from 'node:assert/strict';
import { createPeerProfileController } from '../src/peer-profile-controller.js';
import { peerProfileHeading } from '../src/peer-profile-view.js';
const doc=(uid,name,{fromCache=false,pending=false,exists=true}={})=>({metadata:{fromCache,hasPendingWrites:pending},exists:()=>exists,data:()=>({uid,displayName:name})});
function fixture() {
  const subscriptions=[],changes=[];
  const controller=createPeerProfileController({
    subscribe:(uid,next,fail)=>{const item={uid,next,fail,stopped:false};subscriptions.push(item);return ()=>{item.stopped=true;};},
    onChange:s=>changes.push(s),
  });
  return {controller,subscriptions,changes};
}
test('only server-confirmed valid peer names are displayed; cache fallback clears them',()=>{
  const f=fixture(); f.controller.select('bob');
  const s=f.subscriptions[0];
  s.next(doc('bob','Cached',{fromCache:true})); assert.equal(f.controller.getState().name,'');
  s.next(doc('bob','Pending',{pending:true})); assert.equal(f.controller.getState().status,'unavailable');
  s.next(doc('bob','  Bob  ')); assert.deepEqual(f.controller.getState(),{uid:'bob',status:'ready',name:'Bob'});
  s.next(doc('bob','old',{fromCache:true})); assert.equal(f.controller.getState().name,'');
  s.next(doc('other','Eve')); assert.equal(f.controller.getState().status,'unavailable');
  s.next(doc('bob','x'.repeat(81))); assert.equal(f.controller.getState().status,'unavailable');
  s.next(doc('bob',' ',{})); assert.equal(f.controller.getState().status,'unavailable');
  s.next(doc('bob','x',{exists:false})); assert.equal(f.controller.getState().status,'unavailable');
  f.controller.dispose(); assert.equal(s.stopped,true);
});
test('switching peers or disposing prevents late names and errors from leaking',()=>{
  const f=fixture(); f.controller.select('bob'); const a=f.subscriptions[0];
  a.next(doc('bob','Bob'));
  f.controller.select('eve'); const b=f.subscriptions[1];
  assert.equal(a.stopped,true); assert.deepEqual(f.controller.getState(),{uid:'eve',status:'loading',name:''});
  a.next(doc('bob','Late')); a.fail(new Error('secret'));
  assert.equal(f.controller.getState().name,'');
  b.next(doc('eve','Eve')); const count=f.changes.length;
  f.controller.dispose(); f.controller.dispose(); b.next(doc('eve','Late')); b.fail(new Error('secret'));
  assert.equal(b.stopped,true); assert.equal(f.changes.length,count);
  assert.deepEqual(f.controller.getState(),{uid:'',status:'idle',name:''});
});
test('listener errors and synchronous subscribe failures degrade to unavailable, without exposing error',()=>{
  const f=fixture(); f.controller.select('bob'); f.subscriptions[0].fail(new Error('private'));
  assert.deepEqual(f.controller.getState(),{uid:'bob',status:'unavailable',name:''}); f.controller.dispose();
  const c=createPeerProfileController({subscribe:()=>{throw new Error('secret');},onChange:()=>{}});
  c.select('eve'); assert.deepEqual(c.getState(),{uid:'eve',status:'unavailable',name:''}); c.dispose();
});
test('synchronous observer re-selection releases obsolete subscription',()=>{
  let c, firstStopped=false;
  c=createPeerProfileController({
    subscribe:(uid,next)=>{if(uid==='bob') next(doc('bob','Bob')); return ()=>{if(uid==='bob') firstStopped=true;};},
    onChange:s=>{if(s.uid==='bob' && s.status==='ready') c.select('eve');},
  });
  c.select('bob');
  assert.equal(firstStopped,true); assert.equal(c.getState().uid,'eve');
  c.dispose();
});
test('escaped heading identifies names as unverified and retains the exact ID',()=>{
  const html=peerProfileHeading('b<ob',{uid:'b<ob',status:'ready',name:'<img src=x onerror=1>'});
  assert.doesNotMatch(html,/<img/); assert.match(html,/&lt;img/); assert.match(html,/b&lt;ob/);
  assert.match(html,/not verified/); assert.match(html,/<bdi>/);
  const wrong=peerProfileHeading('bob',{uid:'eve',status:'ready',name:'Impostor'});
  assert.doesNotMatch(wrong,/Impostor/); assert.match(wrong,/Chatbox ID/);
});
