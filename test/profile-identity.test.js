import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { validContactCode, contactQrPayload, parseContactQr, randomContactCode, claimContactCode } from '../src/profile-identity.js';
import { profileIdentityRules } from '../scripts/generate-profile-identity-rules.mjs';

test('contact QR accepts only exact six-digit versioned codes', () => {
  assert.equal(validContactCode('000042'),true);
  assert.equal(contactQrPayload('000042'),'chatbox:contact:v1:000042');
  assert.equal(parseContactQr('chatbox:contact:v1:000042'),'000042');
  for (const bad of ['12345','1234567',' 123456','１２３４５６','123456\n','123456\r',123456,'<x>']) { assert.equal(validContactCode(bad),false); assert.throws(()=>contactQrPayload(bad)); }
  for (const bad of ['chatbox:contact:v1:000042?x=1','https://example.com','chatbox:contact:v2:123456','chatbox:contact:v1:000042\n',null]) assert.equal(parseContactQr(bad),null);
});
test('secure random code avoids modulo bias and preserves leading zeroes', () => {
  let i=0; const values=[4294967295,42];
  assert.equal(randomContactCode({getRandomValues:a=>{a[0]=values[i++];}}),'000042'); assert.equal(i,2);
  assert.throws(()=>randomContactCode({}));
});
test('reservation retries collisions, returns existing code and never writes after session guard fails', async () => {
  const docs=new Map([['code/123456',{uid:'other',code:'123456'}]]), guard={run:fn=>fn()};
  const transact=async fn=>fn({get:async ref=>({exists:()=>docs.has(ref),data:()=>docs.get(ref)}),set:(ref,value)=>docs.set(ref,value)});
  const seq=['123456','000042'];
  const options={uid:'alice',verified:true,guard,transact,profileRef:uid=>'profile/'+uid,codeRef:code=>'code/'+code,newCode:()=>seq.shift()};
  assert.equal(await claimContactCode(options),'000042');
  assert.deepEqual(docs.get('profile/alice'),{uid:'alice',code:'000042'});
  assert.deepEqual(docs.get('code/000042'),{uid:'alice',code:'000042'});
  assert.equal(await claimContactCode({...options,newCode:()=> '999999'}),'000042');
  await assert.rejects(claimContactCode({...options,verified:false}),/Invalid/);
  let called=0; await assert.rejects(claimContactCode({...options,newCode:()=> '999999',guard:{run:()=>{called++;throw Error('session changed');}}}),/session changed/); assert.equal(called,1);
});
test('candidate generation is pinned and never modifies the base rules', () => {
  const base=readFileSync('firestore.combined-experimental.rules','utf8');
  const candidate=profileIdentityRules(base);
  assert.match(candidate,/match \/identityCodes\/\{code\}/);
  assert.doesNotMatch(base,/match \/identityCodes/);
  assert.throws(()=>profileIdentityRules(base+'\n'));
});
