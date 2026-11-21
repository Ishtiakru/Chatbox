// Experimental rules only. Never point this test at a live database.
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, deleteDoc, serverTimestamp, Timestamp } from 'firebase/firestore';
let env;
before(async () => {
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Use an emulator.');
  env = await initializeTestEnvironment({projectId:'demo-chatbox',firestore:{rules:readFileSync(process.env.CHATBOX_TEST_RULES_FILE || 'firestore.calls.rules','utf8')}});
});
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx => {
    await setDoc(doc(ctx.firestore(),'conversations','chat'),{participants:['alice','bob'],createdBy:'alice',status:'active',createdAt:new Date(),updatedAt:new Date()});
  });
});
after(async () => { if (env) await env.cleanup(); });
const db = uid => uid ? env.authenticatedContext(uid).firestore() : env.unauthenticatedContext().firestore();
const call = uid => doc(db(uid),'conversations','chat','calls','call1');
const candidate = (uid,side,id) => doc(db(uid),'conversations','chat','calls','call1',side,id);
const offer = () => ({callerId:'alice',receiverId:'bob',media:'audio',offer:{type:'offer',sdp:'offer'},status:'ringing',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
const ice = () => ({candidate:'candidate:abc',sdpMid:'0',sdpMLineIndex:0,createdAt:serverTimestamp()});
const answer = () => ({status:'accepted',answer:{type:'answer',sdp:'answer'},updatedAt:serverTimestamp()});
test('only a participant may call the other participant and outsiders cannot read', async () => {
  await assertFails(setDoc(call('eve'), {...offer(),callerId:'eve'}));
  await assertFails(setDoc(call('alice'), {...offer(),receiverId:'eve'}));
  await assertFails(setDoc(call('alice'), {...offer(),callerId:'bob'}));
  await assertFails(setDoc(call('alice'), {...offer(),offer:{type:'answer',sdp:'x'}}));
  await assertSucceeds(setDoc(call('alice'),offer()));
  await assertSucceeds(getDoc(call('bob')));
  await assertFails(getDoc(call('eve')));
  await assertFails(getDoc(call(null)));
  await assertFails(deleteDoc(call('alice')));
});
test('only receiver accepts, each side owns its ICE and ended calls cannot be resumed', async () => {
  await assertSucceeds(setDoc(call('alice'),offer()));
  await assertSucceeds(setDoc(candidate('alice','callerCandidates','a'),ice()));
  await assertFails(setDoc(candidate('bob','callerCandidates','spoof'),ice()));
  await assertFails(setDoc(candidate('bob','receiverCandidates','early'),ice()));
  await assertFails(updateDoc(call('alice'),answer()));
  await assertFails(updateDoc(call('eve'),answer()));
  await assertSucceeds(updateDoc(call('bob'),answer()));
  await assertSucceeds(setDoc(candidate('bob','receiverCandidates','b'),ice()));
  await assertFails(setDoc(candidate('eve','receiverCandidates','bad'),ice()));
  await assertFails(updateDoc(candidate('alice','callerCandidates','a'),{candidate:'changed'}));
  await assertFails(updateDoc(call('bob'),{callerId:'eve',status:'ended',updatedAt:serverTimestamp()}));
  await assertSucceeds(updateDoc(call('alice'),{status:'ended',updatedAt:serverTimestamp()}));
  await assertFails(setDoc(candidate('alice','callerCandidates','late'),ice()));
  await assertFails(updateDoc(call('bob'),answer()));
});
test('expired sessions and malformed ICE cannot be used for signaling', async () => {
  await assertSucceeds(setDoc(call('alice'),offer()));
  await assertFails(setDoc(candidate('alice','callerCandidates','bad'),{...ice(),candidate:'x'.repeat(4097)}));
  await assertFails(setDoc(candidate('alice','callerCandidates','bad2'),{...ice(),sdpMLineIndex:-1}));
  await env.withSecurityRulesDisabled(async ctx => updateDoc(doc(ctx.firestore(),'conversations','chat','calls','call1'),{createdAt:Timestamp.fromMillis(Date.now()-360000)}));
  await assertFails(updateDoc(call('bob'),answer()));
  await assertFails(setDoc(candidate('alice','callerCandidates','expired'),ice()));
  // Expiry must not trap a ringing call: only a participant may end it,
  // and this does not permit changing any other field or resuming it.
  await assertFails(updateDoc(call('eve'),{status:'ended',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(call('alice'),{status:'ended',media:'video',updatedAt:serverTimestamp()}));
  await assertSucceeds(updateDoc(call('bob'),{status:'ended',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(call('bob'),answer()));
});
test('either party can end an expired accepted call, even after conversation closure', async () => {
  await assertSucceeds(setDoc(call('alice'),offer()));
  await assertSucceeds(updateDoc(call('bob'),answer()));
  await env.withSecurityRulesDisabled(async ctx => {
    await updateDoc(doc(ctx.firestore(),'conversations','chat','calls','call1'),{createdAt:Timestamp.fromMillis(Date.now()-360000)});
  });
  await assertFails(setDoc(candidate('bob','receiverCandidates','expired'),ice()));
  await assertFails(updateDoc(call('eve'),{status:'ended',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(call('bob'),{status:'ended',answer:{type:'answer',sdp:'tampered'},updatedAt:serverTimestamp()}));
  await assertSucceeds(updateDoc(doc(db('bob'),'conversations','chat'),{status:'closed',updatedAt:serverTimestamp()}));
  await assertSucceeds(updateDoc(call('alice'),{status:'ended',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(call('bob'),answer()));
});
test('calls and ICE require an accepted conversation; closure prevents new signaling', async () => {
  const ref = doc(db('bob'),'conversations','chat');
  await env.withSecurityRulesDisabled(ctx => updateDoc(doc(ctx.firestore(),'conversations','chat'),{status:'pending'}));
  await assertFails(setDoc(call('alice'),offer()));
  await assertSucceeds(updateDoc(ref,{status:'active',updatedAt:serverTimestamp()}));
  await assertSucceeds(setDoc(call('alice'),offer()));
  await assertSucceeds(updateDoc(ref,{status:'closed',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(call('bob'),answer()));
  await assertFails(setDoc(candidate('alice','callerCandidates','after-close'),ice()));
  await assertSucceeds(updateDoc(call('alice'),{status:'ended',updatedAt:serverTimestamp()}));
  await assertFails(setDoc(doc(db('alice'),'conversations','chat','calls','second'),offer()));
});

test('blocking prevents call setup and ICE, but participants can still end the call', async () => {
  await assertSucceeds(setDoc(call('alice'),offer()));
  await assertSucceeds(setDoc(doc(db('bob'),'userBlocks','bob','blocked','alice'),{createdAt:serverTimestamp()}));
  await assertFails(updateDoc(call('bob'),answer()));
  await assertFails(setDoc(candidate('alice','callerCandidates','blocked'),ice()));
  await assertFails(setDoc(doc(db('alice'),'conversations','chat','calls','new'),offer()));
  await assertSucceeds(updateDoc(call('alice'),{status:'ended',updatedAt:serverTimestamp()}));
  await assertSucceeds(deleteDoc(doc(db('bob'),'userBlocks','bob','blocked','alice')));
  await assertSucceeds(setDoc(doc(db('alice'),'conversations','chat','calls','after'),offer()));
});
