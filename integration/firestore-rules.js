// Run with `npm run test:rules` against the Firestore emulator, never production.
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, collection, query, where, setDoc, updateDoc, deleteDoc, serverTimestamp } from 'firebase/firestore';

let env;
before(async () => {
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Use npm run test:rules to start the Firestore emulator.');
  env = await initializeTestEnvironment({ projectId: 'demo-chatbox', firestore: { rules: readFileSync(process.env.CHATBOX_TEST_RULES_FILE || 'firestore.rules', 'utf8') } });
});
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'profiles', 'alice'), { uid: 'alice', displayName: 'Alice' });
    await setDoc(doc(db, 'profiles', 'bob'), { uid: 'bob', displayName: 'Bob' });
    await setDoc(doc(db, 'profiles', 'eve'), { uid: 'eve', displayName: 'Eve' });
    await setDoc(doc(db, 'conversations', 'chat'), { participants: ['alice', 'bob'], createdBy: 'alice', status:'active', createdAt: new Date(), updatedAt:new Date() });
    await setDoc(doc(db, 'conversations', 'chat', 'messages', 'm1'), { senderId: 'alice', body: 'hello', sentAt: new Date() });
  });
});
after(async () => { if (env) await env.cleanup(); });
const db = name => name ? env.authenticatedContext(name).firestore() : env.unauthenticatedContext().firestore();

test('profile lookup is authenticated; listing and impersonation are blocked', async () => {
  await assertFails(getDoc(doc(db(null), 'profiles', 'alice')));
  await assertSucceeds(getDoc(doc(db('alice'), 'profiles', 'bob')));
  await assertFails(getDocs(collection(db('alice'), 'profiles')));
  await assertFails(setDoc(doc(db('alice'), 'profiles', 'bob'), { uid: 'bob', displayName: 'Forged' }));
  await assertFails(setDoc(doc(db('alice'), 'profiles', 'alice'), { uid: 'alice', displayName: 'Alice', email: 'private@example.com' }));
});
test('only participants may read conversations and messages', async () => {
  await assertSucceeds(getDocs(query(collection(db('alice'), 'conversations'), where('participants', 'array-contains', 'alice'))));
  await assertSucceeds(getDoc(doc(db('bob'), 'conversations', 'chat')));
  await assertFails(getDoc(doc(db('eve'), 'conversations', 'chat')));
  await assertFails(getDoc(doc(db('eve'), 'conversations', 'chat', 'messages', 'm1')));
  await assertFails(getDoc(doc(db(null), 'conversations', 'chat', 'messages', 'm1')));
});
test('conversation creation requires distinct existing participants and immutable membership', async () => {
  const good = { participants: ['alice', 'bob'], createdBy: 'alice', status:'pending', createdAt: serverTimestamp(), updatedAt:serverTimestamp() };
  await assertSucceeds(setDoc(doc(db('alice'), 'conversations', 'new'), good));
  await assertFails(setDoc(doc(db('eve'), 'conversations', 'forged'), good));
  await assertFails(setDoc(doc(db('alice'), 'conversations', 'self'), { participants: ['alice', 'alice'], createdBy: 'alice', status:'pending', createdAt: serverTimestamp(), updatedAt:serverTimestamp() }));
  await assertFails(setDoc(doc(db('alice'), 'conversations', 'missing'), { participants: ['alice', 'unknown'], createdBy: 'alice', status:'pending', createdAt: serverTimestamp(), updatedAt:serverTimestamp() }));
  await assertFails(updateDoc(doc(db('alice'), 'conversations', 'chat'), { participants: ['alice', 'eve'] }));
  await assertFails(deleteDoc(doc(db('alice'), 'conversations', 'chat')));
});
test('message creation validates sender, membership, length and timestamps; messages are immutable', async () => {
  const ref = (user, id) => doc(db(user), 'conversations', 'chat', 'messages', id);
  await assertSucceeds(setDoc(ref('bob','good'), { senderId: 'bob', body: 'Hello', sentAt: serverTimestamp() }));
  await assertFails(setDoc(ref('eve','bad'), { senderId: 'eve', body: 'Hello', sentAt: serverTimestamp() }));
  await assertFails(setDoc(ref('bob','spoof'), { senderId: 'alice', body: 'Hello', sentAt: serverTimestamp() }));
  await assertFails(setDoc(ref('bob','empty'), { senderId: 'bob', body: '', sentAt: serverTimestamp() }));
  await assertFails(setDoc(ref('bob','long'), { senderId: 'bob', body: 'x'.repeat(2001), sentAt: serverTimestamp() }));
  await assertFails(setDoc(ref('bob','past'), { senderId: 'bob', body: 'Hello', sentAt: new Date(0) }));
  await assertFails(updateDoc(ref('alice','m1'), { body: 'Edited' }));
  await assertFails(deleteDoc(ref('alice','m1')));
});

