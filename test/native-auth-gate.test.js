import test from 'node:test';
import assert from 'node:assert/strict';
import {createNativeAuthGate, watchAfterNativeGate} from '../src/native-auth-gate.js';
const web = {projectId:'chatbox-test-123',apiKey:'public-key',authDomain:'chatbox-test-123.firebaseapp.com',messagingSenderId:'123456',appId:'1:123456:web:abc123'};
const native = {projectId:'chatbox-test-123',senderId:'123456',androidAppId:'1:123456:android:abc123',packageName:'com.chatbox.test',debuggable:true};
const prod = {projectId:'chatbox-afb8c',messagingSenderId:'1030482946047'};
const prodNative = {projectId:'chatbox-afb8c',senderId:'1030482946047',androidAppId:'1:1030482946047:android:abc123',packageName:'com.chatbox.apk',debuggable:false};
const make = (options = {}) => createNativeAuthGate({isNative:true,isTestProject:true,nativeTestOptIn:true,webConfig:web,getIdentity:async () => native,...options});
const tick = () => new Promise(resolve => setImmediate(resolve));

test('native gate checks both test and default package identities and caches the verification', async () => {
  let count = 0;
  const ready = make({getIdentity:async () => { count++; return native; }});
  await Promise.all([ready(), ready()]); assert.equal(count, 1);
  await make({isTestProject:false,nativeTestOptIn:false,webConfig:prod,getIdentity:async () => prodNative})();
  for (const options of [
    {nativeTestOptIn:false},
    {getIdentity:async () => ({...native,projectId:'chatbox-afb8c'})},
    {getIdentity:async () => ({...native,debuggable:false})},
    {getIdentity:async () => {throw new Error('no bridge');}},
    {isTestProject:false,nativeTestOptIn:true,webConfig:prod,getIdentity:async () => prodNative},
    {isTestProject:false,nativeTestOptIn:false,webConfig:prod,getIdentity:async () => ({...prodNative,packageName:'com.chatbox.test'})},
  ]) await assert.rejects(make(options)());
});

test('auth listener waits for native identity and handles cancellation and failure', async () => {
  let resolveGate, calls = 0, stopped = 0;
  const ready = () => new Promise(resolve => {resolveGate = resolve;});
  const listen = () => {calls++; return () => {stopped++;};};
  const stop = watchAfterNativeGate(ready, listen, () => {}, () => {});
  await tick(); assert.equal(calls, 0);
  resolveGate(); await tick(); assert.equal(calls, 1);
  stop(); assert.equal(stopped, 1);
  let resolveCancelled;
  const cancelled = watchAfterNativeGate(() => new Promise(resolve => {resolveCancelled = resolve;}), listen, () => {}, () => {});
  await tick(); cancelled(); resolveCancelled(); await tick(); assert.equal(calls, 1);
  let failure;
  watchAfterNativeGate(() => Promise.reject(new Error('native mismatch')), listen, () => {}, error => {failure = error;});
  await tick(); assert.match(failure.message, /native mismatch/); assert.equal(calls, 1);
});

test('browser gate never accesses the native bridge', async () => {
  await make({isNative:false,getIdentity:() => {throw new Error('must not be called');}})();
});
