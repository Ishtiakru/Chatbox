// In-memory/emulated identities only. No production Firebase resources are used.
import { readFileSync } from 'node:fs';
import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertFails } from '@firebase/rules-unit-testing';
import { addDoc, collection, doc, getDoc, getDocsFromServer, onSnapshot, query, serverTimestamp, setDoc, updateDoc, where, orderBy, limitToLast, endBefore, writeBatch, Timestamp } from 'firebase/firestore';
import { createMessageFeed } from '../src/message-feed.js';
import { blockAndClose, unblock, watchOwnBlocks } from '../src/block-service.js';
import { createBlockController } from '../src/block-controller.js';
import { createPeerProfileController } from '../src/peer-profile-controller.js';
let env;
before(async () => {
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('This test requires a local Firestore emulator.');
  env = await initializeTestEnvironment({ projectId: 'demo-chatbox', firestore: { rules: readFileSync(process.env.CHATBOX_TEST_RULES_FILE || 'firestore.rules', 'utf8') } });
  await env.clearFirestore();
});
after(async () => { if (env) await env.cleanup(); });
function waitForValue(build, predicate) {
  return new Promise((resolve, reject) => {
    let unsubscribe = () => {};
    const timer = setTimeout(() => { unsubscribe(); reject(new Error('Snapshot timed out.')); }, 10000);
    unsubscribe = build(snapshot => {
      if (predicate(snapshot)) { clearTimeout(timer); unsubscribe(); resolve(snapshot); }
    }, error => { clearTimeout(timer); unsubscribe(); reject(error); });
  });
}
test('two simulated users exchange messages in real time, while a third cannot read', async () => {
  const alice = env.authenticatedContext('flow-alice').firestore();
  const bob = env.authenticatedContext('flow-bob').firestore();
  const stranger = env.authenticatedContext('flow-stranger').firestore();
  await setDoc(doc(alice, 'profiles', 'flow-alice'), { uid: 'flow-alice', displayName: 'Alice' });
  await setDoc(doc(bob, 'profiles', 'flow-bob'), { uid: 'flow-bob', displayName: 'Bob' });
  const conversation = await addDoc(collection(alice, 'conversations'), {
    participants: ['flow-alice', 'flow-bob'], createdBy: 'flow-alice', status:'pending', createdAt: serverTimestamp(), updatedAt:serverTimestamp()
  });
  const bobQuery = query(collection(bob, 'conversations'), where('participants', 'array-contains', 'flow-bob'));
  const seenConversation = await waitForValue((next, error) => onSnapshot(bobQuery, next, error), snapshot => snapshot.docs.some(d => d.id === conversation.id));
  assert.equal(seenConversation.docs.some(d => d.id === conversation.id), true);
  await updateDoc(doc(bob,'conversations',conversation.id),{status:'active',updatedAt:serverTimestamp()});
  const bobMessage = waitForValue((next, error) => onSnapshot(collection(bob, 'conversations', conversation.id, 'messages'), next, error), snapshot => snapshot.docs.some(d => d.data().body === 'Hello Bob'));
  await addDoc(collection(alice, 'conversations', conversation.id, 'messages'), { senderId:'flow-alice', body:'Hello Bob', sentAt:serverTimestamp() });
  assert.equal((await bobMessage).docs.some(d => d.data().senderId === 'flow-alice'), true);
  const aliceMessage = waitForValue((next, error) => onSnapshot(collection(alice, 'conversations', conversation.id, 'messages'), next, error), snapshot => snapshot.docs.some(d => d.data().body === 'Hello Alice'));
  const reply = await addDoc(collection(bob, 'conversations', conversation.id, 'messages'), { senderId:'flow-bob', body:'Hello Alice', sentAt:serverTimestamp() });
  assert.equal((await aliceMessage).docs.some(d => d.data().senderId === 'flow-bob'), true);
  await assertFails(getDoc(doc(stranger, 'conversations', conversation.id, 'messages', reply.id)));
});
test('selected peer profile shows server-confirmed updates without allowing directory listing', async () => {
  const bob=env.authenticatedContext('profile-bob').firestore();
  const alice=env.authenticatedContext('profile-alice').firestore();
  await setDoc(doc(bob,'profiles','profile-bob'),{uid:'profile-bob',displayName:'Bob'});
  let state, notify=()=>{};
  const controller=createPeerProfileController({
    subscribe:(uid,next,fail)=>onSnapshot(doc(alice,'profiles',uid),{includeMetadataChanges:true},next,fail),
    onChange:value=>{state=value;notify();},
  });
  const waitUntil=predicate=>new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{notify=()=>{};reject(new Error('Profile timed out.'));},10000);
    notify=()=>{if(predicate()){clearTimeout(timer);notify=()=>{};resolve();}};
    notify();
  });
  try {
    controller.select('profile-bob');
    await waitUntil(()=>state?.status==='ready' && state.name==='Bob');
    await setDoc(doc(bob,'profiles','profile-bob'),{uid:'profile-bob',displayName:'Bob updated'});
    await waitUntil(()=>state?.status==='ready' && state.name==='Bob updated');
    await assertFails(getDocsFromServer(collection(alice,'profiles')));
  } finally {controller.dispose();}
});
test('message history pages without gaps when the realtime window advances', async () => {
  await env.withSecurityRulesDisabled(async ctx => {
    const db = ctx.firestore();
    const batch = writeBatch(db);
    batch.set(doc(db,'conversations','long-history'),{participants:['flow-alice','flow-bob'],createdBy:'flow-alice',status:'active',createdAt:new Date(),updatedAt:new Date()});
    for (let i=0;i<105;i++) {
      const id=String(i).padStart(3,'0');
      batch.set(doc(db,'conversations','long-history','messages',id),{senderId:'flow-alice',body:id,sentAt:Timestamp.fromMillis(1600000000000+i*1000)});
    }
    await batch.commit();
  });
  const db=env.authenticatedContext('flow-alice').firestore();
  const messages=collection(db,'conversations','long-history','messages');
  let page, error, notify=()=>{};
  const feed=createMessageFeed({
    listenRecent:(next,fail)=>onSnapshot(query(messages,orderBy('sentAt'),limitToLast(100)),{includeMetadataChanges:true},next,fail),
    fetchOlder:cursor=>getDocsFromServer(query(messages,orderBy('sentAt'),endBefore(cursor),limitToLast(100))),
    onChange:value=>{page=value;notify();},onError:value=>{error=value;notify();},
  });
  const waitUntil = predicate => new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{notify=()=>{};reject(new Error('Message feed timed out.'));},5000);
    notify=()=>{if(error || predicate()){clearTimeout(timer);notify=()=>{};error ? reject(error) : resolve();}};
    notify();
  });
  try {
    await waitUntil(()=>page?.messages.length===100 && !page.fromCache);
    assert.equal(page.messages[0].id,'005');
    await setDoc(doc(messages,'new'),{senderId:'flow-alice',body:'new',sentAt:serverTimestamp()});
    await waitUntil(()=>page?.messages.some(m=>m.id==='new'));
    assert.equal(page.messages.length,101);
    await feed.loadOlder();
    assert.equal(page.messages.length,106);
    assert.equal(page.messages[0].id,'000');
    assert.equal(page.messages.at(-1).id,'new');
    assert.equal(page.hasMore,false);
  } finally { feed.stop(); }
});
test('a completely displaced recent window is reset and paged from the new boundary', async () => {
  await env.withSecurityRulesDisabled(async ctx => {
    const db=ctx.firestore(), batch=writeBatch(db);
    batch.set(doc(db,'conversations','window-gap'),{participants:['flow-alice','flow-bob'],createdBy:'flow-alice',status:'active',createdAt:new Date(),updatedAt:new Date()});
    for (let i=0;i<100;i++) {
      const id=String(i).padStart(3,'0');
      batch.set(doc(db,'conversations','window-gap','messages',id),{senderId:'flow-alice',body:id,sentAt:Timestamp.fromMillis(1600000000000+i*1000)});
    }
    await batch.commit();
  });
  const db=env.authenticatedContext('flow-alice').firestore();
  const messages=collection(db,'conversations','window-gap','messages');
  let page, error, notify=()=>{};
  const feed=createMessageFeed({
    listenRecent:(next,fail)=>onSnapshot(query(messages,orderBy('sentAt'),limitToLast(100)),{includeMetadataChanges:true},next,fail),
    fetchOlder:cursor=>getDocsFromServer(query(messages,orderBy('sentAt'),endBefore(cursor),limitToLast(100))),
    onChange:value=>{page=value;notify();},onError:value=>{error=value;notify();},
  });
  const waitUntil=predicate=>new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{notify=()=>{};reject(new Error('Message feed timed out.'));},10000);
    notify=()=>{if(error || predicate()){clearTimeout(timer);notify=()=>{};error ? reject(error) : resolve();}};
    notify();
  });
  try {
    await waitUntil(()=>page?.messages.length===100 && !page.fromCache);
    await env.withSecurityRulesDisabled(async ctx=>{
      const writer=ctx.firestore(), batch=writeBatch(writer);
      for(let i=100;i<205;i++) {
        const id=String(i).padStart(3,'0');
        batch.set(doc(writer,'conversations','window-gap','messages',id),{senderId:'flow-alice',body:id,sentAt:Timestamp.fromMillis(1600000000000+i*1000)});
      }
      await batch.commit();
    });
    await waitUntil(()=>page?.historyReset && page.messages.at(-1)?.id==='204');
    assert.equal(page.messages.length,100);
    assert.equal(page.messages[0].id,'105');
    await feed.loadOlder();
    assert.equal(page.historyReset,false);
    assert.equal(page.messages[0].id,'005');
    await feed.loadOlder();
    assert.deepEqual(page.messages.map(m=>m.id),Array.from({length:205},(_,i)=>String(i).padStart(3,'0')));
    assert.equal(page.hasMore,false);
  } finally {feed.stop();}
});
test('rejected optimistic messages are removed from the feed', async () => {
  await env.withSecurityRulesDisabled(async ctx => {
    await setDoc(doc(ctx.firestore(),'conversations','rejected-write'), {participants:['flow-alice','flow-bob'],createdBy:'flow-alice',status:'active',createdAt:new Date(),updatedAt:new Date()});
  });
  const db=env.authenticatedContext('flow-alice').firestore();
  const messages=collection(db,'conversations','rejected-write','messages');
  let page, error, notify=()=>{}, sawPending=false, ready=false;
  const feed=createMessageFeed({
    listenRecent:(next,fail)=>onSnapshot(query(messages,orderBy('sentAt'),limitToLast(100)),{includeMetadataChanges:true}, snapshot=>{ if (!snapshot.metadata.fromCache) ready=true; next(snapshot); },fail),
    fetchOlder:cursor=>getDocsFromServer(query(messages,orderBy('sentAt'),endBefore(cursor),limitToLast(100))),
    onChange:value=>{page=value; if(value.messages.some(m=>m.id==='invalid')) sawPending=true; notify();},
    onError:value=>{error=value;notify();},
  });
  const waitUntil=predicate=>new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{notify=()=>{};reject(new Error('Message feed timed out.'));},5000);
    notify=()=>{if(error || predicate()){clearTimeout(timer);notify=()=>{};error ? reject(error) : resolve();}};
    notify();
  });
  try {
    await waitUntil(()=>ready);
    const rejected=setDoc(doc(messages,'invalid'),{senderId:'flow-alice',body:'x'.repeat(2001),sentAt:serverTimestamp()});
    await assertFails(rejected);
    await waitUntil(()=>sawPending && !page.messages.some(m=>m.id==='invalid'));
    assert.equal(sawPending,true);
  } finally {feed.stop();}
});