test('administrator records and settings cannot be mutated by clients', async () => {
  await assertFails(setDoc(doc(db('alice'), 'system', 'admin'), { uid: 'alice' }));
  await assertFails(getDoc(doc(db('alice'), 'system', 'admin')));
  await assertFails(setDoc(doc(db('alice'), 'settings', 'public'), { welcome: 'Injected' }));
  await assertSucceeds(getDoc(doc(db(null), 'settings', 'public')));
});
test('recipient consent is required; closing prevents further messages but preserves history', async () => {
  const alice=db('alice'), bob=db('bob'), eve=db('eve');
  const ref = user => doc(user,'conversations','request');
  const msg = (user,id) => doc(user,'conversations','request','messages',id);
  const request = {participants:['alice','bob'],createdBy:'alice',status:'pending',createdAt:serverTimestamp(),updatedAt:serverTimestamp()};
  await assertFails(setDoc(ref(alice),{...request,status:'active'}));
  await assertSucceeds(setDoc(ref(alice),request));
  await assertFails(setDoc(msg(alice,'early'),{senderId:'alice',body:'early',sentAt:serverTimestamp()}));
  await assertFails(updateDoc(ref(alice),{status:'active',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(ref(eve),{status:'active',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(ref(bob),{status:'active',createdBy:'bob',updatedAt:serverTimestamp()}));
  await assertSucceeds(updateDoc(ref(bob),{status:'active',updatedAt:serverTimestamp()}));
  await assertSucceeds(setDoc(msg(alice,'hello'),{senderId:'alice',body:'Hello',sentAt:serverTimestamp()}));
  await assertSucceeds(updateDoc(ref(bob),{status:'closed',updatedAt:serverTimestamp()}));
  await assertSucceeds(getDoc(msg(alice,'hello')));
  await assertFails(setDoc(msg(alice,'late'),{senderId:'alice',body:'late',sentAt:serverTimestamp()}));
  await assertFails(updateDoc(ref(bob),{status:'active',updatedAt:serverTimestamp()}));
});
test('either party can decline or cancel a pending request; closure is terminal', async () => {
  const alice=db('alice'),bob=db('bob');
  const create = id => setDoc(doc(alice,'conversations',id),{participants:['alice','bob'],createdBy:'alice',status:'pending',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
  await create('declined'); await assertSucceeds(updateDoc(doc(bob,'conversations','declined'),{status:'closed',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(doc(bob,'conversations','declined'),{status:'active',updatedAt:serverTimestamp()}));
  await create('cancelled'); await assertSucceeds(updateDoc(doc(alice,'conversations','cancelled'),{status:'closed',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(doc(bob,'conversations','cancelled'),{status:'active',updatedAt:serverTimestamp()}));
});

test('block list is owner-only and entries cannot be forged or edited', async () => {
  const block = (user, owner, other) => doc(db(user),'userBlocks',owner,'blocked',other);
  const valid = () => ({createdAt:serverTimestamp()});
  await assertFails(setDoc(block('eve','alice','bob'),valid()));
  await assertFails(setDoc(block(null,'alice','bob'),valid()));
  await assertFails(setDoc(block('alice','alice','alice'),valid()));
  await assertFails(setDoc(block('alice','alice','bob'),{createdAt:serverTimestamp(),reason:'extra'}));
  await assertFails(setDoc(block('alice','alice','bob'),{createdAt:new Date(0)}));
  await assertSucceeds(setDoc(block('alice','alice','bob'),valid()));
  await assertSucceeds(getDoc(block('alice','alice','bob')));
  await assertSucceeds(getDocs(collection(db('alice'),'userBlocks','alice','blocked')));
  await assertFails(getDoc(block('bob','alice','bob')));
  await assertFails(getDoc(block('eve','alice','bob')));
  await assertFails(getDocs(collection(db('bob'),'userBlocks','alice','blocked')));
  await assertFails(updateDoc(block('alice','alice','bob'),{createdAt:serverTimestamp()}));
  await assertFails(deleteDoc(block('bob','alice','bob')));
  await assertSucceeds(deleteDoc(block('alice','alice','bob')));
});

test('blocking in either direction denies new requests and messages while preserving history', async () => {
  const alice=db('alice'),bob=db('bob');
  const block=(db,owner,other)=>doc(db,'userBlocks',owner,'blocked',other);
  const request=(db,id,author,participants)=>setDoc(doc(db,'conversations',id),{participants,createdBy:author,status:'pending',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
  const msg=(db,id,sender)=>setDoc(doc(db,'conversations','chat','messages',id),{senderId:sender,body:'hello',sentAt:serverTimestamp()});
  await assertSucceeds(request(alice,'before','alice',['alice','bob']));
  await assertSucceeds(setDoc(block(alice,'alice','bob'),{createdAt:serverTimestamp()}));
  await assertFails(request(bob,'blocked-bob','bob',['bob','alice']));
  await assertFails(request(alice,'blocked-alice','alice',['alice','bob']));
  await assertFails(updateDoc(doc(bob,'conversations','before'),{status:'active',updatedAt:serverTimestamp()}));
  await assertFails(msg(alice,'blocked-message-a','alice'));
  await assertFails(msg(bob,'blocked-message-b','bob'));
  await assertSucceeds(getDoc(doc(bob,'conversations','chat','messages','m1')));
  // Either participant may still close existing/pending conversations.
  await assertSucceeds(updateDoc(doc(bob,'conversations','before'),{status:'closed',updatedAt:serverTimestamp()}));
  await assertSucceeds(setDoc(block(bob,'bob','alice'),{createdAt:serverTimestamp()}));
  await assertSucceeds(deleteDoc(block(alice,'alice','bob')));
  await assertFails(request(alice,'still-blocked','alice',['alice','bob']));
  await assertFails(msg(bob,'still-blocked','bob'));
  await assertSucceeds(deleteDoc(block(bob,'bob','alice')));
  await assertSucceeds(request(alice,'after-unblock','alice',['alice','bob']));
  await assertSucceeds(msg(alice,'after-unblock','alice'));
});
