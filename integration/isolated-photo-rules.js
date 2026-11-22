// Synthetic, local-emulator-only rules checks. No photos or production data.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {before, after, test} from 'node:test';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {Bytes,doc,collection,getDoc,getDocs,setDoc,deleteDoc,updateDoc,serverTimestamp} from 'firebase/firestore';
import {isolatedPhotoRules} from '../scripts/generate-isolated-photo-rules.mjs';
let env;
const as=uid=>env.authenticatedContext(uid).firestore();
const photo=(db, conversation, uid)=>doc(db,'conversations',conversation,'sharedPhotos',uid);
const bytes=n=>Bytes.fromUint8Array(new Uint8Array(n));
const payload=(uid,n=4)=>({ownerId:uid,jpeg:bytes(n),updatedAt:serverTimestamp()});
before(async()=>{
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Local emulator required.');
  env=await initializeTestEnvironment({projectId:'demo-chatbox',firestore:{rules:isolatedPhotoRules(readFileSync('firestore.combined-experimental.rules','utf8'),'alice','bob')}});
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context=>{
    const db=context.firestore();
    await setDoc(doc(db,'conversations','active'),{participants:['alice','bob'],status:'active'});
    await setDoc(doc(db,'conversations','pending'),{participants:['alice','bob'],status:'pending'});
    await setDoc(doc(db,'conversations','closed'),{participants:['alice','bob'],status:'closed'});
    await setDoc(doc(db,'conversations','different'),{participants:['bob','charlie'],status:'active'});
  });
});
after(async()=>{if(env)await env.cleanup();});
test('per-conversation photo copies require active unblocked members and owner-only writes', async()=>{
  const alice=as('alice'),bob=as('bob'),eve=as('eve'),anon=env.unauthenticatedContext().firestore();
  await assertSucceeds(setDoc(photo(alice,'active','alice'),payload('alice')));
  await assertSucceeds(getDoc(photo(bob,'active','alice')));
  await assertFails(getDocs(collection(bob,'conversations','active','sharedPhotos')));
  await assertFails(getDoc(photo(eve,'active','alice')));
  await assertFails(getDoc(photo(anon,'active','alice')));
  await assertFails(setDoc(photo(bob,'active','alice'),payload('alice')));
  await assertFails(setDoc(photo(eve,'active','eve'),payload('eve')));
  await assertFails(setDoc(photo(alice,'different','alice'),payload('alice')));
  await assertFails(setDoc(photo(alice,'pending','alice'),payload('alice')));
  await assertFails(setDoc(photo(alice,'closed','alice'),payload('alice')));
  await assertFails(setDoc(photo(alice,'active','alice'),{...payload('alice'),extra:'unsafe'}));
  await assertFails(setDoc(photo(alice,'active','alice'),payload('alice',0)));
  await assertFails(setDoc(photo(alice,'active','alice'),payload('alice',32769)));
  await assertFails(setDoc(photo(alice,'active','alice'),{ownerId:'alice',jpeg:'not bytes',updatedAt:serverTimestamp()}));
  await assertFails(setDoc(photo(alice,'active','alice'),{ownerId:'bob',jpeg:bytes(4),updatedAt:serverTimestamp()}));
  await assertFails(setDoc(photo(alice,'active','alice'),{ownerId:'alice',jpeg:bytes(4),updatedAt:new Date(0)}));
  await assertSucceeds(setDoc(photo(alice,'active','alice'),payload('alice',32768)));
  assert.equal((await getDoc(photo(bob,'active','alice'))).data().jpeg.toUint8Array().length,32768);
  await assertSucceeds(setDoc(doc(bob,'userBlocks','bob','blocked','alice'),{createdAt:serverTimestamp()}));
  await assertFails(getDoc(photo(bob,'active','alice')));
  await assertFails(getDoc(photo(alice,'active','alice')));
  await assertFails(setDoc(photo(alice,'active','alice'),payload('alice')));
  // Removal does not require the conversation to be unblocked or active.
  await assertFails(deleteDoc(photo(bob,'active','alice')));
  await assertSucceeds(deleteDoc(photo(alice,'active','alice')));
  await assertSucceeds(deleteDoc(doc(bob,'userBlocks','bob','blocked','alice')));
  await assertSucceeds(setDoc(photo(alice,'active','alice'),payload('alice')));
  await env.withSecurityRulesDisabled(async context=>updateDoc(doc(context.firestore(),'conversations','active'),{status:'closed'}));
  await assertFails(getDoc(photo(bob,'active','alice')));
  await assertSucceeds(deleteDoc(photo(alice,'active','alice')));
});

test('photo service interoperates with the rules using synthetic byte samples',async()=>{
  const alice=as('alice'),bob=as('bob'),guard={run:fn=>fn()};
  await env.withSecurityRulesDisabled(async context=>setDoc(doc(context.firestore(),'conversations','service'),{participants:['alice','bob'],status:'active'}));
  const {createConversationPhotoService}=await import('../src/conversation-photo.js');
  const a=createConversationPhotoService({db:alice,guard}),b=createConversationPhotoService({db:bob,guard});
  // Synthetic bytes exercise the signature check; they are not a real image.
  const sample=new Uint8Array([255,216,255,42,255,217]);
  await assertSucceeds(a.put('service','alice',sample));
  assert.deepEqual(await assertSucceeds(b.get('service','alice')),sample);
  await assertFails(b.put('service','alice',sample));
  await assertFails(b.remove('service','alice'));
  await assertSucceeds(a.remove('service','alice'));
  assert.equal(await assertSucceeds(b.get('service','alice')),null);
});
