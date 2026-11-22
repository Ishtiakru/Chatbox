import test from 'node:test';
import assert from 'node:assert/strict';
import { getNativeGoogleCredential, signInNative } from '../src/native-sign-in.js';
import { authErrorMessage } from '../src/auth-errors.js';
const deps = {ready:async()=>{},getToken:async()=>({credential:{idToken:'secret-token',accessToken:'secret-access'}}),makeCredential:(id,access)=>({id,access})};
test('normal and explicit legacy flows request tokens without changing native auth', async () => {
  const options=[];
  const configured={...deps,getToken:async args=>{options.push(args);return {credential:{idToken:'token'}};}};
  assert.deepEqual(await getNativeGoogleCredential(configured),{id:'token',access:undefined});
  await getNativeGoogleCredential({...configured,legacy:true});
  assert.deepEqual(options,[{skipNativeAuth:true},{skipNativeAuth:true,useCredentialManager:false}]);
});
test('native failures are stage-tagged without leaking provider details', async () => {
  for (const [changes,code] of [[{ready:async()=>{throw new Error('secret@example.com')}},'native/identity-stage'],[{getToken:async()=>{throw new Error('secret-token')}},'native/google-stage'],[{getToken:async()=>({credential:{}})},'native/token-stage']]) {
    await assert.rejects(getNativeGoogleCredential({...deps,...changes}),err=>err.code===code && !err.message.includes('secret'));
    assert.doesNotMatch(authErrorMessage(code),/secret/);
  }
});
test('Firebase failures expose only selected codes or a fixed stage', async () => {
  for (const [code,expected] of [['auth/invalid-credential','auth/invalid-credential'],['private-token','native/firebase-stage']]) {
    await assert.rejects(signInNative({getCredential:async()=>({}),signIn:async()=>{throw Object.assign(new Error('secret-token'),{code});}}),err=>err.code===expected && !err.message.includes('secret'));
  }
});
