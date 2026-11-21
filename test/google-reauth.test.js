import test from 'node:test';
import assert from 'node:assert/strict';
import {createGoogleReauth} from '../src/google-reauth.js';
function wait(){let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b;});return {promise,resolve,reject};}
function fixture(native=false) {
  let user={uid:'alice'}, events=[];
  const deps={
    getUser:()=>user,isNative:()=>native,
    reauthPopup:async u=>events.push(['popup',u.uid]),
    getNativeCredential:async()=>{events.push(['native']);return 'credential';},
    reauthCredential:async(u,c)=>events.push(['credential',u.uid,c]),
    refreshToken:async u=>events.push(['refresh',u.uid]),
  };
  const run=()=>createGoogleReauth(deps)();
  return {deps,run,events,setUser:value=>{user=value;}};
}
test('web reauthentication uses popup then refreshes the same user token',async()=>{
  const f=fixture();await f.run();
  assert.deepEqual(f.events,[['popup','alice'],['refresh','alice']]);
});
test('native reauthentication passes the provider credential to Firebase for the same user',async()=>{
  const f=fixture(true);await f.run();
  assert.deepEqual(f.events,[['native'],['credential','alice','credential'],['refresh','alice']]);
});
test('no prompt or token refresh without a signed-in account',async()=>{
  const f=fixture();f.setUser(null);await assert.rejects(f.run(),/Sign in first/);assert.deepEqual(f.events,[]);
});
test('an account switch while the native picker is open stops reauthentication',async()=>{
  const f=fixture(true),pending=wait(); f.deps.getNativeCredential=()=>pending.promise;
  const result=f.run();f.setUser({uid:'other'});pending.resolve('credential');
  await assert.rejects(result,/account changed/);assert.deepEqual(f.events,[]);
});
test('an account switch during popup or token refresh cannot report success',async()=>{
  const f=fixture(),popup=wait();f.deps.reauthPopup=()=>popup.promise;
  const result=f.run();f.setUser({uid:'alice'});popup.resolve();
  await assert.rejects(result,/account changed/);assert.deepEqual(f.events,[]);
  const g=fixture(),refresh=wait();g.deps.refreshToken=()=>refresh.promise;
  const result2=g.run();await Promise.resolve();g.setUser(null);refresh.resolve();
  await assert.rejects(result2,/account changed/);
});
test('Firebase rejection (including a mismatched account) never refreshes the token',async()=>{
  const f=fixture(true);f.deps.reauthCredential=async()=>{throw new Error('credential rejected');};
  await assert.rejects(f.run(),/credential rejected/);assert.deepEqual(f.events,[['native']]);
});
