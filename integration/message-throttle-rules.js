// Isolated demo project, synthetic users; never a production database or deploy.
import {readFileSync} from 'node:fs';
import {before,beforeEach,after,test} from 'node:test';
import assert from 'node:assert/strict';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {collection,doc,getDoc,getDocs,setDoc,deleteDoc,writeBatch,serverTimestamp,Timestamp} from 'firebase/firestore';
import {sendThrottledMessage} from '../src/throttled-message.js';
let env;
before(async()=>{
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Local Firestore emulator required.');
  env=await initializeTestEnvironment({projectId:'demo-chatbox',firestore:{rules:readFileSync('firestore.rate-limited-experimental.rules','utf8')}});
});
beforeEach(async()=>{
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx=>{
    const db=ctx.firestore(), b=writeBatch(db);
    for(const uid of ['alice','bob','eve']) b.set(doc(db,'profiles',uid),{uid,displayName:uid});
    for(const id of ['chat','other']) b.set(doc(db,'conversations',id),{participants:['alice','bob'],createdBy:'alice',status:'active',createdAt:new Date(),updatedAt:new Date()});
    b.set(doc(db,'conversations','pending'),{participants:['alice','bob'],createdBy:'alice',status:'pending',createdAt:new Date(),updatedAt:new Date()});
    await b.commit();
  });
});
after(async()=>{if(env)await env.cleanup();});
const db=uid=>env.authenticatedContext(uid).firestore();
const msg=(d,conv,id)=>doc(d,'conversations',conv,'messages',id);
const rate=(d,uid)=>doc(d,'messageThrottle',uid);
function batchMessage(d,uid,conv,id,overrides={}) {
  const b=writeBatch(d);
  b.set(msg(d,conv,id),{senderId:uid,body:'hello',sentAt:serverTimestamp(),...overrides});
  b.set(rate(d,uid),{conversationId:conv,messageId:id,lastSentAt:serverTimestamp()});
  return b.commit();
}
test('a message and its unique reservation must be atomic; records are private and immutable to outsiders',async()=>{
  const a=db('alice'), b=db('bob');
  await assertFails(setDoc(msg(a,'chat','no-rate'),{senderId:'alice',body:'hello',sentAt:serverTimestamp()}));
  await assertFails(setDoc(rate(a,'alice'),{conversationId:'chat',messageId:'no-msg',lastSentAt:serverTimestamp()}));
  await assertSucceeds(batchMessage(a,'alice','chat','one'));
  assert.equal((await getDoc(rate(a,'alice'))).data().messageId,'one');
  await assertFails(getDoc(rate(b,'alice')));
  await assertFails(getDocs(collection(a,'messageThrottle')));
  await assertFails(deleteDoc(rate(a,'alice')));
  await assertFails(setDoc(rate(b,'alice'),{conversationId:'chat',messageId:'other',lastSentAt:serverTimestamp()}));
  await assertFails(setDoc(msg(a,'chat','one'),{senderId:'alice',body:'edited',sentAt:serverTimestamp()}));
});
test('per-user cooldown spans conversations, without preventing another participant sending',async()=>{
  const a=db('alice'), b=db('bob');
  await assertSucceeds(batchMessage(a,'alice','chat','one'));
  await assertFails(batchMessage(a,'alice','other','rapid'));
  assert.equal((await getDoc(msg(a,'other','rapid'))).exists(),false);
  await assertSucceeds(batchMessage(b,'bob','chat','reply'));
  await env.withSecurityRulesDisabled(async ctx=>setDoc(rate(ctx.firestore(),'alice'),{conversationId:'chat',messageId:'one',lastSentAt:Timestamp.fromMillis(Date.now()-10000)}));
  await assertSucceeds(batchMessage(a,'alice','other','later'));
});
test('one reservation cannot authorize multiple messages or mismatched targets',async()=>{
  const a=db('alice');
  let b=writeBatch(a);
  for(const id of ['one','two']) b.set(msg(a,'chat',id),{senderId:'alice',body:id,sentAt:serverTimestamp()});
  b.set(rate(a,'alice'),{conversationId:'chat',messageId:'one',lastSentAt:serverTimestamp()});
  await assertFails(b.commit());
  b=writeBatch(a);
  b.set(msg(a,'chat','one'),{senderId:'alice',body:'hello',sentAt:serverTimestamp()});
  b.set(rate(a,'alice'),{conversationId:'other',messageId:'one',lastSentAt:serverTimestamp()});
  await assertFails(b.commit());
  assert.equal((await getDoc(msg(a,'chat','one'))).exists(),false);
});
test('reservation does not bypass consent, blocks, membership or message validation',async()=>{
  const a=db('alice'), e=db('eve');
  await assertFails(batchMessage(e,'eve','chat','outsider'));
  await assertFails(batchMessage(a,'alice','pending','early'));
  await assertFails(batchMessage(a,'alice','chat','spoof',{senderId:'bob'}));
  await assertFails(batchMessage(a,'alice','chat','empty',{body:''}));
  await assertFails(batchMessage(a,'alice','chat','past',{sentAt:Timestamp.fromMillis(0)}));
  await setDoc(doc(a,'userBlocks','alice','blocked','bob'),{createdAt:serverTimestamp()});
  await assertFails(batchMessage(a,'alice','chat','blocked'));
  assert.equal((await getDoc(rate(a,'alice'))).exists(),false);
});
test('client uses the atomic batch, validates input and leaves rejected cooldown sends uncommitted',async()=>{
  const a=db('alice');
  await assert.rejects(sendThrottledMessage(a,'chat','alice','  '));
  const ref=await sendThrottledMessage(a,'chat','alice','  Hello  ');
  assert.equal((await getDoc(ref)).data().body,'Hello');
  await assert.rejects(sendThrottledMessage(a,'chat','alice','too soon'));
  assert.equal((await getDoc(rate(a,'alice'))).data().messageId,ref.id);
});
test('two simultaneous sends for the same account cannot both consume the same interval',async()=>{
  const a=db('alice'), secondDevice=db('alice');
  const results=await Promise.allSettled([batchMessage(a,'alice','chat','first'),batchMessage(secondDevice,'alice','other','second')]);
  assert.equal(results.filter(x=>x.status==='fulfilled').length,1);
  assert.equal(results.filter(x=>x.status==='rejected').length,1);
});