test('block service atomically closes the selected conversation and preserves privacy', async () => {
  await env.withSecurityRulesDisabled(async ctx => {
    const db=ctx.firestore();
    await setDoc(doc(db,'conversations','block-ui'),{participants:['flow-alice','flow-bob'],createdBy:'flow-alice',status:'active',createdAt:new Date(),updatedAt:new Date()});
    await setDoc(doc(db,'conversations','block-ui','messages','old'),{senderId:'flow-bob',body:'history',sentAt:new Date()});
  });
  const alice=env.authenticatedContext('flow-alice').firestore(), bob=env.authenticatedContext('flow-bob').firestore();
  const conversation={id:'block-ui',participants:['flow-alice','flow-bob'],status:'active',pendingWrites:false};
  const observations=[];
  let notify=()=>{}, error;
  const stop=watchOwnBlocks(alice,'flow-alice',value=>{observations.push(value);notify();},e=>{error=e;notify();});
  const waitUntil=predicate=>new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{notify=()=>{};reject(new Error('Block watcher timed out.'));},5000);
    notify=()=>{if(error || predicate()){clearTimeout(timer);notify=()=>{};error ? reject(error) : resolve();}};
    notify();
  });
  try {
    await waitUntil(()=>observations.some(x=>!x.fromCache));
    await blockAndClose(alice,'flow-alice','flow-bob',conversation);
    await waitUntil(()=>observations.some(x=>!x.fromCache && !x.pendingWrites && x.ids.includes('flow-bob')));
    assert.equal((await getDoc(doc(alice,'conversations','block-ui'))).data().status,'closed');
    await assertFails(getDoc(doc(bob,'userBlocks','flow-alice','blocked','flow-bob')));
    assert.equal((await getDoc(doc(bob,'conversations','block-ui','messages','old'))).data().body,'history');
    await assertFails(setDoc(doc(bob,'conversations','block-ui','messages','blocked'),{senderId:'flow-bob',body:'blocked',sentAt:serverTimestamp()}));
    await unblock(alice,'flow-alice','flow-bob');
    await waitUntil(()=>!observations.at(-1).fromCache && !observations.at(-1).ids.includes('flow-bob'));
    assert.equal((await getDoc(doc(alice,'conversations','block-ui'))).data().status,'closed');
    await assertFails(updateDoc(doc(bob,'conversations','block-ui'),{status:'active',updatedAt:serverTimestamp()}));
  } finally {stop();}
});

