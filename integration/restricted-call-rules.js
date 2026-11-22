// Synthetic identities against an emulator only. Does not deploy or validate
// the real project, actual devices, STUN, TURN, or complete call security.
import {readFileSync} from 'node:fs';
import {after,before,test} from 'node:test';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {doc,getDoc,setDoc,updateDoc,serverTimestamp} from 'firebase/firestore';
import {restrictedCallTestRules} from '../scripts/generate-isolated-call-rules.mjs';

let env;
before(async()=>{
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Local emulator required.');
  const rules=restrictedCallTestRules(readFileSync('firestore.calls.rules','utf8'),'alice','bob');
  env=await initializeTestEnvironment({projectId:'demo-chatbox',firestore:{rules}});
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx=>{
    const db=ctx.firestore(), now=new Date();
    await setDoc(doc(db,'settings','public'),{example:'public'});
    await setDoc(doc(db,'conversations','allowed'),{participants:['alice','bob'],createdBy:'alice',status:'active',createdAt:now,updatedAt:now});
    // An outsider who is present in an existing conversation must still be
    // denied; this checks UID gating separately from membership checks.
    await setDoc(doc(db,'conversations','outside'),{participants:['alice','eve'],createdBy:'alice',status:'active',createdAt:now,updatedAt:now});
    await setDoc(doc(db,'conversations','outside','calls','existing'),{callerId:'alice',receiverId:'eve',media:'audio',offer:{type:'offer',sdp:'offer'},status:'ringing',createdAt:now,updatedAt:now});
  });
});
after(async()=>{if(env) await env.cleanup();});
test('allowed participants can signal while an outsider and anonymous user are denied',async()=>{
  const alice=env.authenticatedContext('alice').firestore(), bob=env.authenticatedContext('bob').firestore();
  const eve=env.authenticatedContext('eve').firestore(), anon=env.unauthenticatedContext().firestore();
  const ref=db=>doc(db,'conversations','allowed','calls','call');
  const offer={callerId:'alice',receiverId:'bob',media:'audio',offer:{type:'offer',sdp:'offer'},status:'ringing',createdAt:serverTimestamp(),updatedAt:serverTimestamp()};
  // Public settings are an intentional exception to the UID restriction.
  await assertSucceeds(getDoc(doc(anon,'settings','public')));
  await assertFails(setDoc(doc(anon,'settings','public'),{example:'tampered'}));
  await assertFails(setDoc(ref(anon),offer));
  await assertSucceeds(setDoc(ref(alice),offer));
  await assertSucceeds(getDoc(ref(bob)));
  await assertFails(getDoc(ref(eve))); await assertFails(getDoc(ref(anon)));
  await assertFails(getDoc(doc(eve,'conversations','outside','calls','existing')));
  await assertFails(updateDoc(doc(eve,'conversations','outside','calls','existing'),{status:'accepted',answer:{type:'answer',sdp:'answer'},updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(ref(alice),{status:'accepted',answer:{type:'answer',sdp:'answer'},updatedAt:serverTimestamp()}));
  await assertSucceeds(updateDoc(ref(bob),{status:'accepted',answer:{type:'answer',sdp:'answer'},updatedAt:serverTimestamp()}));
  const ice={candidate:'candidate:synthetic',sdpMid:'0',sdpMLineIndex:0,createdAt:serverTimestamp()};
  await assertSucceeds(setDoc(doc(alice,'conversations','allowed','calls','call','callerCandidates','one'),ice));
  await assertFails(setDoc(doc(eve,'conversations','allowed','calls','call','callerCandidates','forged'),ice));
  await assertSucceeds(updateDoc(ref(bob),{status:'ended',updatedAt:serverTimestamp()}));
  await assertFails(setDoc(doc(alice,'conversations','allowed','calls','call','callerCandidates','late'),ice));
});
