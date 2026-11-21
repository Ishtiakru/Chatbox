// Isolated demo project and synthetic identities only, never a deployment.
import {readFileSync} from 'node:fs';
import {before,beforeEach,after,test} from 'node:test';
import assert from 'node:assert/strict';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {collection,doc,getDoc,getDocs,setDoc,deleteDoc,writeBatch,serverTimestamp,Timestamp} from 'firebase/firestore';
import {createThrottledRequest} from '../src/throttled-request.js';
import {sendThrottledMessage} from '../src/throttled-message.js';
let env;
before(async()=>{
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Local Firestore emulator required.');
  env=await initializeTestEnvironment({projectId:'demo-chatbox',firestore:{rules:readFileSync('firestore.request-limited-experimental.rules','utf8')}});
});
beforeEach(async()=>{
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx=>{
    const d=ctx.firestore(),b=writeBatch(d);
    for(const uid of ['alice','bob','eve']) b.set(doc(d,'profiles',uid),{uid,displayName:uid});
    b.set(doc(d,'conversations','existing'),{participants:['alice','bob'],createdBy:'alice',status:'active',createdAt:new Date(),updatedAt:new Date()});
    await b.commit();
  });
});
after(async()=>{if(env)await env.cleanup();});
const db=uid=>env.authenticatedContext(uid).firestore();
const request=(d,id)=>doc(d,'conversations',id);
const rate=(d,uid)=>doc(d,'requestThrottle',uid);
const contents=(uid,peer='bob')=>({participants:[uid,peer],createdBy:uid,status:'pending',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
function send(d,uid,id,peer='bob',override={}) {
  const b=writeBatch(d);
  b.set(request(d,id),{...contents(uid,peer),...override});
  b.set(rate(d,uid),{conversationId:id,lastRequestedAt:serverTimestamp()});
  return b.commit();
}
async function existsAdmin(path) {
  let result;
  await env.withSecurityRulesDisabled(async ctx=>{result=(await getDoc(doc(ctx.firestore(),...path))).exists();});
  return result;
}
test('a valid request and reservation must be atomic, private and non-deletable',async()=>{
  const a=db('alice'), b=db('bob');
  await assertFails(setDoc(request(a,'no-rate'),contents('alice')));
  await assertFails(setDoc(rate(a,'alice'),{conversationId:'no-request',lastRequestedAt:serverTimestamp()}));
  await assertSucceeds(send(a,'alice','one'));
  assert.equal((await getDoc(rate(a,'alice'))).data().conversationId,'one');
  await assertFails(getDoc(rate(b,'alice')));
  await assertFails(getDocs(collection(a,'requestThrottle')));
  await assertFails(deleteDoc(rate(a,'alice')));
  await assertFails(setDoc(rate(b,'alice'),{conversationId:'one',lastRequestedAt:serverTimestamp()}));
});
test('cooldown is per sender across recipients, while another account can request',async()=>{
  const a=db('alice'),b=db('bob');
  await assertSucceeds(send(a,'alice','one'));
  await assertFails(send(a,'alice','rapid','eve'));
  assert.equal(await existsAdmin(['conversations','rapid']),false);
  await assertSucceeds(send(b,'bob','other','alice'));
  await env.withSecurityRulesDisabled(async ctx=>setDoc(rate(ctx.firestore(),'alice'),{conversationId:'one',lastRequestedAt:Timestamp.fromMillis(Date.now()-60000)}));
  await assertSucceeds(send(a,'alice','later','eve'));
});
test('one reservation cannot authorize two requests or an incorrect target',async()=>{
  const a=db('alice'); let b=writeBatch(a);
  b.set(request(a,'one'),contents('alice')); b.set(request(a,'two'),contents('alice'));
  b.set(rate(a,'alice'),{conversationId:'one',lastRequestedAt:serverTimestamp()});
  await assertFails(b.commit());
  b=writeBatch(a); b.set(request(a,'one'),contents('alice')); b.set(rate(a,'alice'),{conversationId:'two',lastRequestedAt:serverTimestamp()});
  await assertFails(b.commit());
  assert.equal(await existsAdmin(['conversations','one']),false);
});
test('reservations cannot bypass identity, distinct members, profile existence or blocking',async()=>{
  const a=db('alice');
  await assertFails(send(a,'alice','spoof','bob',{createdBy:'bob'}));
  await assertFails(send(a,'alice','self','alice'));
  await assertFails(send(a,'alice','missing','unknown'));
  await setDoc(doc(a,'userBlocks','alice','blocked','bob'),{createdAt:serverTimestamp()});
  await assertFails(send(a,'alice','blocked'));
  assert.equal((await getDoc(rate(a,'alice'))).exists(),false);
});
test('client batches the request and preserves the independent message cooldown',async()=>{
  const a=db('alice');
  await assert.rejects(createThrottledRequest(a,'alice','alice'));
  const ref=await createThrottledRequest(a,'alice','bob');
  assert.equal((await getDoc(ref)).data().status,'pending');
  await assert.rejects(createThrottledRequest(a,'alice','eve'));
  const msg=await sendThrottledMessage(a,'existing','alice','hello');
  assert.equal((await getDoc(msg)).data().body,'hello');
  await assert.rejects(sendThrottledMessage(a,'existing','alice','too soon'));
});
test('two devices cannot both create a request in the same interval',async()=>{
  const results=await Promise.allSettled([send(db('alice'),'alice','one'),send(db('alice'),'alice','two','eve')]);
  assert.equal(results.filter(x=>x.status==='fulfilled').length,1);
  assert.equal(results.filter(x=>x.status==='rejected').length,1);
});
test('old timestamps, extra reservation fields, and reused conversation IDs do not bypass the reservation',async()=>{
  const a=db('alice'); let b=writeBatch(a);
  b.set(request(a,'bad-time'),contents('alice'));
  b.set(rate(a,'alice'),{conversationId:'bad-time',lastRequestedAt:Timestamp.fromMillis(0)});
  await assertFails(b.commit());
  b=writeBatch(a); b.set(request(a,'extra'),contents('alice'));
  b.set(rate(a,'alice'),{conversationId:'extra',lastRequestedAt:serverTimestamp(),extra:true});
  await assertFails(b.commit());
  await assertFails(setDoc(rate(a,'alice'),{conversationId:'existing',lastRequestedAt:serverTimestamp()}));
  assert.equal(await existsAdmin(['conversations','bad-time']),false);
});
test('a recipient block prevents requests and cannot consume the sender cooldown',async()=>{
  const a=db('alice'),b=db('bob');
  await setDoc(doc(b,'userBlocks','bob','blocked','alice'),{createdAt:serverTimestamp()});
  await assertFails(send(a,'alice','blocked'));
  assert.equal((await getDoc(rate(a,'alice'))).exists(),false);
  await assertSucceeds(send(a,'alice','unblocked','eve'));
});