test('a stale selected conversation causes the entire block-and-close batch to fail', async () => {
  await env.withSecurityRulesDisabled(async ctx => setDoc(doc(ctx.firestore(),'conversations','already-closed'),{participants:['flow-alice','flow-bob'],createdBy:'flow-alice',status:'closed',createdAt:new Date(),updatedAt:new Date()}));
  const alice=env.authenticatedContext('flow-alice').firestore();
  await assertFails(blockAndClose(alice,'flow-alice','flow-bob',{id:'already-closed',participants:['flow-alice','flow-bob'],status:'active'}));
  assert.equal((await getDoc(doc(alice,'userBlocks','flow-alice','blocked','flow-bob'))).exists(),false);
});

test('block controller waits for confirmed service state and closes its subscription', async () => {
  await env.withSecurityRulesDisabled(async ctx => setDoc(doc(ctx.firestore(),'conversations','controller-block'),{participants:['flow-alice','flow-bob'],createdBy:'flow-alice',status:'active',createdAt:new Date(),updatedAt:new Date()}));
  const db=env.authenticatedContext('flow-alice').firestore();
  let state, notify=()=>{};
  const controller=createBlockController({
    subscribe:(uid,next,fail)=>watchOwnBlocks(db,uid,next,fail),
    block:(uid,peer,chat)=>blockAndClose(db,uid,peer,chat),
    unblock:(uid,peer)=>unblock(db,uid,peer),
    onChange:value=>{state=value;notify();},
  });
  const waitUntil=predicate=>new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{notify=()=>{};reject(new Error('Block controller timed out.'));},5000);
    notify=()=>{if(predicate()){clearTimeout(timer);notify=()=>{};resolve();}};notify();
  });
  try {
    controller.setUser('flow-alice');
    await waitUntil(()=>state.status==='ready');
    const operation=controller.block('flow-bob',{id:'controller-block',participants:['flow-alice','flow-bob'],status:'active'});
    assert.equal(state.status,'saving');
    await operation;
    await waitUntil(()=>state.status==='ready' && state.ids.includes('flow-bob'));
    assert.equal((await getDoc(doc(db,'conversations','controller-block'))).data().status,'closed');
    await controller.unblock('flow-bob');
    await waitUntil(()=>state.status==='ready' && !state.ids.includes('flow-bob'));
  } finally {controller.dispose();}
});
